# 0002 — Versionamento de API via URI desde o início

## Status

Accepted

## Contexto

A API é consumida diretamente por um frontend web e por um app mobile. Ainda que não haja uma v2 planejada a curto prazo, a existência de clientes reais consumindo endpoints sem nenhum esquema de versionamento cria retrabalho e risco de breaking changes no futuro, caso o versionamento seja introduzido depois que já existirem clientes em produção.

Entre as estratégias comuns de versionamento de API (URI, header customizado, media type/`Accept` header, query parameter), o versionamento via URI é o padrão mais consolidado no mercado e o mais simples de documentar e explicar.

## Decisão

Todos os endpoints expostos pela API usam o prefixo `/api/v1/...` desde a v0.1.0, mesmo sem uma v2 planejada no curto prazo.

## Consequências

**Positivas**

- Padrão amplamente reconhecido, fácil de entender por qualquer consumidor da API sem documentação adicional.
- Evita a necessidade de introduzir versionamento retroativamente, migrando clientes já existentes.
- Simples de implementar e de visualizar em logs, métricas e documentação OpenAPI.

**Trade-offs aceitos**

- Nenhuma v2 está planejada hoje — o prefixo `v1` existe puramente como proteção estrutural, não como resposta a uma necessidade concreta já identificada.
- Versionamento via URI acopla a versão à rota (em vez de, por exemplo, um header), o que é uma escolha deliberada de simplicidade sobre "pureza" REST.
