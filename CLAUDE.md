# FinAI Android — guia para sessões do Claude Code

Leia isto primeiro em toda sessão nova. Depois leia `planning.md` inteiro (requisitos,
arquitetura, roadmap por fases, critérios de aceite) — ele é a fonte de verdade do
que construir e em que ordem. Este arquivo é sobre *como* trabalhar no repo e
*onde estamos agora*; não duplica o planning.

## Status atual

**Fase 0 (Fundamentos), Fase 1 (MVP sem IA), Fase 2 (camada de IA com um
provedor) e Fase 3 (multi-provedor e resiliência) — concluídas.** Ver
`planning.md` §9 para a lista de fases.

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

Fase 3 — o que mudou:
- **`AiProvider` virou interface implementada por seis classes**: `GeminiAiProvider`
  (formato bespoke do Gemini, inalterado), `OpenAiCompatibleAiProvider` (classe base
  abstrata para o formato `chat/completions` estilo OpenAI, com `Authorization: Bearer`)
  e suas quatro subclasses finas — `GroqAiProvider`, `OpenRouterAiProvider`,
  `MistralAiProvider`, `CerebrasAiProvider` — mais `AiRouter`, que também implementa
  `AiProvider` e é o único que `AiViewModel`/`AppViewModel` constroem agora. Nenhum
  ViewModel importa um provedor concreto.
- **`AiRouter`** (`data/ai/AiRouter.kt`) tenta os provedores na ordem do planning.md
  §7.3 (Gemini → Groq → OpenRouter → Mistral → Cerebras), pulando um provedor sem
  chave configurada ou já esgotado hoje, e só devolve `Unavailable` (com mensagem de
  degradação graciosa) quando todos falham. Nunca muda de modelo/tier dentro de um
  provedor ao falhar — um provedor que falha é só pulado.
- **`AiResponse.Unavailable` ganhou `kind: AiFailureKind`** (`NO_KEY`, `AUTH_ERROR`,
  `RATE_LIMITED`, `MODEL_UNAVAILABLE`, `TIMEOUT`, `NETWORK_ERROR`, `EMPTY_RESPONSE`,
  `UNKNOWN`) — cada adaptador classifica o status HTTP/exceção; o roteador só marca um
  provedor como esgotado do dia (`ProviderUsageStore`) quando `kind == RATE_LIMITED`.
- **`ProviderUsageStore`** (`data/ai/ProviderUsageStore.kt`, DataStore próprio
  `finai_ai_usage`, sem PII) guarda por provedor a contagem de chamadas do dia e a data
  em que ele foi marcado esgotado (planning.md §5 `UsoProvedorIA` / §7.3). Reativo, não
  preditivo: só esgota depois de um 429 de verdade, nunca chuta o limite diário de um
  provedor (que muda sem aviso). Testável via a interface `UsageTracker` que a classe
  implementa.
- **`AiResponseCache`** (`data/ai/AiResponseCache.kt`, `object` em memória, chave =
  hash de task+system+prompt, só resposta do dia corrente) evita regastar cota para a
  mesma pergunta/simulação repetida no mesmo dia — planning.md §7.3.
- **`AiKeyStore` guarda uma chave por provedor** (`ProviderId`: `GEMINI`, `GROQ`,
  `OPENROUTER`, `MISTRAL`, `CEREBRAS`), ainda em `EncryptedSharedPreferences`/Android
  Keystore, com migração automática de uma vez da chave antiga do Gemini (Fase 2)
  para o novo esquema por provedor. `ui/components/AiSettingsDialog.kt` virou uma
  lista de 5 campos (um por provedor), nenhum obrigatório.
- **Modelos gratuitos configurados** (cada um documentado com a URL de docs
  conferida e a data da conferência, no companion object do respectivo arquivo —
  conferir lá antes de trocar): Gemini `gemini-2.5-flash` (corrigido nesta sessão —
  estava `gemini-3.7-flash`, um nome não verificável na documentação atual, provável
  alucinação de uma sessão anterior), Groq `llama-3.1-8b-instant`, OpenRouter
  `google/gemma-4-31b-it:free` (confirmado ao vivo via `GET
  https://openrouter.ai/api/v1/models`), Mistral `mistral-small-latest`, Cerebras
  `gpt-oss-120b` (confirmado ao vivo via `GET
  https://api.cerebras.ai/public/v1/models`, que hoje só lista dois modelos). Nenhum é
  de tier pago; ver o relatório da sessão para os avisos de "nunca adicionar cartão"
  específicos de Cerebras/Groq.
