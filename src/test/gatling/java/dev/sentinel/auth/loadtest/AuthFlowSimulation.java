package dev.sentinel.auth.loadtest;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Teste de carga do fluxo completo de autenticação (ticket #21): {@code register} → {@code
 * login} → {@code refresh} → {@code logout}, contra a aplicação real subida via {@code docker
 * compose -f docker-compose.yml -f docker-compose.loadtest.yml up} (não Testcontainers).
 *
 * <p>O overlay {@code docker-compose.loadtest.yml} eleva o rate limit de login (ADR-0010, os dois
 * buckets do {@code LoginRateLimitFilter} — IP e e-mail — compartilham a mesma capacidade
 * configurável) bem acima do padrão de produção. E-mail varia por usuário virtual (feeder
 * abaixo), então o bucket de e-mail não seria o problema; o bucket por IP sim: todas as
 * requisições desta simulação saem do mesmo IP de origem (uma única máquina rodando o Gatling),
 * então sem elevar a capacidade ele derrubaria a simulação de carga bem antes de medir a
 * performance real do fluxo — o comportamento do rate limiter em si já é coberto pelos testes de
 * integração do ticket #01.
 */
public class AuthFlowSimulation extends Simulation {

    private static final Iterator<Map<String, Object>> USER_FEEDER =
            Stream.generate(AuthFlowSimulation::newVirtualUser).iterator();

    private static Map<String, Object> newVirtualUser() {
        Map<String, Object> user = new HashMap<>();
        user.put("email", "loadtest-" + UUID.randomUUID() + "@example.com");
        user.put("password", "LoadTest#1234");
        return user;
    }

    // Configurável via -Dsentinel.loadtest.baseUrl, default aponta para docker-compose local
    // (docs/technologies/gatling.md: nunca hardcode de URL/porta na simulação).
    private final HttpProtocolBuilder httpProtocol = http.baseUrl(
                    System.getProperty("sentinel.loadtest.baseUrl", "http://localhost:8080"))
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final ChainBuilder register = exec(http("register")
            .post("/api/v1/auth/register")
            .body(StringBody("{\"email\":\"#{email}\",\"password\":\"#{password}\"}"))
            .check(status().is(201)));

    private final ChainBuilder login = exec(http("login")
            .post("/api/v1/auth/login")
            .body(StringBody("{\"email\":\"#{email}\",\"password\":\"#{password}\"}"))
            .check(status().is(200))
            .check(jsonPath("$.accessToken").saveAs("accessToken"))
            .check(jsonPath("$.refreshToken").saveAs("refreshToken")));

    private final ChainBuilder refresh = exec(http("refresh")
            .post("/api/v1/auth/refresh")
            .body(StringBody("{\"refreshToken\":\"#{refreshToken}\"}"))
            .check(status().is(200))
            .check(jsonPath("$.accessToken").saveAs("accessToken"))
            .check(jsonPath("$.refreshToken").saveAs("refreshToken")));

    private final ChainBuilder logout = exec(http("logout")
            .post("/api/v1/auth/logout")
            .header("Authorization", "Bearer #{accessToken}")
            .body(StringBody("{\"refreshToken\":\"#{refreshToken}\"}"))
            .check(status().is(204)));

    // login/refresh também setam o refresh token via Set-Cookie (ADR-0009), e o Gatling
    // gerencia cookies automaticamente por usuário virtual, como um browser. flushCookieJar()
    // depois de cada passo garante que refresh/logout usem de fato o valor de #{refreshToken}
    // injetado explicitamente no corpo (a variável de sessão via saveAs, como o ticket pede) —
    // sem isso, o cookie mascararia silenciosamente uma simulação com o encadeamento quebrado.
    private final ScenarioBuilder authFlow = scenario("Auth flow")
            .feed(USER_FEEDER)
            .exec(register)
            .exec(login)
            .exec(flushCookieJar())
            .exec(refresh)
            .exec(flushCookieJar())
            .exec(logout);

    {
        setUp(authFlow.injectOpen(rampUsers(50).during(Duration.ofSeconds(30))))
                .protocols(httpProtocol);
    }
}
