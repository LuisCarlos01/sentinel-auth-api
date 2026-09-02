# PRD — sentinel-auth-api

Este documento é o **Product Requirements Document** do `sentinel-auth-api`: define por que o projeto existe, para quem, o que conta como sucesso e qual é o escopo funcional do primeiro release estável (`v1.0.0`). Ele faz a ponte entre o raciocínio de produto e o raciocínio técnico — para as decisões de arquitetura, stack e modelo de domínio, ver [`docs/architecture.md`](architecture.md) e os [ADRs](adr/README.md); este documento não duplica esse conteúdo.

## 1. Visão geral / problema

O `sentinel-auth-api` é um projeto de portfólio: seu objetivo é demonstrar domínio técnico de Spring Security e das práticas de engenharia associadas a uma API de autenticação de padrão **"production-inspired"**, não resolver um problema de negócio real. Não há usuários reais além do autor, em contexto de estudo (ver detalhamento completo em [`docs/architecture.md` — "Problema e escopo"](architecture.md#problema-e-escopo)).

Isso molda como este PRD deve ser lido: "requisitos de produto" aqui significam, na prática, o conjunto de decisões que tornam o projeto um artefato crível de competência técnica — escopo funcional coerente com uma API de autenticação real, qualidade de engenharia (testes, CI, documentação) e capacidade de qualquer pessoa validar isso na prática rodando o projeto localmente.

## 2. Público-alvo

O projeto tem dois públicos distintos, que não devem ser confundidos:

- **Usuários hipotéticos da API em si** — os consumidores técnicos dos endpoints de autenticação: um frontend web e um app mobile, conforme já definido em [`docs/architecture.md`](architecture.md#problema-e-escopo). Esse público é hipotético — não existe frontend/app real sendo construído neste momento — mas orienta decisões como formato de erro (RFC 9457) e estratégia de armazenamento de token por canal.
- **Leitores do projeto/repositório** — recrutadores e outros desenvolvedores avaliando o repositório público no GitHub como prova de competência técnica e de raciocínio de produto. Este é o público **real e imediato** deste PRD e do `README.md`: alguém que clona o repositório, lê a documentação e espera entender rapidamente o que o projeto faz, por que as decisões foram tomadas dessa forma, e — crucialmente — consegue rodar o projeto na própria máquina para verificar isso na prática.

## 3. Objetivos e critério de sucesso

O sucesso do projeto é **qualitativo**, não numérico — não há meta de cobertura de testes nem SLA de latência definidos. O projeto é considerado bem-sucedido quando, simultaneamente:

- O código está limpo e organizado, seguindo o estilo arquitetural definido em [`docs/architecture.md`](architecture.md#estilo-arquitetural).
- A suíte de testes automatizados (unitários e de integração) está verde.
- O pipeline de CI (GitHub Actions) passa a cada push/PR.
- A documentação está completa e consistente — README, arquitetura, ADRs e este PRD.
- O projeto está rodando em algum lugar (deploy funcional), não apenas em ambiente local do autor.
- **Qualquer outro desenvolvedor que acesse o repositório, clone na própria máquina e queira testar o projeto, consegue rodar sem fricção.**

Esse último ponto é um **requisito forte, não um nice-to-have**: dado que o público real deste projeto avalia competência através da leitura e da execução do repositório, um projeto que não roda de primeira falha no próprio objetivo de existir. A decisão de arquitetura que viabiliza isso é o empacotamento via Docker/`docker-compose` (ver [`docs/architecture.md` — Stack técnica](architecture.md#stack-técnica)): um único comando (`docker-compose up`) deve subir a aplicação e o PostgreSQL prontos para uso, sem etapas manuais adicionais de configuração de ambiente.

## 4. Escopo do v1.0.0 — "Stable Authentication API"

O `v1.0.0` é o primeiro release considerado estável, conforme o roadmap do [`README.md`](../README.md#roadmap). O escopo abaixo é fechado — nada além disso entra no v1.0. Cada item é um requisito funcional com um critério de aceite objetivo:

| # | Feature | Critério de aceite |
|---|---|---|
| 1 | **Register** (`POST /api/v1/auth/register`) | Dado um e-mail ainda não cadastrado e uma senha válida, a API cria o usuário (com senha em hash Argon2id) e retorna sucesso; dado um e-mail já cadastrado ou dados inválidos, retorna erro no formato RFC 9457 com o status apropriado (`409`/`400`). |
| 2 | **Login** (`POST /api/v1/auth/login`) | Dado um e-mail e senha válidos, a API retorna access token e refresh token; dado credencial inválida, retorna `401` no formato RFC 9457. |
| 3 | **Refresh** (`POST /api/v1/auth/refresh`) | Dado um refresh token válido e não expirado, a API retorna um novo par de tokens e invalida o refresh token anterior (rotação a cada uso); dado um refresh token inválido, expirado ou já usado, retorna `401` no formato RFC 9457. |
| 4 | **Logout** (`POST /api/v1/auth/logout`) | Dado um refresh token válido associado ao usuário autenticado, a API revoga esse token no banco, tornando-o inutilizável em chamadas futuras a `refresh`. |
| 5 | **RBAC (roles)** | Endpoints protegidos respeitam o papel (`Role`) do usuário autenticado: um usuário sem o papel exigido recebe `403` ao tentar acessar um recurso restrito; o modelo segue o desenho enxuto do [ADR-0001](adr/0001-lean-rbac-modeling.md). |
| 6 | **Actuator** | Endpoints de observabilidade do Spring Boot Actuator (ex.: health check) estão habilitados e acessíveis, permitindo verificar que a aplicação está no ar. |
| 7 | **Swagger / OpenAPI** | A documentação interativa da API está disponível em uma rota própria e reflete fielmente todos os endpoints de `/api/v1`, incluindo os formatos de erro RFC 9457. |
| 8 | **Testes automatizados** | Existe suíte de testes unitários (services, via Mockito) e de integração (repositories e fluxo completo de autenticação via controllers, com Testcontainers/Postgres real), e a suíte completa passa (`green`). |
| 9 | **CI (GitHub Actions)** | Todo push/PR dispara build e execução da suíte de testes automaticamente, e o pipeline está verde na branch principal. |

Todos os endpoints seguem o versionamento via URI (`/api/v1`, [ADR-0002](adr/0002-uri-based-api-versioning.md)), o formato de erro RFC 9457 ([ADR-0003](adr/0003-rfc9457-error-format.md)) e validação de entrada via Bean Validation ([ADR-0004](adr/0004-bean-validation-input.md)) — esses padrões cross-cutting não são repetidos por feature na tabela acima, mas se aplicam a todas elas.

## 5. Fora de escopo (v1.0.0)

Os itens abaixo são **não-escopo direto do produto** para o v1.0.0 — diferente do rate limiting (item à parte, adiado com justificativa própria em ADR), estes simplesmente não fazem parte da proposta de valor do release:

- **Reset de senha** (fluxo de recuperação de senha).
- **Verificação de e-mail** — o campo `emailVerified` já existe no modelo de dados desde a v0.1.0 (ver [`docs/architecture.md` — Modelo de domínio](architecture.md#modelo-de-domínio)), mas o fluxo/feature de verificação em si não é implementado até o v1.0.
- **Multi-tenancy.**
- **Painel administrativo** (admin UI).

Adicionalmente, **rate limiting** também está fora do v1.0.0, mas por um motivo diferente: é uma decisão deliberada de sequenciamento, já registrada e justificada em [ADR-0005](adr/0005-defer-rate-limiting.md), que adia a feature para a fase `v0.5.0 — Quality & Security` do roadmap.

## 6. Requisitos não-funcionais

Resumo dos requisitos não-funcionais já detalhados em [`docs/architecture.md`](architecture.md) — ver o documento original para a prosa completa:

- **Segurança**: senha sempre em hash Argon2id, nunca em texto plano ([`docs/architecture.md` — Modelo de domínio](architecture.md#modelo-de-domínio)); tokens JWT stateless com refresh token persistido e revogável ([`docs/architecture.md` — Fluxo de autenticação](architecture.md#fluxo-de-autenticação-e-ciclo-de-vida-de-tokens)); segredos nunca commitados, geridos via `.env` local e GitHub Secrets em CI ([`docs/architecture.md` — Segurança operacional](architecture.md#segurança-operacional-e-segredos)).
- **Observabilidade**: Spring Boot Actuator habilitado desde a v0.1.0; logs simples, mas claros e úteis (logs estruturados ficam para versão futura).
- **Testes**: cobertura via testes unitários (Mockito) e de integração com banco real (Testcontainers), sem meta numérica de cobertura definida para o v1.0.0.
- **Local runnability** *(requisito novo, não coberto explicitamente em `docs/architecture.md`)*: qualquer desenvolvedor deve conseguir clonar o repositório e subir a aplicação completa (API + PostgreSQL) localmente com um único comando (`docker-compose up`), sem etapas manuais de configuração além de variáveis de ambiente documentadas. Este é o requisito operacional que sustenta o critério de sucesso descrito na seção 3.

## 7. Roadmap / fases futuras (fora do v1.0.0)

Fases posteriores ao `v1.0.0`, listadas no [roadmap do `README.md`](../README.md#roadmap) mas ainda não detalhadas — não passaram por entrevista de arquitetura e não têm ADRs ou requisitos formais associados até o momento:

- **`v2.0.0` — OAuth2/OIDC.**
- **`v3.0.0` — Integração com AWS Cognito.**

Este PRD cobre exclusivamente o `v1.0.0`; as fases acima serão detalhadas em documentação própria quando entrarem em planejamento.

## 8. Referências

- [`README.md`](../README.md) — descrição do projeto e roadmap versionado.
- [`docs/architecture.md`](architecture.md) — decisões consolidadas de arquitetura, stack, modelo de domínio e fluxo de autenticação.
- [`docs/adr/README.md`](adr/README.md) — índice das Architecture Decision Records (ADR-0001 a ADR-0005).
