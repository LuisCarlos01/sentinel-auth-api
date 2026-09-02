# 0004 — Validação de entrada via Bean Validation

## Status

Accepted

## Contexto

A API expõe endpoints de autenticação (`register`, `login`, `refresh`, `logout`) que recebem dados de entrada de clientes externos (web e mobile) e precisam de validação declarativa e consistente antes de chegar à camada de serviço. O ecossistema Spring possui integração nativa com Jakarta Bean Validation, com custo de implementação baixo.

## Decisão

Usar Jakarta Bean Validation (`jakarta.validation`) em DTOs de entrada, desde a v0.1.0, para validação declarativa dos dados recebidos pela API.

## Consequências

**Positivas**

- Validação declarativa (anotações em DTOs) em vez de código imperativo espalhado pelos services.
- Integração nativa com Spring MVC/WebFlux e com as respostas de erro em RFC 9457 (ver ADR-0003), incluindo mensagens de violação estruturadas.
- Custo de implementação baixo frente ao valor de robustez e clareza que agrega desde o início do projeto.

**Trade-offs aceitos**

- Nenhum trade-off relevante identificado para o escopo atual — validações mais complexas ou cross-field, se necessárias no futuro, podem exigir validadores customizados além das anotações padrão.
