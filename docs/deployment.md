# Deploy — sentinel-auth-api

Runbook do primeiro deploy do `v1.0.0` (issue #22), conforme decidido em [ADR-0011](adr/0011-aws-ec2-manual-deploy.md): AWS EC2, `t3.micro`, primeiro deploy **manual**, reaproveitando o `docker-compose.yml` do repositório sem nenhuma mudança de arquitetura. Deploys seguintes já são automatizados via GitHub Actions (issue #23 — ver seção 8).

Este documento é o passo a passo para **quem for executar o deploy manualmente** — não é uma automação, é literalmente a sequência de comandos/cliques a seguir.

## 1. Provisionar a instância EC2

No Console AWS (região **us-east-2**, Ohio — corrigido em ADR-0011, o valor anterior `us-east-1` estava errado):

1. **EC2 → Launch Instance**.
2. **AMI**: Amazon Linux 2023 (free tier eligible).
3. **Instance type**: `t3.micro`.
4. **Key pair**: crie um par novo (ex.: `sentinel-auth-api-key`) e baixe o `.pem` — é a única forma de acessar a instância via SSH depois (sem senha).
5. **Network settings / Security group**: crie um novo grupo com estas regras de entrada:
   - `SSH` (porta 22) — origem `0.0.0.0/0` (ADR-0011: aceitável para este perfil de projeto, autenticação é só por chave).
   - `HTTP` (porta 80) — origem `0.0.0.0/0`.
   - `HTTPS` (porta 443) — origem `0.0.0.0/0` (ADR-0013 — necessária para o Caddy/Let's Encrypt).
6. **Storage**: 20 GB `gp3` (default do free tier costuma ser suficiente; ajuste se precisar).
7. **Launch instance**. Anote o **IP público** exibido depois que a instância entrar em estado `running`.
8. **Elastic IP** (ADR-0013): aloque um Elastic IP (`EC2 → Network & Security → Elastic IPs → Allocate`) e associe-o a esta instância. Sem isso, o IP público muda a cada `stop`/`start` (passo 7 da seção "Depois de validado" abaixo) e quebraria o hostname `sslip.io` calculado a partir dele. Um Elastic IP é gratuito **enquanto associado a uma instância em execução** — nunca deixe um alocado e sem associar, custa por hora.

## 2. Conectar via SSH e instalar Docker

```bash
chmod 400 sentinel-auth-api-key.pem
ssh -i sentinel-auth-api-key.pem ec2-user@<IP-PÚBLICO>
```

Na instância (Amazon Linux 2023 já vem com `dnf`):

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# Disconecte e reconecte via SSH para o grupo `docker` ter efeito na sessão.
```

Instale os plugins `docker compose` e `docker buildx` (Amazon Linux 2023 não traz nenhum dos dois por padrão via `dnf`; o pacote `docker` do repositório só traz o Engine). `compose build`/`up --build` **exige buildx ≥ 0.17.0** — instalar só o compose e ficar com um buildx antigo (ou nenhum) falha com `compose build requires buildx 0.17.0 or later` (achado ao validar a automação da issue #23):

```bash
DOCKER_CONFIG=${DOCKER_CONFIG:-$HOME/.docker}
mkdir -p $DOCKER_CONFIG/cli-plugins

curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o $DOCKER_CONFIG/cli-plugins/docker-compose
chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose
docker compose version

curl -SL https://github.com/docker/buildx/releases/latest/download/buildx-v0.37.0.linux-amd64 \
  -o $DOCKER_CONFIG/cli-plugins/docker-buildx
chmod +x $DOCKER_CONFIG/cli-plugins/docker-buildx
docker buildx version
```

> A versão do `buildx` no comando acima (`v0.37.0`) é a mais recente confirmada em `github.com/docker/buildx/releases/latest` nesta edição do runbook — confirme se ainda é a atual antes de rodar, mesmo cuidado de não confiar cegamente numa versão fixada em documentação (mesmo racional já aplicado a outras dependências do projeto, ex. `docs/technologies/jacoco.md`/`gatling.md`).

## 3. Clonar o repositório e configurar o `.env`

```bash
git clone https://github.com/LuisCarlos01/sentinel-auth-api.git
cd sentinel-auth-api
cp .env.example .env
```

Edite o `.env` na instância (`nano .env` ou similar):

- `JWT_SIGNING_KEY`: gere um valor real, **diferente** do valor de exemplo do repositório (esse é só para desenvolvimento local). Ex.: `openssl rand -base64 48`.
- `POSTGRES_PASSWORD`: troque pelo valor real que você quer usar em produção (não precisa ser o mesmo do dev local).

Esse `.env` fica **só na instância** — nunca é commitado (mesma regra do `.gitignore` já existente no repo).

## 4. Configurar HTTPS (Caddy + sslip.io) — ADR-0013

Calcule o hostname a partir do Elastic IP alocado no passo 8 acima: troque os pontos por hifens e acrescente `.sslip.io` (ex.: `18.117.253.110` → `18-117-253-110.sslip.io`). `sslip.io` não exige cadastro nem configuração própria — o nome já resolve para o IP embutido nele.

Na instância, copie o template committed e preencha o hostname real:

```bash
cp Caddyfile.example Caddyfile
# edite Caddyfile e troque {PLACEHOLDER} pelo hostname calculado acima
```

`Caddyfile` **não é commitado** (está no `.gitignore` do repositório, mesmo tratamento do `.env`) — é específico desta instância. Se esta instância já tiver um `docker-compose.override.yml` de uma configuração anterior (mapeamento manual `80:8080`, do runbook antigo), **apague-o**: o Caddy agora é quem ocupa a porta 80 do host, e um `docker-compose.override.yml` remanescente seria mesclado automaticamente em qualquer `docker compose up` futuro sem `-f` explícito, conflitando com a porta do Caddy.

## 5. Subir a aplicação

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

O overlay `docker-compose.prod.yml` (committed) adiciona o serviço `caddy`, que expõe `80`/`443`, faz proxy para `app:8080` e obtém/renova automaticamente um certificado Let's Encrypt (desafio HTTP-01) para o hostname configurado no `Caddyfile`.

Acompanhe os logs até confirmar que a aplicação subiu e as migrations Flyway rodaram:

```bash
docker compose logs -f app
```

Acompanhe também o Caddy até ver a confirmação de emissão do certificado (algo como "certificate obtained successfully"):

```bash
docker compose logs -f caddy
```

## 6. Validar o deploy (critérios de aceite da issue #22 + HTTPS da ADR-0013)

Do seu próprio computador (substitua `<HOSTNAME>` pelo hostname `sslip.io` calculado no passo 4):

```bash
curl -I http://<HOSTNAME> # espera redirect (30x) para https
curl -v https://<HOSTNAME>/actuator/health # espera cadeia de certificado válida, sem -k
curl https://<HOSTNAME>/swagger-ui.html -I
curl -X POST https://<HOSTNAME>/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke-test@example.com","password":"Str0ngP@ssw0rd!"}'
curl -X POST https://<HOSTNAME>/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke-test@example.com","password":"Str0ngP@ssw0rd!"}'
```

Um `login` bem-sucedido retorna `accessToken`/`refreshToken` — use o `refreshToken` para testar `refresh` e `logout` da mesma forma. Para o critério de RBAC (`GET /api/v1/users` → `403` sem papel `ADMIN`), qualquer usuário recém-registrado já serve (papel padrão é `USER`). Valide também, com um cliente que preserve cookies (`curl -c/-b` ou o navegador), que o cookie do refresh token agora é retido entre chamadas — é o comportamento que a issue #24 reportava como quebrado sob HTTP puro.

## 7. Depois de validado

- Atualize a seção "Deployment" do `README.md` com o IP público real.
- Pare a instância (`EC2 → Stop instance`, não `Terminate`) quando não estiver testando ativamente — reduz o custo a quase zero fora dos períodos de uso (ADR-0011).

## 8. Deploy automatizado (issue #23)

Depois do primeiro deploy manual (passos 1–7 acima), deploys seguintes podem ser automatizados via GitHub Actions (`.github/workflows/deploy.yml`): a cada push em `main`, depois que o workflow `CI` passar, o `Deploy` conecta via SSH na instância e roda o mesmo `git pull && docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d` do passo 5 — sem registry de imagem (Docker Hub/ECR), a EC2 continua buildando a própria imagem (ADR-0011). Depois de subir, o workflow espera até 60s pelo `app` responder em `http://localhost:8080/actuator/health` (direto no container, sem passar pelo Caddy — ADR-0013) — se não responder, o job falha e mostra os últimos logs do container, em vez de reportar sucesso com a aplicação travada.

**Secrets necessários** (`Settings → Secrets and variables → Actions` do repositório no GitHub):

| Secret | Valor |
|---|---|
| `DEPLOY_HOST` | IP público da instância (ex.: `18.117.253.110`) |
| `DEPLOY_SSH_USER` | `ec2-user` |
| `DEPLOY_SSH_KEY` | Conteúdo do `.pem` gerado no passo 1 (chave privada completa) |
| `PROD_JWT_SIGNING_KEY` | O mesmo valor real de produção usado no `.env` da instância |
| `PROD_POSTGRES_PASSWORD` | O mesmo valor real de produção usado no `.env` da instância |

O workflow **reescreve o `.env` da instância a cada deploy** a partir desses secrets — não depende do `.env` manual criado no passo 3 continuar existindo por conta própria.

O caminho manual (passos 1–7) continua funcionando normalmente como fallback — a automação só substitui a repetição dos passos 3 (parte do `.env`) e 5 a cada atualização, não o processo inteiro.

## Limitações conhecidas desta primeira entrega (ADR-0011)

- **Sem HTTPS/domínio** — tráfego em texto plano, aceitável dado que o projeto não tem usuários reais.
- **Sem alta disponibilidade** — instância única; reiniciar o host derruba a aplicação até subir de novo manualmente.
- **Custo não é zero para sempre** — ver ADR-0011 para o modelo de crédito da AWS e a estimativa de custo pós-crédito.
