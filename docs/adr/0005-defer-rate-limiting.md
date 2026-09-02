# 0005 — Rate limiting adiado deliberadamente para a fase de Quality & Security

## Status

Accepted

## Contexto

Rate limiting é uma prática de segurança relevante para APIs de autenticação (proteção contra brute force, credential stuffing, abuso de endpoints públicos). No entanto, implementá-lo prematuramente, antes de o fluxo principal de autenticação estar validado, adiciona complexidade que compete por atenção com a validação do core do projeto.

O roadmap do projeto já prevê uma fase dedicada a segurança e qualidade, `v0.5.0 — Quality & Security`, posterior ao bootstrap (`v0.1.0`) e ao core de autenticação (`v0.3.0`/`v0.4.0`).

## Decisão

Rate limiting **não** entra na v0.1.0. É uma escolha consciente de sequenciamento: primeiro validar o core do fluxo de autenticação (registro, login, refresh, logout), e só então endurecer a API com rate limiting na fase `v0.5.0 — Quality & Security` do roadmap.

## Consequências

**Positivas**

- Escopo da v0.1.0 permanece focado no fluxo essencial de autenticação, sem complexidade prematura (YAGNI aplicado a uma feature de segurança não crítica para o MVP de portfólio).
- Rate limiting é revisitado em uma fase dedicada a qualidade e segurança, com mais contexto sobre o comportamento real da API já implementada.

**Trade-offs aceitos**

- Entre a v0.1.0 e a v0.5.0, os endpoints de autenticação (em especial `login`) ficam sem proteção própria da API contra brute force ou abuso — risco aceito conscientemente, dado que o projeto não tem usuários reais além do autor/estudo nesse período.
- Esse trade-off é reavaliado e encerrado explicitamente na fase `v0.5.0 — Quality & Security` do roadmap.
