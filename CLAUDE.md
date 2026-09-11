# FinAI Android — guia para sessões do Claude Code

Leia isto primeiro em toda sessão nova. Depois leia `planning.md` inteiro (requisitos,
arquitetura, roadmap por fases, critérios de aceite) — ele é a fonte de verdade do
que construir e em que ordem. Este arquivo é sobre *como* trabalhar no repo e
*onde estamos agora*; não duplica o planning.

## Status atual

**Fase 0 (Fundamentos) — concluída.** Ver `planning.md` §9 para a lista de fases.

- Projeto Gradle criado do zero: Kotlin 1.9.22, AGP 8.3.1, Compose (BOM 2024.02.01),
  Navigation-Compose 2.7.7, Room 2.6.1 (KSP), DataStore 1.1.1.
- `applicationId`/namespace: `com.finai.app`. `minSdk` 26, `compileSdk`/`targetSdk` 34.
- As 6 telas do protótipo (Início, Agenda, Objetivos, Dívidas, Limites, Importar)
  estão navegáveis com dados fixos em `data/fixtures/FinaiFixtures.kt` — os mesmos
  números e textos do protótipo (`project/FinAI Mobile.dc.html` + `support.js`).
- Overlays (FAB, simulador "Posso comprar?", chat, notificações) funcionam com
  estado em memória via `state/AppViewModel.kt` — **um único ViewModel para tudo,
  de propósito**, espelhando o `state` único do protótipo. Isso é um atalho
  deliberado da Fase 0, não a arquitetura final.
- Room e DataStore estão configurados e compilam (`data/local/`, `data/prefs/`)
  mas **nenhuma tela usa isso ainda** — é scaffolding para a Fase 1.
- Build validado de ponta a ponta via `./gradlew assembleDebug` (linha de comando,
  sem device conectado neste ambiente — ainda não visto rodando num aparelho/emulador
  de verdade).
- Fonte: Roboto (padrão do sistema) com os mesmos tamanhos/pesos do protótipo, não
  o Inter real — trade-off deliberado da Fase 0 pra não travar em bundlar `.ttf`.

**Próximo passo:** Fase 1 (MVP sem IA) — entrada manual de lançamentos/contas/objetivos/
dívidas, e os cálculos determinísticos reais (saldo seguro, progresso de metas,
capacidade de poupança, juros e ordem de dívidas, progresso de orçamento) substituindo
`FinaiFixtures`. Ver planning.md §6 e §9.

## Como retomar uma sessão

1. Leia este arquivo + `planning.md` completo antes de propor qualquer mudança.
2. Rode `git log --oneline -20` e `git status` pra ver o que já foi commitado e o
   que está pendente no working tree.
3. Se for continuar uma fase em andamento, procure por comentários `// TODO` e
   por divergências entre o código e a seção "Status atual" acima — se algo aqui
   estiver desatualizado, corrija este arquivo como parte do trabalho.
4. Ao terminar uma fase (ou um pedaço grande o suficiente de uma fase), **atualize
   a seção "Status atual" acima** antes de encerrar a sessão. Isso é o que permite
   a próxima sessão continuar sem re-explorar o projeto do zero.
5. Perguntas que ficaram em aberto durante o trabalho (arquitetura, escopo, UX)
   devem ser resolvidas com o usuário e a resposta registrada em `planning.md`
   §11 ("Perguntas em aberto") — não deixe a resposta só na conversa.

## Convenções do projeto

- **Commits**: um commit por fase ou por sub-tarefa fechada dentro de uma fase,
  não um commit gigante no fim. Mensagem explica o *porquê* quando a escolha não
  é óbvia (ex.: por que um ViewModel único, por que essa versão do AGP).
- **Fixtures antes de lógica real**: ao implementar uma tela nova, comece pela UI
  com dados fixos (como a Fase 0), só then plugue Room/cálculo real — mais fácil
  de revisar visualmente antes de misturar com lógica.
- **IA é só linguagem, cálculo é local** (planning.md §6): não chame nenhum provedor
  de IA para números que dá pra calcular em Kotlin puro. Isso é o que faz o app
  caber no free tier — não regrida nisso "pra simplificar".
- **Privacidade por padrão** (planning.md §4): nenhum dado financeiro sai do
  aparelho sem necessidade; ao integrar IA (Fase 2+), minimize o que entra no
  prompt e nunca envie documento original, nome completo, CPF ou nº de conta.
- **Sem chave de API em texto puro** — nunca commitar chave em código-fonte ou
  em `local.properties`/`gradle.properties` versionado. Quando a Fase 2 chegar,
  usar `EncryptedSharedPreferences`/Android Keystore (planning.md §7.4).

## Onde procurar o quê

- Requisitos, arquitetura, estratégia de IA, roadmap, critérios de aceite: `planning.md`.
- Protótipo original (referência visual/copy, não para copiar estrutura de código):
  `project/FinAI Mobile.dc.html`, `project/support.js`, `project/ref/*.png`.
- Transcript de design (intenção por trás de cada decisão de UX): `chats/chat1.md`.
- Design tokens já extraídos: `app/src/main/java/com/finai/app/ui/theme/`.
- Dados de exemplo (a substituir por dados reais na Fase 1): `data/fixtures/FinaiFixtures.kt`.
