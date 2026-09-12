# FinAI Android — guia para sessões do Claude Code

Leia isto primeiro em toda sessão nova. Depois leia `planning.md` inteiro (requisitos,
arquitetura, roadmap por fases, critérios de aceite) — ele é a fonte de verdade do
que construir e em que ordem. Este arquivo é sobre *como* trabalhar no repo e
*onde estamos agora*; não duplica o planning.

## Status atual

**Fase 0 (Fundamentos), Fase 1 (MVP sem IA) e Fase 2 (camada de IA com um
provedor) — concluídas.** Ver `planning.md` §9 para a lista de fases.

Fase 0: projeto Gradle (Kotlin 1.9.22, AGP 8.3.1, Compose BOM 2024.02.01,
Navigation-Compose 2.7.7, Room 2.6.1/KSP, DataStore 1.1.1), `applicationId`/namespace
`com.finai.app`, `minSdk` 26/`compileSdk`+`targetSdk` 34, fonte Roboto do sistema.

Fase 1 — o que mudou:
- **Room é a fonte de verdade.** `data/repository/FinanceRepository.kt` envolve os
  DAOs (agora com update/delete reais); `data/repository/FinanceSeeder.kt` insere um
  dataset inicial realista uma única vez (`FinaiPreferences.seeded`), do jeito que uma
  importação de fatura faria — não há mais tela vazia num install novo, mas também
  não há dado fixo/mock disfarçado de real: tudo que aparece na tela é lido do banco.
- **Cálculo determinístico em `domain/`** (planning.md §6): `SafeToSpendCalculator`,
  `SavingsCapacityCalculator`, `GoalCalculator`, `DebtCalculator` (juros + projeção de
  quitação por amortização), `BudgetCalculator`, `SubscriptionCalculator`. Sem nenhuma
  chamada de IA. Cobertos por testes de unidade em `app/src/test/java/.../domain/`
  (`./gradlew testDebugUnitTest`).
- **`state/FinanceViewModel.kt`** (novo, `AndroidViewModel`) — combina os Flows do
  Room, roda os calculators e expõe `FinanceUiState` (goals/debts/budgets/bills já no
  formato que a UI espera) mais as funções de CRUD (`addTransacao`, `saveConta`,
  `saveObjetivo`, `contribuirParaObjetivo`, `saveDivida`, `setBudgetLimit`,
  `toggleAssinatura`, deletes). **Decisão de arquitetura:** ficou um ViewModel só para
  todos os dados financeiros, não um por tela — com um banco local só e nenhuma
  paginação/lifecycle por tela ainda, dividir mais viraria wrapper fino repetido em
  cima dos mesmos Flows. `state/AppViewModel.kt` continua existindo só para estado de
  overlay (chat, sheets, cursor do mês da Agenda) — não mexe em dado persistido.
- **Entrada manual real**: diálogos em `ui/components/EntryDialogs.kt`
  (`AddTransactionDialog`, `AddGoalDialog`, `AddContaDialog`, `AddDividaDialog`,
  `ContributionDialog`, `BudgetLimitDialog`) — Material3 `AlertDialog` simples, sem o
  visual bespoke do resto do app (ver "Pendências" no PR/relatório da sessão).
  Objetivos e dívidas têm exclusão pela própria tela; lançamentos aparecem e podem ser
  excluídos na tela Limites.
- **Textos que dependem de IA viraram placeholder honesto, não dado inventado**: a
  seção "Decisões para você" (Início), a "leitura da IA" de cada objetivo, o roteiro
  de negociação e o chat deixam claro que a Fase 2 ainda não chegou, em vez de mostrar
  texto fixo do protótipo como se fosse análise real em cima dos números reais agora
  na tela. "Linha do tempo do ano" e "Próximos 7 dias", por outro lado, viraram reais
  (lidos de `ContaEntity`), porque são só filtragem de dado, não geração de linguagem.
- `data/fixtures/FinaiFixtures.kt` ficou só com o que é genuinamente Fase 2/4/5
  (chat canned, import simulado, notificações) ou config de UI sem dado pessoal
  (rótulos do menu do FAB, presets do simulador).
- Build (`./gradlew assembleDebug`) e testes (`./gradlew testDebugUnitTest`) verdes
  neste ambiente; **não rodado ainda num aparelho/emulador real** — ver pendências.

Fase 2 — o que mudou:
- **Uma única abstração de IA**: `data/ai/AiProvider.kt` (`interface AiProvider`,
  `AiRequest`, `AiResponse`, `AiText`). `data/ai/GeminiAiProvider.kt` é a única
  implementação — chama o endpoint REST `generateContent` do Gemini direto do app via
  `HttpURLConnection`/`org.json` (sem Retrofit/Ktor: um único provedor não justificava a
  dependência; reavaliar se a Fase 3 trouxer um cliente HTTP compartilhado entre
  provedores). Nenhum ViewModel importa Gemini fora do construtor deles mesmos —
  dependem do tipo `AiProvider`. Modelo padrão em `GeminiAiProvider.DEFAULT_MODEL`
  (`gemini-2.5-flash`); trocar só essa constante se o modelo for descontinuado.
