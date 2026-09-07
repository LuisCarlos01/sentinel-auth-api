# Deploy — sentinel-auth-api

Runbook do primeiro deploy do `v1.0.0` (issue #22), conforme decidido em [ADR-0011](adr/0011-aws-ec2-manual-deploy.md): AWS EC2, `t3.micro`, deploy **manual** (não automatizado — ver #23 para a automação futura via GitHub Actions), reaproveitando o `docker-compose.yml` do repositório sem nenhuma mudança de arquitetura.

Este documento é o passo a passo para **quem for executar o deploy manualmente** — não é uma automação, é literalmente a sequência de comandos/cliques a seguir.

## 1. Provisionar a instância EC2

No Console AWS (região **us-east-1**, conforme ADR-0011):

1. **EC2 → Launch Instance**.
2. **AMI**: Amazon Linux 2023 (free tier eligible).
3. **Instance type**: `t3.micro`.
4. **Key pair**: crie um par novo (ex.: `sentinel-auth-api-key`) e baixe o `.pem` — é a única forma de acessar a instância via SSH depois (sem senha).
5. **Network settings / Security group**: crie um novo grupo com estas regras de entrada:
   - `SSH` (porta 22) — origem `0.0.0.0/0` (ADR-0011: aceitável para este perfil de projeto, autenticação é só por chave).
   - `HTTP` (porta 80) — origem `0.0.0.0/0`.
6. **Storage**: 20 GB `gp3` (default do free tier costuma ser suficiente; ajuste se precisar).
7. **Launch instance**. Anote o **IP público** exibido depois que a instância entrar em estado `running`.

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

Instale o plugin `docker compose` (Amazon Linux 2023 não traz por padrão):

```bash
DOCKER_CONFIG=${DOCKER_CONFIG:-$HOME/.docker}
mkdir -p $DOCKER_CONFIG/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o $DOCKER_CONFIG/cli-plugins/docker-compose
chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose
docker compose version
```

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

## 4. Mapear a porta 80 (só nesta instância, não no repositório)

Crie um `docker-compose.override.yml` **na instância** (não commitado, entra no `.gitignore` da própria instância ou simplesmente não é versionado ali) para expor a porta 80 sem mexer no `docker-compose.yml` do repositório (ADR-0011: mudança de ambiente, não de aplicação):

```yaml
# docker-compose.override.yml (só nesta instância — não commitar)
services:
  app:
    ports:
      - "80:8080"
```

O Docker Compose já mescla `docker-compose.yml` + `docker-compose.override.yml` automaticamente, sem precisar de flag extra. O container fica com **duas** portas mapeadas (`8080:8080` do repo + `80:8080` do override) — inofensivo: o security group da instância só libera `22`/`80`, então `8080` não fica alcançável de fora mesmo mapeada.

## 5. Subir a aplicação

```bash
docker compose up --build -d
```

Acompanhe os logs até confirmar que a aplicação subiu e as migrations Flyway rodaram:

```bash
docker compose logs -f app
```

## 6. Validar o deploy (critérios de aceite da issue #22)

Do seu próprio computador (substitua `<IP-PÚBLICO>`):

```bash
curl http://<IP-PÚBLICO>/actuator/health
curl http://<IP-PÚBLICO>/swagger-ui.html -I
curl -X POST http://<IP-PÚBLICO>/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke-test@example.com","password":"Str0ngP@ssw0rd!"}'
curl -X POST http://<IP-PÚBLICO>/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke-test@example.com","password":"Str0ngP@ssw0rd!"}'
```

Um `login` bem-sucedido retorna `accessToken`/`refreshToken` — use o `refreshToken` para testar `refresh` e `logout` da mesma forma. Para o critério de RBAC (`GET /api/v1/users` → `403` sem papel `ADMIN`), qualquer usuário recém-registrado já serve (papel padrão é `USER`).

## 7. Depois de validado

- Atualize a seção "Deployment" do `README.md` com o IP público real.
- Pare a instância (`EC2 → Stop instance`, não `Terminate`) quando não estiver testando ativamente — reduz o custo a quase zero fora dos períodos de uso (ADR-0011).

## 8. Deploy automatizado (issue #23)

Depois do primeiro deploy manual (passos 1–7 acima), deploys seguintes podem ser automatizados via GitHub Actions (`.github/workflows/deploy.yml`): a cada push em `main`, depois que o workflow `CI` passar, o `Deploy` conecta via SSH na instância e roda o mesmo `git pull && docker compose up --build -d` do passo 5 — sem registry de imagem (Docker Hub/ECR), a EC2 continua buildando a própria imagem (ADR-0011). Depois de subir, o workflow espera até 60s pelo `/actuator/health` responder — se não responder, o job falha e mostra os últimos logs do container, em vez de reportar sucesso com a aplicação travada.

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