- **Testes novos**: `app/src/test/java/.../data/ai/AiRouterTest.kt` — fallback ao
  receber 429 do provedor principal, provedor esgotado é pulado sem nova chamada,
  degradação graciosa quando todos falham, mensagem distinta quando nenhuma chave está
  configurada, e cache servindo a segunda chamada idêntica no mesmo dia. Usa fakes de
  `AiProvider`/`UsageTracker`, nunca rede real ou Context Android — por isso `AiRouter`
  tem um construtor secundário `(Context)` além do primário `(UsageTracker,
  List<Pair<ProviderId, AiProvider>>)`. Precisou de
  `android.testOptions.unitTests.isReturnDefaultValues = true` no `app/build.gradle.kts`
  porque `android.util.Log` (chamado pelo roteador/adaptadores) explode em teste JVM
  puro sem esse flag.
- Build (`./gradlew assembleDebug`) e testes (`./gradlew testDebugUnitTest`) verdes
  neste ambiente. **Nenhuma chave real de nenhum provedor foi testada nesta sessão**
  (nem Gemini, que já vinha pendente da Fase 2) — ver "AÇÃO MANUAL NECESSÁRIA" no
  relatório da sessão para como configurar e verificar cada uma.

**Próximo passo:** Fase 4 (importação de fatura) — ver planning.md §9.

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
  em `local.properties`/`gradle.properties` versionado. Desde a Fase 2 (e agora
  para os cinco provedores da Fase 3), toda chave fica em
  `EncryptedSharedPreferences`/Android Keystore (`data/prefs/AiKeyStore.kt`,
  planning.md §7.4), configurada pelo usuário via diálogo — nunca hardcoded.
- **Nunca fallback para modelo/tier pago** (planning.md §4/§9): cada adaptador em
  `data/ai/` hard-coda um `DEFAULT_MODEL` gratuito documentado com a fonte
  conferida; `AiRouter` só pula um provedor que falhou, nunca troca de modelo
  dentro dele. Antes de trocar qualquer `DEFAULT_MODEL`, confira a documentação
  atual do provedor (citada no comentário da constante) — nomes/gratuidade mudam
  sem aviso.

## Onde procurar o quê

- Requisitos, arquitetura, estratégia de IA, roadmap, critérios de aceite: `planning.md`.
- Protótipo original (referência visual/copy, não para copiar estrutura de código):
  `project/FinAI Mobile.dc.html`, `project/support.js`, `project/ref/*.png`.
- Transcript de design (intenção por trás de cada decisão de UX): `chats/chat1.md`.
- Design tokens já extraídos: `app/src/main/java/com/finai/app/ui/theme/`.
- Cálculo real (Fase 1): `app/src/main/java/com/finai/app/domain/`.
- Dado real (Room): `data/local/`, `data/repository/`. O que resta de fixture
  (Fase 4/5 e config de UI sem dado pessoal): `data/fixtures/FinaiFixtures.kt`.
- Camada de IA (Fase 2/3): `data/ai/` — `AiProvider` (interface),
  `GeminiAiProvider`/`OpenAiCompatibleAiProvider`+4 subclasses (adaptadores),
  `AiRouter` (fallback), `ProviderUsageStore` (cota diária),
  `AiResponseCache` (cache em memória); `data/prefs/AiKeyStore.kt` (chaves, uma
  por provedor); `domain/AiPromptBuilder.kt` (prompts); `state/AiViewModel.kt`
  (goal insight/veredito/negociação/decisões + gestão das chaves) e o chat em
  `state/AppViewModel.kt`. Testes do roteador:
  `app/src/test/java/.../data/ai/AiRouterTest.kt`.
