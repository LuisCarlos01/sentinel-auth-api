# 0013 — HTTPS via Elastic IP + sslip.io + reverse proxy (Caddy) com Let's Encrypt

## Status

Accepted

## Contexto

ADR-0011 registrou, como trade-off consciente do deploy do `v1.0.0`, a ausência de domínio e HTTPS: "aceitável aqui porque o projeto não tem usuários reais [...] e o próprio ADR-0009 (cookie `Secure`) só se torna um requisito rígido quando um frontend real depender de cookie em HTTPS, o que ainda não é o caso."

A issue [#24](https://github.com/LuisCarlos01/sentinel-auth-api/issues/24), aberta por um usuário terceiro, reporta exatamente essa consequência: o cookie do refresh token tem `Secure=true` (ADR-0009), a instância roda em HTTP puro (porta 80), e o navegador descarta o `Set-Cookie` silenciosamente — `refresh` passa a depender só do corpo JSON. O comentário na issue confirmou que é o trade-off já documentado, mantendo-a aberta para rastrear até HTTPS entrar em escopo. Este ADR fecha esse caminho.

Restrição adicional desta decisão: o dono do projeto optou por **não comprar um domínio** (ex.: via Hostinger) para resolver isso — o objetivo é HTTPS funcional sem custo monetário recorrente novo, mantendo a mesma postura de custo-consciência já registrada em ADR-0011 (crédito AWS limitado, sem serviços gerenciados novos).

Ponto técnico relevante: o IP público padrão de uma instância EC2 **não é estático** — muda a cada `stop`/`start`. ADR-0011 já cita parar a instância quando ociosa como mitigação de custo; sem um IP fixo, qualquer solução de HTTPS baseada em nome de domínio apontando para esse IP quebraria a cada parada/reinício.

## Decisão

1. **Elastic IP** alocado e associado à instância EC2 existente — gratuito enquanto permanecer associado a uma instância em execução (cobrança só incide sobre IP elástico alocado e **não** associado a nenhuma instância rodando). Resolve a instabilidade do IP público sem custo adicional, mantendo a mitigação de custo do ADR-0011 (parar a instância quando ociosa) sem quebrar o mapeamento de rede.
2. **`sslip.io`** como hostname — nome como `18-117-253-110.sslip.io` (ajustado ao IP elástico final), que resolve via DNS público diretamente para o IP embutido no próprio nome. Sem cadastro, sem custo, aceito pelo Let's Encrypt como um domínio válido para o desafio HTTP-01.
3. **Caddy** como reverse proxy na própria instância, na frente da aplicação (que continua ouvindo HTTP puro internamente, porta 8080, sem nenhuma mudança de código/imagem). Caddy expõe `443`, obtém e renova automaticamente o certificado Let's Encrypt para o hostname `sslip.io`, e redireciona `80 → 443`. Substitui o mapeamento `80:8080` direto do `docker-compose.yml` da instância (ADR-0011) por `Caddy:443/80 → app:8080`.

Com isso, ADR-0009 (cookie `Secure`) passa a funcionar de fato — o cookie do refresh token deixa de ser descartado pelo navegador.

### Alternativas consideradas

- **Comprar um domínio (Hostinger ou outro registrador)**: descartado por decisão explícita do dono do projeto de não introduzir custo monetário novo para resolver isso.
- **Cloudflare (tier grátis) na frente da instância**: também exige um domínio próprio cadastrado no Cloudflare — não elimina a necessidade de comprar/possuir um domínio, só move onde o TLS é terminado.
- **DuckDNS (ou similar) em vez de `sslip.io`**: equivalente em custo (grátis) e mecanismo (Let's Encrypt aceita ambos), mas exige cadastro numa conta externa e um cliente de atualização dinâmica de IP. `sslip.io` não exige nada disso porque o IP já está codificado no próprio nome — mais simples para este caso, que já vai ter IP fixo via Elastic IP. DuckDNS fica como alternativa se `sslip.io` alguma vez sair do ar.
- **AWS ALB + ACM (certificado gerenciado)**: descartado — ALB tem custo próprio (não é gratuito), contradizendo o orçamento de custo mínimo já fixado em ADR-0011; também é a categoria de serviço gerenciado que ADR-0011 já evitou deliberadamente (objetivo de aprendizado de operar a infraestrutura diretamente).
- **Manter HTTP indefinidamente**: descartado — mantém o bug relatado em #24 sem prazo de resolução, e o próprio ADR-0011 já registrava isso como pendência a ser revisitada.

## Consequências

**Positivas**

- Resolve a issue #24 sem custo monetário recorrente novo (Elastic IP associado é gratuito; `sslip.io` e Let's Encrypt são gratuitos).
- Mantém a arquitetura de instância única, auto-operada, consistente com o objetivo de aprendizado do ADR-0011 — nenhum serviço gerenciado novo entra no desenho.
- Certificado renovado automaticamente pelo Caddy, sem intervenção manual recorrente.
- Torna o requisito `Secure` do cookie de refresh token (ADR-0009) efetivo pela primeira vez em produção.

**Trade-offs aceitos**

- **Hostname não é uma marca própria**: `18-117-253-110.sslip.io` (ou equivalente) é funcional, mas não é um domínio memorável/profissional — aceitável para o perfil de portfólio/estudo do projeto; se o projeto evoluir para precisar de identidade de marca, comprar um domínio real continua sendo o caminho natural, sem precisar desfazer esta decisão (só troca o hostname configurado no Caddy).
- **Dependência do Elastic IP**: se a instância for terminada (não apenas parada) e substituída por uma nova, o Elastic IP precisa ser reassociado à nova instância, e o hostname `sslip.io` muda (já que o IP mudou) — exige reconfigurar o Caddy e qualquer cliente que dependa da URL antiga. Restaurar a partir de `stop`/`start` da mesma instância não tem esse problema.
- **Sem serviço gerenciado de certificado**: a renovação depende do Caddy rodando saudável na própria instância — sem alerta externo, uma falha silenciosa do container Caddy expiraria o certificado sem aviso. Mitigação futura possível (fora do escopo deste ADR): monitoramento básico do Actuator já existente estendido para checar a validade do certificado, se isso vier a importar.
- **Abertura de porta 443** no Security Group da instância, além da já existente `80`/`22`.

## Referências

- [ADR-0011](0011-aws-ec2-manual-deploy.md) — decisão original de deploy sem HTTPS/domínio, cujo trade-off este ADR fecha.
- [ADR-0009](0009-dual-channel-refresh-token-delivery.md) — cookie `Secure` do refresh token, que passa a funcionar de fato com esta decisão.
- [Issue #24](https://github.com/LuisCarlos01/sentinel-auth-api/issues/24) — relato do bug (cookie `Secure` descartado em HTTP) que motiva este ADR.
- `docs/deployment.md` — documentação operacional do deploy atual, a ser atualizada quando esta decisão for implementada.
