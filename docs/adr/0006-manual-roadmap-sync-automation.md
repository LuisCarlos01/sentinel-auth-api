# 0006 — Automação de sincronização do roadmap manual e aprovada por humano

## Status

Accepted

## Contexto

O projeto mantém um roadmap versionado no `README.md`, e cada versão desse roadmap (`v0.1.0`, `v0.2.0`, ...) vira um milestone no GitHub, com uma ou mais issues `[Phase N]` seguindo um padrão de título, corpo e labels estabelecido manualmente ao longo das issues #1-#5. Recriar esse padrão à mão a cada fase concluída é repetitivo e sujeito a inconsistência (título fora do formato, seção do corpo esquecida, label errada).

Automatizar totalmente esse fluxo via CI — um gatilho automático disparado em push ou PR na `main` — foi considerado e descartado.

## Decisão

A sincronização do backlog (fechar o milestone concluído, criar o próximo milestone do roadmap, criar sua issue com labels) é feita por um runbook de invocação **manual**, o comando `/sync-roadmap` (ver `.claude/commands/sync-roadmap.md`), nunca por um gatilho automático de CI ou git hook.

O conteúdo de cada issue é rascunhado por um subagente a partir da documentação viva do projeto (PRD, arquitetura, ADRs), mas só é publicado no GitHub após aprovação humana explícita — nenhum milestone ou issue é criado sem essa etapa. O `README.md` nunca é editado por esse fluxo; atualizar o roadmap continua sendo decisão manual do usuário.

### Alternativas consideradas

- **Gatilho automático via GitHub Action em push/PR na `main`**: descartado porque cria artefatos públicos e permanentes no GitHub (milestones, issues) sem revisão humana. Além disso, o repositório hoje não usa Pull Requests — todo o histórico é push direto na `main` —, então não existe um evento de "PR merged" confiável para ancorar esse gatilho.
- **Conteúdo gerado livremente por LLM sem revisão**: descartado pelo risco de alucinação ou drift em relação ao padrão de título/corpo/labels já estabelecido nas issues existentes.
- **Conteúdo 100% de um arquivo estruturado estático (ex.: YAML de roadmap escrito à mão)**: descartado por perder a flexibilidade de o subagente ler a documentação viva do projeto (PRD, arquitetura, ADRs) a cada execução, correndo o risco de duplicar e desatualizar informação que já vive em outro lugar.

## Consequências

**Positivas**

- Zero risco de issue ou milestone incorreto ir parar no GitHub sem revisão — a etapa de aprovação humana é obrigatória e nunca pulada.
- A documentação do projeto continua sendo a fonte da verdade lida a cada execução, em vez de duplicada em configuração estática que pode ficar desatualizada.
- O `README.md` permanece a única fonte de verdade do roadmap, sempre editado manualmente pelo usuário.

**Trade-offs aceitos**

- O fluxo não é "zero toque": o usuário ainda precisa lembrar de invocar `/sync-roadmap` a cada fase concluída, não há gatilho algum.
- Se o projeto crescer e passar a adotar Pull Requests, essa decisão pode ser revisitada em favor de um gatilho semi-automático ancorado em "PR merged".