- **Chave guardada com `EncryptedSharedPreferences`/Android Keystore**
  (`data/prefs/AiKeyStore.kt`, singleton via `.get(context)` como `FinaiDatabase.get`),
  nunca em texto puro (planning.md §7.4). Configurada pelo usuário no diálogo atrás do
  ícone de engrenagem na topbar (`ui/components/AiSettingsDialog.kt`) — não há chave
  nenhuma no repositório. `backup_rules.xml`/`data_extraction_rules.xml` excluem esse
  arquivo do backup/transferência (a chave do Keystore que o decifra não viaja entre
  aparelhos mesmo).
- **`state/AiViewModel.kt`** (novo) é o único lugar que fala com o `AiProvider` para os
  quatro pontos ligados a `FinanceViewModel`: leitura da IA por objetivo, veredito do
  simulador, roteiro de negociação de dívida, "Decisões para você". Cada `ensureX` é
  memoizado por uma cache key derivada dos números de entrada, pra não regastar cota a
  cada recomposição. `FinaiApp.kt` dispara os `ensureX` via `LaunchedEffect` por tela e
  passa o resultado (`AiText`: `Loading`/`Ready`/`Unavailable`) pra baixo — nenhuma tela
  fala com IA diretamente.
- **Chat continua em `AppViewModel`** (agora `AndroidViewModel`, com seu próprio
  `GeminiAiProvider`): `sendMessage`/`sendDraft` passam a receber o resumo financeiro
  mínimo (`FinanceUiState.toAiSummaryText()`) e chamam o Gemini de verdade; sem chave ou
  com erro, cai em `FinaiFixtures.offlineReply` (dica local + o motivo da
  indisponibilidade), não trava o chat.
- **Prompts centralizados em `domain/AiPromptBuilder.kt`** — recebem só números/labels já
  computados (Goal/Debt/Budget/Subscription, nunca entidade Room crua), nunca nome
  completo/CPF/conta (planning.md §4); é o único lugar que decide o que vai no prompt.
- **Sem fallback entre provedores, sem controle de cota, sem cache de resposta entre
  dias** — deliberadamente fora do escopo da Fase 2 (planning.md §9 reserva isso pra
  Fase 3); a resiliência aqui é só "sem chave/erro → texto explicativo indisponível,
  números continuam reais" (planning.md §4).
- Build e testes (`testDebugUnitTest`, inalterados — calculators de domínio continuam
  sem IA) verdes neste ambiente. Fluxo de IA **não testado em aparelho real nem com uma
  chave Gemini de verdade** nesta sessão — ver "AÇÃO MANUAL NECESSÁRIA" no relatório da
  sessão.

**Próximo passo:** Fase 3 (multi-provedor e resiliência) — ver planning.md §9.

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
  aparelho sem necessidade; toda chamada de IA (`domain/AiPromptBuilder.kt`)
  minimiza o que entra no prompt e nunca envia documento original, nome
  completo, CPF ou nº de conta.
- **Sem chave de API em texto puro** — nunca commitar chave em código-fonte ou
  em `local.properties`/`gradle.properties` versionado. Desde a Fase 2, a chave
  do Gemini fica em `EncryptedSharedPreferences`/Android Keystore
  (`data/prefs/AiKeyStore.kt`, planning.md §7.4), configurada pelo usuário via
  diálogo — nunca hardcoded.

## Onde procurar o quê

- Requisitos, arquitetura, estratégia de IA, roadmap, critérios de aceite: `planning.md`.
- Protótipo original (referência visual/copy, não para copiar estrutura de código):
  `project/FinAI Mobile.dc.html`, `project/support.js`, `project/ref/*.png`.
- Transcript de design (intenção por trás de cada decisão de UX): `chats/chat1.md`.
- Design tokens já extraídos: `app/src/main/java/com/finai/app/ui/theme/`.
- Cálculo real (Fase 1): `app/src/main/java/com/finai/app/domain/`.
- Dado real (Room): `data/local/`, `data/repository/`. O que resta de fixture
  (Fase 4/5 e config de UI sem dado pessoal): `data/fixtures/FinaiFixtures.kt`.
- Camada de IA (Fase 2): `data/ai/` (abstração + `GeminiAiProvider`),
  `data/prefs/AiKeyStore.kt` (chave), `domain/AiPromptBuilder.kt` (prompts),
  `state/AiViewModel.kt` (goal insight/veredito/negociação/decisões) e o chat
  em `state/AppViewModel.kt`.
