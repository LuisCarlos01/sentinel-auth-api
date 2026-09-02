---
description: Fecha a fase concluída do roadmap (milestone com todas as issues fechadas) e prepara a próxima fase — rascunho de issue + milestone, aguardando aprovação antes de criar no GitHub.
---

# /sync-roadmap

Runbook para sincronizar o backlog do GitHub (`LuisCarlos01/sentinel-auth-api`) com o roadmap versionado do `README.md`, replicando o padrão já estabelecido pelas issues #1-#5. Disparo é sempre MANUAL — invoque este comando quando considerar uma fase do roadmap concluída (implementação mergeada/pushada na `main`), nunca automaticamente em CI.

Se você é o maestro orquestrador com acesso à ferramenta Agent e aos especialistas (discovery/executor/review), delegue cada etapa como abaixo. Se não houver esse acesso na sessão atual, execute os mesmos passos diretamente via Bash + `gh`.

## Padrão estabelecido (não redescubra do zero a cada execução — mas VERIFIQUE, não assuma cego)

- Milestones nomeados `vX.Y.Z — Nome do Roadmap`, com em dash real (`—`, U+2014), extraído literalmente do roadmap em `README.md`.
- Issues tituladas `[Phase N] <ação curta, imperativo, inglês>`, numeração de Phase GLOBAL (não reseta por milestone) — próximo N é sempre "último Phase criado + 1".
- Corpo em Markdown, seções H2 nesta ordem exata: `## Objective`, `## Scope`, `## Technical Stack` (opcional), `## Deliverables` (checklist `- [ ]`), `## Acceptance Criteria` (checklist `- [ ]`), `## Related Release` (`Target milestone: vX.Y.Z — Nome`).
- Labels: sempre 1+ de tipo/área dentre as já existentes no repo (confira com `gh label list`; NUNCA invente uma nova sem confirmar com o usuário) + exatamente 1 `priority: high|medium|low` + exatamente 1 `status: ready|blocked` (`blocked` quando há decisão técnica real em aberto no escopo, não só por depender da fase anterior).
- Idioma 100% inglês no conteúdo da issue, sem emojis, sem menções cruzadas a outras issues/PRs.
- Assignee: dono do repositório (`LuisCarlos01`).
- `README.md` NUNCA é editado por este fluxo — atualizar os checkboxes do roadmap continua sendo decisão manual do usuário (ou um PR revisado à parte).

## Procedimento

1. **Verificar estado atual** (delegar a `review`, read-only): `gh api repos/LuisCarlos01/sentinel-auth-api/milestones --method GET -f state=all`, `gh issue list --state all --limit 100`, `gh label list`. Determinar: (a) qual é o milestone aberto mais antigo hoje; (b) se todas as issues desse milestone estão fechadas; (c) qual é o próximo `Phase N` / próximo milestone da sequência do roadmap ainda sem issue criada.

2. **Decidir se avança**:
   - Se o milestone aberto mais antigo AINDA tem issue(s) aberta(s): reporte o status ao usuário (quantas issues faltam fechar) e PARE — não crie nada. Ofereça, só se o usuário pedir explicitamente, rascunhar a próxima fase adiantado sem criar nada ainda.
   - Se todas as issues do milestone aberto mais antigo estão fechadas (ou não há nenhum milestone aberto): prossiga.

3. **Fechar o milestone concluído**, se aplicável (delegar a `executor`): `gh api repos/LuisCarlos01/sentinel-auth-api/milestones/{number} --method PATCH -f state=closed`.

4. **Rascunhar a próxima fase** (delegar a `discovery`, read-only): ler `README.md` (roadmap), `docs/prd.md`, `docs/architecture.md`, ADRs relevantes em `docs/adr/`, e docs técnicos relevantes em `docs/technologies/`/`docs/integrations/` para montar o conteúdo da próxima issue no padrão acima. Sinalizar explicitamente qualquer ponto do escopo que não esteja fundamentado em documentação (não inventar).

5. **Apresentar para aprovação**: mostrar ao usuário o título, corpo completo, labels propostas e nome/descrição do milestone (se for um milestone novo). NÃO criar nada no GitHub antes de aprovação explícita do usuário. Esta é a etapa "híbrida" — o subagente rascunha, o humano decide.

6. **Criar de verdade** (delegar a `executor`, só após aprovação): verificar idempotência (`gh issue list`/`gh api milestones` de novo, evitar duplicar), criar o milestone se necessário (mesmo formato de título/descrição do padrão), gravar o corpo em arquivo temporário e usar `gh issue create --title "..." --body-file ... --label "..." --milestone "..." --assignee "LuisCarlos01"`.

7. **Verificar** (delegar a `review`, read-only): conferir se título, labels, milestone, assignee e corpo da issue recém-criada batem com o rascunho aprovado. Reportar veredito final ao usuário com os links.

## Restrições

- Nunca dispare isso automaticamente via git hook, GitHub Action ou CI — é sempre invocação manual.
- Nunca crie uma label nova sem confirmar antes com o usuário.
- Nunca edite `README.md`, nem issues/milestones já fechados.
- Nunca pule a etapa 5 (aprovação) — mesmo que o rascunho pareça óbvio.
