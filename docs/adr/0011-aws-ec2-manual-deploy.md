# 0011 — Deploy do v1.0.0 em EC2 (AWS), manual, sem RDS/HTTPS/domínio

## Status

Accepted

> **Correção factual (2026-09-10):** este ADR registrava a região como `us-east-1`. A instância provisionada de fato está em `us-east-2` (Ohio) — confirmado no console AWS ao trabalhar na ADR-0013. Preço on-demand do `t3.micro` verificado como idêntico entre as duas regiões (US$ 0,0104/hora), então as estimativas de custo abaixo continuam válidas; só o nome da região estava errado. Não é uma revisão de decisão (não muda `t3.micro`/deploy manual/sem RDS-HTTPS-domínio), por isso corrigido no lugar em vez de um novo ADR.

## Contexto

O PRD (`docs/prd.md`, seção 3 — "Objetivos e critério de sucesso") registra como requisito forte, não opcional, que "o projeto está rodando em algum lugar (deploy funcional), não apenas em ambiente local do autor". Os 9 critérios funcionais do `v1.0.0` (seção 4 do PRD) já estavam implementados ao final da v0.5.0 — faltava só esse requisito não-funcional de implantação pública.

A escolha de provedor e topologia de deploy não tinha ADR nem PRD prévio — foi decidida em sessão de grilling com o dono do projeto, motivada por um objetivo de aprendizado explícito (primeira experiência real de "subir Docker" em um provedor de nuvem) e pelo plano de, em breve, hospedar também um frontend simples (login/cadastro/recuperação de senha) no mesmo provedor para validar UI/UX contra os endpoints reais.

Durante o grilling, verificamos que o antigo modelo "EC2 grátis por 12 meses (750h/mês)" **não existe mais** — a AWS substituiu isso por um crédito de até US$ 200, válido pelo menor entre 6 meses ou o esgotamento do crédito. Depois disso, uma `t3.micro` rodando 24/7 custa cerca de **US$ 7,59/mês** de computação (US$ 0,0104/hora × 730h, região us-east-2 (Ohio)) mais ~US$ 1,60/mês de armazenamento EBS (`gp3`, ~20GB) — total estimado **US$ 9–10/mês**, ou próximo de zero se a instância for parada quando ociosa.

## Decisão

Deploy do `sentinel-auth-api` em uma instância **EC2 `t3.micro`** (AWS, região `us-east-2` (Ohio)), rodando o `docker-compose.yml` já existente no repositório **sem modificação de arquitetura** — API e Postgres no mesmo host, mesmo container/composição usada em desenvolvimento local. Nenhum serviço gerenciado novo (RDS, ALB, Route53, ACM) entra nesta decisão.

Escopo explicitamente **dividido em dois entregáveis independentes**:

1. **Primeiro deploy: manual.** SSH na instância, `git pull`, `docker compose up --build -d`. Decisão deliberada: o objetivo declarado do dono do projeto é ganhar essa experiência prática de primeira mão — automatizar de saída pularia o aprendizado que motivou a escolha da AWS/EC2 em vez de um PaaS. Fecha sozinho o critério de sucesso do PRD.
2. **Automação via GitHub Actions: ticket separado, não-bloqueante.** Deploys seguintes passam a ser disparados por push em `main`, via SSH direto na instância (build acontece na própria EC2 via `git pull`, sem registry de imagem — Docker Hub/ECR fica fora de escopo). Segredos (chave SSH, `JWT_SIGNING_KEY`, senha do Postgres) ficam como **GitHub Actions Secrets**, não em arquivo `.env` manual — decisão já pensando nesse ticket futuro, mesmo que o primeiro deploy não dependa disso.

Outras decisões operacionais fechadas junto:

- **Sem domínio próprio, sem HTTPS** nesta entrega — acesso via IP público da instância, HTTP puro. Mapeamento `80:8080` feito no `docker-compose.yml` **da instância** (não no repositório — é configuração de ambiente, não de aplicação), só para uma URL sem porta explícita.
- **Porta 22 (SSH) aberta a `0.0.0.0/0`**, autenticação exclusivamente por chave (sem senha) — padrão de mercado para este perfil de projeto (hobby/portfólio, instância única, sem dado sensível de terceiros).

## Alternativas consideradas

