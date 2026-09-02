# 0001 — Modelagem enxuta de RBAC

## Status

Accepted

## Contexto

O `sentinel-auth-api` precisa de um mecanismo de autorização baseado em papéis (RBAC). Existem duas abordagens comuns nesse espaço:

1. Um modelo estático, com um conjunto fixo de papéis (`Role`) associados aos usuários.
2. Um modelo dinâmico, com uma entidade `Permission` própria, permitindo compor papéis a partir de permissões configuráveis em runtime (um "motor de permissões").

O projeto é um portfólio técnico para demonstrar domínio de Spring Security, com escala esperada de dezenas de usuários e nenhum requisito atual de permissões granulares configuráveis por um administrador em runtime.

## Decisão

Adotar um modelo enxuto de RBAC:

- Entidade `Role` como tabela própria, em relação N:N com `User`.
- **Sem** entidade ou sistema de `Permission` dinâmica — nenhum motor de permissões configurável.
- Checagens de autorização feitas diretamente no código (ex.: `hasRole()` ou checagem direta de enum/nome de role), sem camada de abstração adicional sobre essas checagens.

## Consequências

**Positivas**

- Modelo simples de entender, implementar e testar — alinhado a KISS/YAGNI.
- Menos superfície de código e de schema para manter, sem sacrificar o valor de demonstrar RBAC funcional no portfólio.
- Menor tempo de implementação, permitindo focar esforço nas partes centrais do fluxo de autenticação.

**Trade-offs aceitos**

- Se no futuro surgir a necessidade de permissões granulares e configuráveis em runtime (ex.: um admin criando papéis customizados via UI), será necessário migrar o modelo de dados e o código de autorização — esse custo é aceito conscientemente agora (YAGNI), não sendo uma necessidade do escopo atual.
- Adicionar ou alterar papéis hoje exige alteração de código/enum e deploy, não uma operação administrativa em runtime.
