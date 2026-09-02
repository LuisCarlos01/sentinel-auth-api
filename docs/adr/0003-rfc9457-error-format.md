# 0003 — Formato de erro padronizado via RFC 9457

## Status

Accepted

## Contexto

A API precisa de um formato consistente para respostas de erro, consumível de forma previsível tanto pelo frontend web quanto pelo app mobile. Historicamente, a RFC 7807 ("Problem Details for HTTP APIs") definia esse formato, mas ela foi **obsoletada pela RFC 9457**, que consolida e substitui a especificação anterior. O Spring Framework possui suporte nativo a esse formato através do tipo `ProblemDetail`.

## Decisão

Todas as respostas de erro da API seguem a **RFC 9457** ("Problem Details for HTTP APIs"), usando o suporte nativo do Spring (`ProblemDetail`), com os campos padrão:

- `type`
- `title`
- `status`
- `detail`
- `instance`

## Consequências

**Positivas**

- Padrão HTTP formal e amplamente adotado, o que facilita o consumo por qualquer cliente HTTP genérico.
- Suporte nativo no Spring (`ProblemDetail`) reduz o custo de implementação a praticamente zero código de infraestrutura próprio.
- Formato de erro consistente e autoexplicativo, o que é valioso tanto para os clientes reais (web/mobile) quanto para quem avalia o portfólio.

**Trade-offs aceitos**

- É necessário atenção ao citar/documentar a especificação corretamente como RFC 9457 (e não RFC 7807, que ela obsoleta), para evitar referência desatualizada em código, comentários e documentação de API (OpenAPI/Swagger).
- Nenhum trade-off funcional relevante identificado — o custo de adoção é baixo frente ao valor.