- **ECS Fargate / AWS App Runner**: descartadas — ambas abstraem o host por trás de um serviço gerenciado, o que atende bem a um cenário produtivo real, mas conflita diretamente com o objetivo declarado de aprendizado (a motivação era justamente operar Docker "de verdade", não delegar isso a um PaaS).
- **RDS gerenciado para o Postgres**: descartado por ora — não é gratuito de forma indefinida (ao contrário do que se buscava), e o `docker-compose.yml` já resolve isso localmente sem custo adicional. Fica como possível evolução futura se a instância única virar um gargalo real.
- **Oracle Cloud "Always Free" (VM.Standard.E2.1.Micro / Ampere A1)**: verificado como alternativa com tier permanente (sem expiração por crédito, ao contrário da AWS atual) durante o grilling, mas descartado — o dono do projeto priorizou a experiência específica com AWS (relevante para o plano de também hospedar o frontend lá) sobre o custo zero indefinido.
- **Render / Google Cloud "Always Free"**: também levantadas como comparação; descartadas pelo mesmo motivo (preferência explícita por AWS), notando que o Postgres gratuito do Render expira em 30 dias — não serviria para este caso de uso de qualquer forma.
- **Automatizar o deploy desde a primeira entrega**: descartado — ver justificativa na seção Decisão (objetivo de aprendizado do primeiro deploy manual).
- **Segredos via arquivo `.env` manual na instância**: considerado por ser o caminho mais simples, mas descartado em favor de GitHub Actions Secrets, já que a automação (ticket separado) é um passo já decidido e próximo — evita retrabalho de migrar o mecanismo de segredos depois.

## Consequências

**Positivas**

- Zero mudança de arquitetura da aplicação para rodar em produção — o mesmo `Dockerfile`/`docker-compose.yml` usado em desenvolvimento local roda tal qual na EC2.
- Fecha o único critério de sucesso do PRD (`docs/prd.md`, seção 3) ainda não satisfeito, sem introduzir complexidade desproporcional ao estágio do projeto.
- Caminho de evolução claro e já mapeado (automação CI/CD, HTTPS/domínio, possivelmente RDS) sem comprometer a decisão atual — nenhuma dessas evoluções exige desfazer o que foi decidido aqui.

**Trade-offs aceitos**

- **Custo não é zero para sempre**: após ~6 meses (ou antes, se o crédito acabar por outro uso da conta AWS), a instância passa a custar ~US$ 9–10/mês enquanto ligada 24/7. Mitigação: parar a instância quando não estiver em uso ativo (custo cai para centavos de armazenamento).
- **Sem HTTPS**: tráfego em texto plano, inadequado para dado sensível de produção real — aceitável aqui porque o projeto não tem usuários reais (`docs/architecture.md` — "Problema e escopo") e o próprio ADR-0009 (cookie `Secure`) só se torna um requisito rígido quando um frontend real depender de cookie em HTTPS, o que ainda não é o caso.
- **Sem alta disponibilidade**: instância única, sem réplica, sem RDS multi-AZ — reinício do host derruba a aplicação até subir de novo. Aceitável para o perfil de portfólio/estudo do projeto.
- **Deploy manual não escala**: cada atualização exige intervenção humana via SSH até o ticket de automação ser implementado — período de transição conhecido e temporário, não um estado final.
- **Porta 22 aberta a `0.0.0.0/0`**: superfície de ataque de força bruta contra SSH existe (mitigada por autenticação exclusiva por chave, sem senha).

## Validação

- Deploy considerado bem-sucedido quando os 4 endpoints de autenticação (`register`/`login`/`refresh`/`logout`), o endpoint RBAC (`GET /api/v1/users`), o Actuator health e o Swagger UI respondem corretamente contra o IP público da instância — mesmos critérios de aceite do ticket de deploy do `v1.0.0`.
- `docker compose up` continua funcionando localmente sem qualquer alteração, confirmando que nenhuma mudança de arquitetura foi introduzida só para viabilizar o deploy.

## Referências

- [`docs/prd.md`](../prd.md) — seção 3 (critério de sucesso) e seção 4 (escopo funcional do `v1.0.0`).
- [ADR-0009](0009-dual-channel-refresh-token-delivery.md) — cookie `Secure` do refresh token, requisito que só se torna rígido quando HTTPS entrar em escopo (fora desta decisão).
- Grilling desta decisão (sessão registrada na conversa, sem documento próprio além deste ADR) — inclui a verificação factual do modelo de crédito atual da AWS (substituindo o antigo "12 meses grátis") e do preço on-demand de `t3.micro` (AWS EC2 On-Demand Pricing, região us-east-2 (Ohio)).
