# FinAI Android — guia para sessões do Claude Code

Leia isto primeiro em toda sessão nova. Depois leia `planning.md` inteiro (requisitos,
arquitetura, roadmap por fases, critérios de aceite) — ele é a fonte de verdade do
que construir e em que ordem. Este arquivo é sobre *como* trabalhar no repo e
*onde estamos agora*; não duplica o planning.

## Status atual

**Fase 0 (Fundamentos), Fase 1 (MVP sem IA), Fase 2 (camada de IA com um
provedor), Fase 3 (multi-provedor e resiliência), Fase 4 (importação de
fatura), Fase 5 (notificações proativas e coach comportamental) e Fase 6
(endurecimento e lançamento, no que depende de código) — concluídas. Fase 7
(clareza e confiança) — implementada e mergeada no `main` em 28/09/2026, a pedido do
usuário, depois do primeiro ajuste pós-teste (letras um degrau menores; Agenda volta ao
"toca na linha, paga").**
Ver `planning.md` §9 para a lista de fases.

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
  conferir lá antes de trocar): Gemini `gemini-3.7-flash` (**correção da Fase 6:** esta
  seção afirmava que o modelo tinha sido trocado para `gemini-2.5-flash`, mas o código
  nunca mudou — e não precisava: `gemini-3.7-flash` está listado como modelo estável
  com free tier em ai.google.dev/gemini-api/docs/models e /pricing, conferido em
  12/09/2026), Groq `llama-3.1-8b-instant` (**trocado em 28/09/2026 para `openai/gpt-oss-120b`**, com `reasoning_effort: low` e `include_reasoning: false`, porque o Llama 8B saiu do plano Free do Groq), OpenRouter
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

Fase 4 — o que mudou:
- **Cinco camadas separadas, nenhuma delas dentro de Composable ou ViewModel**:
  seleção/IO (`data/importer/StatementImporter.kt` — o único lugar que conhece
  `Uri`/`ContentResolver`), extração de texto (`data/importer/DocumentTextExtractor.kt`:
  `PdfTextExtractor`, `ImageTextExtractor`, `SpreadsheetTextExtractor` + `OcrEngine`),
  parsing determinístico (`domain/importer/`), decisão/classificação
  (`domain/importer/ImportAnalyzer.kt`) e persistência (`state/ImportViewModel.kt` →
  `FinanceRepository`). Room continua sendo a fonte de verdade: importação só grava
  `TransacaoEntity(origem = "importado")` e, para recorrência confirmada,
  `AssinaturaEntity` — nenhum schema novo, nenhuma migração.
- **OCR on-device com ML Kit** (`com.google.mlkit:text-recognition:16.0.1`, variante
  *bundled*: o modelo latino vai no APK, então funciona offline e sem Play Services
  baixar nada). `OcrEngine.toReadingOrderLines` reagrupa os blocos do ML Kit por
  proximidade vertical e ordena por x — sem isso, uma tabela de fatura vira três
  colunas soltas e nenhum lançamento é reconhecido.
- **PDF via `PdfRenderer` + OCR, não via biblioteca de PDF.** O Android não expõe
  leitura da camada de texto de um PDF; rasterizar a 3x e passar no OCR cobre PDF
  digital e escaneado com o mesmo caminho e sem somar vários MB ao APK. Custo: é mais
  lento e depende do render (teto de 20 páginas por importação). PDF com senha falha
  com mensagem explicando que é preciso remover a senha.
- **Planilha sem dependência externa**: `CsvReader` (detecta `;`/`,`/tab, trata aspas,
  cai para ISO-8859-1 quando não é UTF-8 válido) e `XlsxReader` (o .xlsx é um ZIP de
  XML — `java.util.zip` + varredura de tags, com `sharedStrings` e data em número de
  série do Excel). **.xls binário antigo não é suportado** de propósito: exigiria
  Apache POI (>10 MB) para um formato que o Excel já exporta como .xlsx/.csv.
- **Parsing determinístico** (`BrazilianStatementFormats`, `StatementTextParser`,
  `SpreadsheetStatementParser`): datas (`12/03`, `12/03/26`, `2026-03-12`, `12 MAR`,
  `15 de janeiro`, número de série do Excel), moeda brasileira (`R$ 1.234,56`,
  `89,90-`, `(35,90)`, `123,00 CR`, `1234.56` de planilha exportada), parcelas
  (`03/10`, `PARC 3/10`, `PARCELA 2 DE 6`), descrição quebrada em várias linhas
  (linha com data inicia o bloco, linhas sem data são continuação), estorno como valor
  negativo, compra internacional usando o valor em R$ e não o em USD, e descarte de
  linha de cabeçalho/total/rodapé. Planilha de banco com gasto negativo tem o sinal
  invertido no arquivo inteiro, para bater com a convenção do app (gasto positivo).
- **IA só no que a regra local não resolveu** (planning.md §6): `MerchantClassifier`
  (tabela de estabelecimentos brasileiros), `DuplicateDetector` (mesma data + mesmo
  valor + mesmo estabelecimento = `LIKELY`; valor igual com data/descrição próximas =
  `POSSIBLE`) e `RecurrenceDetector` (assinatura conhecida ou mesma cobrança em 2+
  meses = `LIKELY`) rodam primeiro. Só os `POSSIBLE`/sem categoria vão para
  `data/ai/ImportAiAssistant.kt`, que usa o **mesmo `AiRouter` da Fase 3** (nada de
  integração paralela), em **no máximo 3 chamadas por importação** (uma por tipo de
  pergunta, em lote de até 25 itens) em vez de uma por lançamento.
- **O arquivo nunca sai do aparelho.** O que vai no prompt é `"3. mercado sao joao —
  R$ 189,90 em 12/03"`: estabelecimento normalizado por
  `MerchantClassifier.normalizeMerchant` (tira prefixo de adquirente, sufixo de razão
  social e **qualquer sequência de 4+ dígitos**, que é o que carregaria final de cartão
  ou código de loja), valor e dia/mês. Nem nome de arquivo, nem linha original, nem
  imagem. Há teste que falha se isso regredir (`ImportAnalyzerTest`).
- **Sem IA o fluxo continua inteiro**: item ambíguo fica com "Outros" + selo "a
  revisar" (honesto, não chutado), duplicata/recorrência mantêm o veredito local, e a
  tela mostra o motivo da IA estar indisponível. Exceção na camada de IA é capturada —
  importação nunca cai por causa dela.
- **Tela de revisão real** (`ui/screens/importer/ImportScreen.kt`, reescrita): seletor
  PDF/Planilha/Foto via SAF (`ActivityResultContracts.OpenDocument`, sem permissão
  nova no manifesto), etapas visíveis do processamento, lista revisável com checkbox
  por item, troca de categoria por menu, selos de duplicata/assinatura/parcela/estorno
  e barra de confirmação com total. Duplicata provável entra **desmarcada**. O estágio
  simulado da Fase 0 (`FinaiFixtures.importSteps/importTitle/...`, `importStage` no
  `AppViewModel`) foi removido.
- **Testes**: 31 testes de unidade novos em `app/src/test/java/.../domain/importer/`
  (formatos, parser de texto, parser de planilha incluindo .xlsx montado em memória,
  classificação, duplicata, recorrência e o pipeline completo com fake de `AiProvider`
  — com IA, sem IA, offline e com exceção). Mais **11 testes instrumentados** em
  `app/src/androidTest/.../data/importer/`, que rodam OCR de verdade no emulador
  (`./gradlew connectedDebugAndroidTest`) sobre as faturas de exemplo em `samples/`.
- **`samples/`** tem a mesma fatura em .csv, .xlsx, .pdf e .png (ver `samples/README.md`)
  — serve tanto para testar à mão no aparelho quanto como asset do teste instrumentado
  (`app/build.gradle.kts` aponta os assets de `androidTest` para essa pasta).
- Build (`assembleDebug`), `testDebugUnitTest` (66 testes) e `connectedDebugAndroidTest`
  (11 testes, Pixel 6 API 34) verdes. Fluxo conferido à mão no emulador: escolher
  arquivo → revisar → salvar → reimportar o mesmo arquivo e ver as 6 duplicatas
  sinalizadas. **Não testado com uma chave de IA real** (nenhum provedor foi
  configurado nesta sessão) nem com uma fatura real de banco.

Revisão pré-Fase 5 (auditoria de mock/fixture vs. critérios de aceite das Fases 1–4):
- **Status de conta virou cálculo, não campo gravado** (`domain/BillStatusCalculator.kt`,
  novo). `"atrasado"`/`"vence_hoje"` estavam declarados no schema mas nenhum código os
  escrevia: a Agenda mostrava "Pendente" numa conta vencida há meses. Agora
  `toUiBill`/`toUiWeekBill` derivam o status do vencimento vs. hoje (planning.md §6 lista
  status de conta como cálculo local determinístico); `ContaEntity.status` guarda só o que
  a data não revela (`"pago"`/`"pendente"`). `BillStatus` ganhou `Expected` ("Previsto")
  para conta a receber.
- **Avisos do sino saíram da fixture** (`domain/AlertCalculator.kt`, novo). O badge da
  topbar era a constante `FinaiFixtures.notifCount = 1` e o dropdown mostrava um item fixo
  "chega na Fase 5". Agora a lista é calculada do Room — conta atrasada, conta vencendo em
  até 3 dias, categoria estourada ou projetada acima do limite, assinatura parada, entrada
  prevista em até 7 dias (exatamente os casos de planning.md §3.9), ordenada por
  severidade, e o badge é `alerts.size`. Nenhuma IA envolvida: o que a **Fase 5** ainda
  acrescenta é a *entrega* proativa fora do app (`WorkManager`) e o texto redigido por IA,
  não a existência da lista.
- **Histórico do chat passou a ser persistido** (`data/repository/ChatRepository.kt`,
  novo). A tabela `mensagens_chat` e o `MensagemChatDao` existiam desde a Fase 0 e nada os
  usava: a conversa vivia em memória e todo restart voltava para uma mensagem de boas-vindas
  fixa (`FinaiFixtures.initialMessages`), que parecia resposta de IA e não era. Agora
  `AppViewModel` coleta `ChatRepository.mensagens` (Room é a fonte de verdade, planning.md
  §5) e grava pergunta e resposta; a tela vazia mostra um cartão de apresentação, não uma
  mensagem falsa. Corrigida também a linha "lê seus últimos 90 dias" do cabeçalho do chat —
  a IA recebe o resumo agregado de `FinanceUiState.toAiSummaryText()`, não o extrato
  (planning.md §4).
- **`FinaiFixtures` não tem mais nenhum dado financeiro** — sobrou só config de UI sem dado
  pessoal (presets do simulador, sugestões de pergunta, rótulos do FAB) e `offlineReply`.
  `NotificationItem`/`NegotiationStep` (models órfãos) foram removidos.
- Revisado e **sem mock**: Início, Agenda, Objetivos, Dívidas, Limites e Importar já liam
  tudo de Room/`domain/`; os textos de IA são `AiText.Loading/Ready/Unavailable` com
  fallback determinístico honesto (veredito do simulador, roteiro de negociação genérico),
  não texto de protótipo disfarçado.
- Pendências conhecidas, **deliberadamente não mexidas** nesta revisão: (a)
  `FinanceSeeder` continua inserindo um dataset inicial realista no primeiro run — é dado
  real e editável no Room, não fixture de tela, e a decisão está documentada na Fase 1;
  (b) Agenda não tem a "dica contextual da IA sobre ordem de pagamento" de planning.md
  §3.2 (não está na lista de pontos de IA da Fase 2); (c) o simulador não mostra o efeito
  na cobertura da reserva de emergência (planning.md §3.7); (d) não há ação de "marcar
  conta como paga" na UI — o único jeito de uma conta virar `"pago"` hoje é pelo seeder.
- Build (`assembleDebug`) e testes verdes: **76 testes de unidade** (66 anteriores + 10
  novos em `BillStatusCalculatorTest` e `AlertCalculatorTest`). Instrumentados não
  reexecutados nesta sessão.

Fase 5 — o que mudou:
- **`FinanceCheckWorker`** (`data/work/`, `CoroutineWorker`) é a rotina periódica
  (planning.md §5/§9) — `PeriodicWorkRequestBuilder(24h, flex 4h)`, `KEEP` policy,
  sem restrição de rede (a detecção é local e deve funcionar offline). Agendado uma vez
  em `FinaiApplication.onCreate()` (novo `android:name` no manifesto); sobrevive a app
  fechado e a reboot porque o próprio WorkManager persiste o agendamento — nenhum
  `BroadcastReceiver` de boot foi necessário.
- **Quatro camadas separadas, nenhuma dentro do Worker ou de um Composable** (exigência
  explícita desta fase): **detecção** — `domain/AlertCalculator.kt` (estendido: agora
  recebe `List<GoalPlan>` e também sinaliza objetivos que precisam de atenção e "entrada
  extra confirmada", planning.md §3.9) e `domain/BehaviorCoach.kt` (novo, o "coach de
  comportamento" real); **conteúdo** — `data/notifications/NotificationContentBuilder.kt`
  (só decide o *texto*, nunca se o evento existe); **entrega** —
  `data/notifications/FinaiNotifier.kt` (único lugar que fala com
  `NotificationManagerCompat`); **deduplicação** —
  `data/notifications/NotificationDedupeStore.kt`, sobre uma tabela Room nova
  (`NotificacaoEnviadaEntity`/`notificacoes_enviadas`, `FinaiDatabase` versão 2→3).
- **Dedup por dia, não por evento único**: cada `FinanceAlert.id`/`BehaviorPattern.id` só
  notifica uma vez por data local (`ultimoEnvio`); o mesmo id continua "existindo" (e
  volta a notificar no dia seguinte) enquanto o problema não for resolvido — uma conta
  atrasada lembra todo dia, um orçamento estourado também, exatamente o critério de
  aceite da Fase 5 ("no máximo as notificações relevantes daquele dia, não uma por
  hora", planning.md §10). Quando o id some da lista que `AlertCalculator`/`BehaviorCoach`
  devolvem (conta paga, orçamento normalizado), `pruneExcept` limpa o registro.
- **IA só entra quando já existe evento, nunca mais de 2 chamadas por execução**: uma
  para o lote de `FinanceAlert`s "novos hoje" (resumidos numa única notificação se houver
  mais de um) e uma para o padrão do coach — nunca uma chamada por alerta. Usa o mesmo
  `AiRouter` da Fase 3 (`AiTask.PROACTIVE_ALERT`/`BEHAVIOR_COACH` novos em
  `AiProvider.kt`, prompts em `AiPromptBuilder.kt`); sem eventos, o Worker não instancia
  o router — zero chamadas de IA num dia sem nada relevante. Sem IA disponível, a
  notificação sai igual com o texto determinístico do próprio alerta/padrão (nunca "IA
  indisponível" como corpo da notificação).
- **`domain/BehaviorCoach.kt`** é o "coach de comportamento" de planning.md §3.1,
  substituindo o placeholder da Fase 1 ("maior categoria de gasto... chega na Fase 5").
  Como `TransacaoEntity.data` só guarda o dia (sem hora), os padrões são por dia da
  semana e repetição, não por horário como o protótipo sugeria: crescimento de categoria
  mês a mês (≥30%), estabelecimento repetido ≥4x no mês (hábito, exclui assinatura já
  conhecida), gasto concentrado no fim de semana (≥60%) ou em dias úteis (≥85%). Cálculo
  100% determinístico; a IA (`ensureCoachInsight` em `AiViewModel`) só redige
  `pattern.detail` em linguagem mais natural, com o texto determinístico como fallback
  imediato — mesma convenção do simulador. O cartão "Coach" da Início
  (`HomeScreen.CoachCard`) some quando não há padrão relevante, em vez de mostrar texto
  fixo.
- **Permissão de notificação (Android 13+)**: `POST_NOTIFICATIONS` no manifesto, pedida
  em runtime por `MainActivity` via `ActivityResultContracts.RequestPermission()` — negada
  ou não, o app continua funcionando por completo (`FinaiNotifier.hasNotificationPermission()`
  faz o Worker pular só o `notify()`, nunca falhar). Diagnóstico e um botão "Testar agora"
  (dispara `FinanceCheckWorker.runOnce`, um `OneTimeWorkRequest` avulso que não mexe no
  agendamento periódico) foram colocados na tela de configuração de IA
  (`AiSettingsDialog.kt`), por ser o lugar existente mais próximo de "diagnóstico do app".
- **Fechada uma lacuna que a Fase 5 dependia para ser testável**: não havia nenhuma ação
  de UI para uma conta sair de `"pendente"` (só o seeder gravava `"pago"`). Agenda ganhou
  toque para alternar paga/pendente em `BillRow`
  (`FinanceViewModel.marcarContaPaga`) — sem isso não dava para simular "conta atrasada
  resolvida" nem "entrada extra confirmada" de verdade.
- **Testes novos** (20): `BehaviorCoachTest` (crescimento, hábito frequente, concentração
  fim de semana/dia útil, prioridade entre padrões), `AlertCalculatorTest` estendido
  (objetivo Reassess/Priority, entrada confirmada vs. prevista, salário recorrente não
  conta como "extra"), `NotificationContentBuilderTest` (fallback determinístico sem IA,
  uma chamada só para vários alertas, nunca chamada sem evento) e
  `NotificationDedupeStoreTest` (dedup por dia, prune) — todos com fakes, sem Room/Context
  real, mesmo padrão de `AiRouterTest`/`ImportAnalyzerTest`.
- Build (`assembleDebug`) e testes (`testDebugUnitTest`, **96 testes**) verdes neste
  ambiente. **Não executado em aparelho/emulador real nesta sessão** (sem `adb`/emulador
  disponível) — ver "AÇÃO MANUAL NECESSÁRIA" no relatório da sessão para o roteiro de
  teste manual do Worker, da permissão de notificação e do dedup ao vivo.

Correções pós-Fase 5 (bug fix nos fluxos de lançamento manual e importação):
- **Causa raiz identificada: a Agenda nunca leu `TransacaoEntity`.** `agendaDataFor`
  (`FinaiApp.kt`) só filtrava `ContaEntity` (contas/vencimentos); um gasto manual ou um
  item importado grava só `TransacaoEntity` (arquitetura correta desde a Fase 1/4 —
  planning.md §8, `Transacao` ≠ `Conta`), então nunca aparecia na Agenda. Isso não era
  visível ao ler `FinanceViewModel`/`FinanceRepository` isoladamente — a persistência em
  si sempre funcionou (confirmado ao vivo em emulador: o dado sobrevivia a
  fechar/reabrir o app e já entrava nos cálculos de orçamento/saldo seguro); só a tela
  Agenda ficava cega para esse dado. O sintoma "nada acontece" ao lançar um gasto era
  essa lacuna de exibição, não perda de dado. Confirmado que o próprio app já direcionava
  o usuário para lá: o botão "Ver na Agenda" da tela de importação, e o texto "já
  entraram nos seus números" do card de sucesso, ambos pressupunham que a Agenda
  mostraria o que acabou de ser importado — o que planning.md §3.6 também prevê
  ("lançamentos prontos para revisar antes de entrar na Agenda").
- **Correção:** `FinanceUiState` ganhou `rawTransacoes` (lista completa, sem filtro de
  mês — `rawTransacoesDoMes` já existia mas só cobria o mês corrente do relógio, não o
  mês que a Agenda estiver navegando). `agendaDataFor` passou a filtrar também as
  transações pelo mês/ano selecionado e `AgendaScreen` ganhou uma seção "Lançamentos
  deste mês" que lista cada uma (categoria, dia, selo "importado" quando aplicável) com
  exclusão, espelhando a ação que já existia na tela Limites. Contas (`a_pagar`/
  `a_receber`) continuam sendo o único componente dos totais "Contas a pagar"/
  "Recebimentos" do topo — uma transação já é dinheiro gasto, não um compromisso futuro,
  então somá-la ali distorceria o significado do total.
- **Validado ao vivo num emulador** (Pixel/API 34, já rodava com `adb` disponível nesta
  sessão — diferente das anteriores): lançar um gasto manual aparece imediatamente na
  Agenda sem reiniciar o app; `force-stop` + reabrir o app confirma que o dado persiste
  no Room; importar `samples/fatura-exemplo.csv` e confirmar mostra os 6 lançamentos na
  Agenda do mês de cada data (março de 2026 nesse arquivo de exemplo), com o selo
  "importado".
- **Corrigido também `gradlew.bat`**, que estava com `set CLASSPATH=` vazio em vez de
  `set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar` — o wrapper não rodava
  por linha de comando neste ambiente (`Error: -classpath requires class path
  specification`) com o JDK 20 instalado aqui, o que teria impedido compilar/testar via
  `./gradlew` nesta sessão.
- Build (`assembleDebug`) e testes (`testDebugUnitTest`, **96 testes**, sem alteração de
  quantidade) verdes.

Refatoração do lançamento manual (12/09/2026):
- Referências lidas integralmente: os 13 slides de `project/ref/Finai Mobile App Design.pdf`,
  `lancamento.png` e `categoria.png`. O antigo `AddTransactionDialog` foi substituído
  por `ui/components/TransactionEntryScreen.kt`, camada Compose dedicada em tela cheia.
  Header e teclado próprio de 240 dp são fixos; só o formulário rola. Em paisagem,
  teclado e formulário ficam lado a lado. Rascunho usa `rememberSaveable` e é
  preservado ao voltar, inclusive após recriação da Activity.
- Inter 400–800 está empacotada em `res/font`, usada somente no novo fluxo;
  licença OFL em `assets/licenses/Inter-OFL.txt`. Ícones vetoriais próprios usam
  grid 24, traço 1,8 e tamanho 21 dp. Categorias têm a grade fixa de nove opções,
  seleção verde, fechamento em um toque e cancelamento pelo fundo.
- `domain/TransactionEntry.kt` concentra tipos, categorias e entrada em centavos
  (string, máximo 9 dígitos, 4 → 3 → 00 = 4300; apagar = 430).
  Salvar exige apenas valor positivo e categoria; descrição vazia usa a categoria
  como nome na Agenda. Conta padrão vem das origens já usadas, com Carteira como
  fallback local; o seletor permite informar outra conta sem inventar bancos.
- **Migração Room 3→4 não destrutiva:** acrescenta `TransacaoEntity.tipo`,
  com `Gasto` como padrão para registros existentes. Receita soma ao saldo,
  transferência é neutra e nenhuma das duas entra no orçamento/coach como gasto.
  Não há cadastro de saldos bancários ou conta de destino no modelo/referência:
  transferência registra tipo, valor, categoria e origem, sem inventar débito/crédito
  em contas bancárias inexistentes. Repositório e ViewModels existentes são mantidos.
- Recorrência projeta uma ocorrência por mês a partir da data inicial, ajustando
  dia 29–31 para o último dia quando necessário, sem criar cópias no banco.
  A Agenda e os números do mês usam essas ocorrências; excluir uma recorrência
  exclui o registro da série. Sucesso aparece somente após Room concluir;
  falha mantém o formulário e permite tentar novamente. “Ver lançamento” limpa
  o rascunho e abre a Agenda no mês/ano da data salva.
- Validação: `assembleDebug`, 100 testes JVM, 5 testes instrumentados dedicados
  (`TransactionEntryFlowTest`: três tipos/Room reaberto/Agenda, rascunho e migração).
  Fluxos exercitados no emulador API 34 em 412×892, 320×640 e 640×360 dp.
  Nenhuma chave de IA ou rede é necessária no fluxo. Não testado em aparelho físico.
- Diferenças da plataforma: sombras são nativas; o teclado usa gradiente claro
  sobre fundo fixo. O desfoque Compose do formulário atrás do sheet funciona em
  Android 12+, com scrim sem blur nas APIs 26–30. Calendário é Material 3.
  Menu de ações e rodapé global ficaram fora desta refatoração do lançamento.

Fase 6 — o que mudou (endurecimento e lançamento):
- **Build de release de verdade** (`app/build.gradle.kts`): `isMinifyEnabled`/
  `isShrinkResources` ligados, `buildConfig = true` (é o que apaga log em release),
  `versionName` 1.0.0, idiomas limitados a pt/pt-rBR/en, `lint { abortOnError = true }`
  para release, e assinatura lida de `keystore.properties` (gitignored, modelo em
  `keystore.properties.example`). **Sem esse arquivo o `assembleRelease` continua
  passando e gera APK não assinado** — de propósito, para o R8 poder ser validado por
  quem não tem o keystore. `app/proguard-rules.pro` foi escrito do zero, com o motivo
  de cada regra; a mais importante é o keep de `ListenableWorker`: o WorkManager
  instancia `FinanceCheckWorker` por reflexão a partir do nome gravado no banco dele, e
  sem a regra a rotina da Fase 5 pararia de rodar depois de um update **sem crash e sem
  log** — o pior tipo de falha. Conferido no `mapping.txt` que a classe sobrevive com o
  nome original enquanto `domain/`/`ui/` são ofuscados normalmente.
- **Chave do Gemini saiu da query string** (`GeminiAiProvider`): agora vai no header
  `x-goog-api-key`. Motivo concreto: `HttpURLConnection` repete a URL chamada na
  mensagem de várias exceções de IO, então `?key=AIza...` acabava no logcat e em
  qualquer bug report do aparelho.
- **`util/FinaiLog.kt` (novo) é o único lugar do app que chama `android.util.Log`.**
  `d`/`i` somem em release (guardados por `BuildConfig.DEBUG`, que o R8 resolve como
  constante); `w`/`e` ficam, mas sem stack trace e sem mensagem de exceção — só a classe
  — porque stack trace de rede carrega URL e mensagem de IO carrega caminho de arquivo.
  Corpo de resposta de provedor só é logado em debug, e passa por `redactKeys`.
  Verificado ao vivo no release: o log virou `W/GeminiAiProvider: Gemini respondeu HTTP
  400`, sem corpo, onde antes ia a resposta inteira.
- **Room deixou de apagar os dados do usuário num update.** `fallbackToDestructiveMigration()`
  saiu; entraram `MIGRATION_1_2` (`dividas.valorOriginalCentavos`) e `MIGRATION_2_3`
  (tabela `notificacoes_enviadas`), que nunca tinham sido escritas — só `MIGRATION_3_4`
  existia, e o fallback mascarava o buraco. `exportSchema = true` + `app/schemas/`
  versionado. Sobrou `fallbackToDestructiveMigrationOnDowngrade()`, que só dispara se o
  usuário instalar um APK mais antigo por cima. Coberto por
  `androidTest/.../FinaiDatabaseMigrationTest.kt`, que cria um banco v1 com dados e abre
  pelo Room de verdade (roda 1→2→3→4 e valida o schema). Não usa `MigrationTestHelper`
  porque ele exige o JSON das versões antigas, que não existe — `exportSchema` só foi
  ligado agora.
- **Nenhuma escrita de banco pode mais derrubar o app.** `FinanceViewModel` ganhou
  `launchSafely` (toda escrita passa por ele) mais `persistenceError`, e o `combine`
  que monta o estado ganhou `.catch`; `ImportViewModel` protege importar e confirmar;
  `AppViewModel` protege o chat com `finally` (sem isso, uma falha deixava o indicador
  de "pensando" girando para sempre); `ProviderUsageStore` trata `IOException` do
  DataStore; `AiRouter.generate` passou a nunca lançar. Antes, tudo isso eram
  `viewModelScope.launch { }` sem `try` — exceção de SQLite era crash.
- **Permissões revisadas.** As do app continuam duas (`INTERNET`, `POST_NOTIFICATIONS`).
  Descoberto e documentado que o manifest merger acrescenta mais quatro vindas do
  WorkManager (`ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
  `FOREGROUND_SERVICE`); as três primeiras são requisito real da rotina proativa
  (removê-las a derruba com SecurityException) e `FOREGROUND_SERVICE` ficou com o
  trade-off registrado em RELEASE.md — o app nunca cria trabalho expedido/foreground.
- **`network_security_config.xml` (novo)** com `cleartextTrafficPermitted="false"`.
  Sem certificate pinning de propósito: um pin desatualizado derrubaria a camada de IA
  inteira sem o usuário poder fazer nada, num app que não guarda segredo de servidor.
- **`android:allowBackup="false"`** — decisão de privacidade, registrada em planning.md
  §11 aguardando confirmação. Com backup ligado, o `finai.db` inteiro ia para a conta
  Google. Custo assumido e explícito: trocar de aparelho perde os dados, porque não há
  export manual. `backup_rules.xml`/`data_extraction_rules.xml` foram completados
  (banco + DataStore, além das chaves) para que religar seja um atributo só.
- **Dois defeitos de UI achados testando o release e corrigidos**: (a) `ChatOverlay` não
  tratava insets — o cabeçalho ficava sob a barra de status e **o botão de fechar não
  respondia ao toque**, porque caía embaixo do relógio do sistema; ganhou
  `statusBarsPadding`/`imePadding`/`navigationBarsPadding`. (b) `AiRouter` citava o
  motivo do *último* provedor da fila na mensagem de degradação, que é sempre o que o
  usuário não configurou — dizia "Nenhuma chave do Cerebras configurada" quando o
  problema real era a chave do Gemini estar inválida. Agora escolhe o motivo mais
  acionável (`reasonRank`), com dois testes novos.
- **Documentos de lançamento (novos)**: `PRIVACY.md` (auditoria do que sai e do que fica,
  cada afirmação apontando para o arquivo que a implementa), `RELEASE.md` (runbook de
  build/assinatura/publicação, com tudo que depende do usuário marcado **[VOCÊ]**),
  `play-store/ficha-loja.md`, `play-store/data-safety.md`, `play-store/politica-de-privacidade.md`,
  `keystore.properties.example`.
- **Validação**: `assembleDebug`, `assembleRelease` (R8 + shrink), 102 testes JVM e os
  instrumentados verdes. O APK de release **minificado** foi instalado e navegado no
  emulador (Pixel 6 API 34): todas as telas leem o Room normalmente sob R8,
  `EncryptedSharedPreferences` grava e lê a chave (conferido que o XML em disco está
  cifrado, chave e valor), a chamada de IA sai e degrada graciosamente, e o worker
  sobrevive à ofuscação. **Nada testado em aparelho físico** e **nenhuma chave real de
  provedor foi usada** (o teste de IA foi com chave inválida, exercitando só o caminho
  de erro) — ver "AÇÃO MANUAL NECESSÁRIA" no relatório da sessão e o checklist em
  RELEASE.md.

Remoção do dataset de demonstração + tour guiado + reset (12/09/2026):
- **Causa raiz do "app nunca começa vazio": `FinanceSeeder`.** Desde a Fase 1,
  `FinanceViewModel.init` chamava `FinanceSeeder.seedOnce(...)` na primeira execução
  (guardado por `FinaiPreferences.seeded`) e gravava direto no Room um dataset fixo
  (salário, aluguel, condomínio, fatura de cartão, três objetivos, três dívidas,
  cinco limites de categoria, dez lançamentos e três assinaturas — tudo com nomes/
  valores de exemplo tipo "Portugal — 10 dias" e "iPhone 15 Pro Max"). Era dado real
  do ponto de vista do Room (editável, computado pelos calculators de `domain/`), mas
  não era dado do usuário — a decisão da Fase 1 era deliberada ("padrão de
  fatura importada até a Fase 4 existir"), só nunca foi revertida depois que a
  importação de fatura (Fase 4) e a entrada manual (Fase 0/1) tornaram esse
  substituto desnecessário. Removido por completo: `FinanceSeeder.kt` apagado, a
  chamada em `FinanceViewModel.init` removida, e a flag `FinaiPreferences.seeded`
  (DataStore `finai_prefs`) removida — não há mais nenhum código de produção que
  escreva dado financeiro sem ação explícita do usuário. Uma instalação nova (ou um
  "Apagar todos os dados") deixa Início/Agenda/Objetivos/Dívidas/Limites vazios até o
  usuário lançar algo manualmente ou importar uma fatura.
- **Tour guiado na primeira abertura** (`ui/components/OnboardingTour.kt`, novo):
  overlay de tela cheia com 7 passos curtos (boas-vindas + Início, Agenda, Objetivos/
  Dívidas, Limites, Ação rápida/FAB, Assistente), indicador de progresso, botão
  "Pular" sempre visível e "Próximo"/"Concluir" no último passo. Estado persistido em
  `FinaiPreferences.onboardingComplete` (a flag já existia no DataStore desde a Fase 0,
  nunca tinha sido usada). `AppViewModel.onboardingComplete` expõe um `StateFlow<Boolean?>`
  — `null` só enquanto o DataStore ainda não emitiu o primeiro valor real (evita
  desenhar e esconder o tour no mesmo frame para quem já concluiu); `false` é o valor
  real de "ainda não concluído", que cobre tanto a instalação nova quanto o pós-reset.
  `FinaiApp.kt` desenha `OnboardingTour` por cima de tudo (mesma ideia do `Scrim` dos
  outros overlays, bloqueando toque no que está atrás) quando o valor é `false`.
  "Rever tour guiado" nas Configurações (`AiSettingsDialog.kt` → `DataPrivacySection`)
  chama `AppViewModel.restartOnboarding()`, que só regrava a flag — nenhum dado é
  tocado.
- **"Apagar todos os dados"** nas Configurações (mesma seção "Dados e privacidade"),
  com um `AlertDialog` de confirmação explicando que a ação é irreversível antes de
  executar. `FinanceRepository.apagarTodosOsDados()` (novo) limpa, numa única
  transação Room (`androidx.room.withTransaction`), as oito tabelas com dado do
  usuário — `transacoes`, `contas`, `objetivos`, `dividas`, `orcamento_categorias`,
  `assinaturas`, `mensagens_chat`, `notificacoes_enviadas` — via `deleteAll()` novos
  em cada DAO. `FinanceViewModel.apagarTodosOsDados()` chama isso mais
  `AiResponseCache.clear()` (cache de resposta de IA em memória — novo `clear()`) e
  `FinaiNotifier.cancelAll()` (descarta notificações já entregues sobre dados que não
  existem mais — novo `cancelAll()`), e só depois avisa `onDone`, que em `FinaiApp.kt`
  encadeia `AiViewModel.resetMemoizedState()` (novo — descarta leitura de IA
  memoizada por objetivo/dívida/decisões/coach, que fica indexada por id e podia
  mostrar texto de um item já apagado por um instante), `ImportViewModel.reset()`,
  `AppViewModel.restartOnboarding()` e navega de volta para a Início — ou seja, o
  reset também reabre o tour, como "voltar ao estado de instalação nova" pede.
  Deliberadamente fora do reset: chaves de provedor de IA (`AiKeyStore`,
  `EncryptedSharedPreferences`) e a contagem de cota diária (`uso_provedor_ia`/
  `ProviderUsageStore`) — são configuração técnica, não dado financeiro, e o pedido
  foi explícito em não apagá-las sem necessidade.
- A tela antes rotulada "Configurações da IA" (`FinaiDestination.AiSettings`) virou
  "Configurações" — já não é só sobre chaves de IA.
- **Teste novo**: `app/src/androidTest/.../data/repository/FinanceRepositoryResetTest.kt`
  — popula as oito tabelas via `FinanceRepository`/DAO, chama `apagarTodosOsDados()` e
  confere que todas ficam vazias. Não executado nesta sessão (sem `adb`/emulador
  disponível no ambiente) — ver "AÇÃO MANUAL NECESSÁRIA" no relatório da sessão.
  `NotificationDedupeStoreTest`'s `FakeDao` ganhou `deleteAll()` para continuar
  implementando `NotificacaoEnviadaDao` depois do método novo.
- Build (`assembleDebug`, `assembleRelease` com R8/shrink) e testes
  (`testDebugUnitTest`, **102 testes**, mesma contagem — nenhum teste de domínio foi
  afetado) verdes neste ambiente. Instrumentados (`connectedDebugAndroidTest`) não
  rodados nesta sessão.

Saldo do mês + parcelas de dívida na Agenda + nova tela de dívida (26/09/2026):
- **Saldo atual (Início) passou a ser só do mês corrente** — `BalanceCalculator.saldoDoMesCents`
  usa `transactionsInMonth` (recorrentes contam uma vez por mês); meses anteriores não se acumulam.
- **Parcelas de dívida projetadas até a última** (`domain/DebtSchedule.kt`). Antes a Agenda
  mostrava a dívida só no mês corrente, porque não havia vencimento. `DividaEntity` ganhou
  `parcelasTotais` e `proximoVencimento` (**Room 5→6**, `MIGRATION_5_6`, não destrutiva);
  cada parcela restante aparece no mês em que vence (dia 29–31 cai no último dia do mês),
  com "Parcela N de M", atrasada em vermelho. Nada é gravado por parcela: "Paguei a parcela"
  (`DebtSchedule.afterPayment`) decrementa o restante e avança o vencimento em um mês. Só a
  parcela mais antiga em aberto é tocável. Dívida antiga sem vencimento fica ancorada no mês
  corrente, "sem dia definido", até ser editada.
- **`ui/components/DebtEntryScreen.kt`** substitui o `AddDividaDialog` (removido): tela cheia
  no visual do lançamento (componentes `Entry*` de `TransactionEntryScreen.kt` viraram
  `internal`), Parcelada/Sem parcelas, teclado próprio para valores, total/já pagas com
  stepper, vencimento pelo calendário, juros opcional, saldo devedor estimado (sobrescrevível)
  e resumo "faltam N parcelas… até mês/ano". Regras em `domain/DebtEntry.kt`.
- Testes: `BalanceCalculatorTest`, `DebtScheduleTest` (JVM) verdes; migração coberta em
  `FinaiDatabaseMigrationTest` (não reexecutado). Conferido no emulador Pixel 6 API 34:
  cadastro 24x/5 pagas → parcela 6/24 em out/2026 … 24/24 em abr/2028, nada em mai/2028.
  O passo de dívidas do `InitialSetupWizard` continua sem vencimento (vira "sem dia definido").

Entrada extra, 7 dias sem itens de fatura, página da fatura (26/09/2026):
- **`TransacaoEntity.extra`** (**Room 6→7**, `MIGRATION_6_7`): receita marcada como entrada
  extra (13º, bônus, restituição). A "Linha do tempo do ano" (`domain/ExtraIncomeTimeline.kt`)
  junta conta a receber avulsa + receitas `extra` do ano — antes só lia `ContaEntity`, que não
  tem mais cadastro na UI, então ficava sempre vazia. No lançamento, Receita ganhou o switch
  "Entrada extra" (exclusivo com Recorrente); o "+" da linha do tempo já abre com ele ligado.
  Na Agenda, receitas têm "Marcar extra"/"Extra ✓" para corrigir as já lançadas.
- **"Próximos 7 dias" ignora itens de fatura** (`faturaId != null`): eles têm a data da
  compra, não do pagamento. Quem aparece é a conta da fatura, pelo vencimento.
- **`ui/screens/invoice/InvoiceScreen.kt`** substitui o `AlertDialog` de itens da fatura:
  cartão com total/vencimento/status e "Marcar fatura como paga", "Para onde foi" (categorias),
  "Onde você mais usou" (estabelecimentos agrupados), "Parcelamentos" (lê `(01/02)`,
  `PARC 3/10`, `parcela 2 de 6`), filtros Todos/Compras/Estornos/Parcelados e itens por dia.
  Números em `domain/InvoiceSummary.kt`, testados em `InvoiceAndTimelineTest`.
- Build `pessoal` (novo em `app/build.gradle.kts`): release minificado assinado com a chave
  de debug, só arm64 — `./gradlew assemblePessoal` gera um APK de ~16 MB que instala por
  cima do debug. Não usar `-Pandroid.injected.build.abi` (marca o APK como testOnly).
- Testes JVM verdes. **Não conferido em emulador/aparelho** nesta rodada (emulador foi
  encerrado por falta de memória); APK enviado ao usuário para teste no celular.

Saldo = Agenda, "Livre em" pelo contrato, nova logo (26/09/2026):
- **`domain/MonthCashFlow.kt`** é a única conta de "o que entra/sai no mês": totais da Agenda
  e "Saldo atual" da Início (= recebimentos − contas a pagar do mês). Substitui o
  `BalanceCalculator`, que ignorava parcelas de dívida e contava itens de fatura pela data da
  compra. "Paguei a parcela" agora grava um Gasto (`DebtSchedule.PAYMENT_CATEGORY`/`ORIGIN`,
  fora do coach) para o valor não "voltar" ao saldo quando a parcela sai da projeção.
- **"Livre em"** (`DebtCalculator`): dívida com contrato de parcelas termina na última parcela;
  a amortização só vale para dívidas sem contrato (reaplicar juros estendia a data).
- Logo: `res/drawable-nodpi/finai_mark.png` (folha recortada da arte do usuário) no header;
  ícone adaptativo com a folha em `mipmap-*/ic_launcher_foreground.png` sobre fundo branco.
- 121 testes JVM verdes. Não conferido em aparelho.

Ciclo do salário e recorrência de fim de mês (26–27/09/2026, feito no branch `experimento`,
já mergeado no `main`):
- **`domain/PayCycle.kt`**: "até o próximo salário", segunda camada ao lado do balanço do
  mês. Salário = receita com "salário"/"holerite" na descrição, avulsa ou recorrente
  (sem acento/caixa; "13º"/"férias"/extra ficam de fora); sem nenhuma, a maior receita
  recorrente. Pelo nome e não por flag porque o experimento não podia migrar o Room
  (voltar a um APK v7 com banco v8 apagaria os dados). Sem salário futuro lançado, o
  próximo é estimado em +1 mês. O usuário não quer informar saldo de banco.
  Ciclo = [último salário, véspera do próximo);
  entradas do ciclo − saídas (gastos, contas pelo vencimento, parcelas; vencido não pago
  do ciclo anterior entra como devido hoje). Sobra do ciclo anterior **não** é carregada.
  `shortfall` acha o primeiro dia em que o saldo corrido fica negativo.
- Início: novo `PayCycleCard` (livre, entradas/já saiu/a pagar, alerta de falta); o antigo
  "Saldo atual" virou "Balanço de <mês>". "Pode gastar hoje" usa
  `SafeToSpendCalculator.fromCycle` (livre − metas ÷ dias até o salário) quando há ciclo.
- **Recorrência de fim de mês** (`monthlyOccurrence` em `domain/TransactionEntry.kt`): série
  que começa no último dia do mês (30/09, 28/02) segue o último dia (31/10, 30/11); qualquer
  outro dia continua igual. Vale para lançamentos recorrentes, salário do ciclo e parcelas
  de dívida (projeção, "Paguei a parcela" e "Livre em"). **Limitação:** a dívida só guarda o
  próximo vencimento, não o dia contratado — uma dívida de dia fixo 29/30 que, ao pagar,
  cai num fim de mês (30/11, 28/02) passa a seguir o fim de mês. Corrigir exige um campo
  `diaVencimento` (migração Room).
- 137 testes JVM verdes. O cartão do ciclo foi visto funcionando no celular do usuário
  (APK `pessoal`); a regra de fim de mês (lançamentos e dívidas) ainda não foi confirmada
  por ele. Nada conferido em emulador.
- **Pendências combinadas com o usuário:** (a) campo `diaVencimento` em `DividaEntity` e
  marcação explícita "é salário" na receita — as duas pedem Room 7→8, marcar como salário
  na migração as receitas cujo nome já casa com `PayCycle.isSalary`; (b) "Pode gastar
  hoje" desconta o aporte mensal das metas (R$ 2.052 no caso dele) mesmo quando os cartões
  de meta dizem "sem capacidade de poupança" — o usuário não entendeu a divergência e
  pediu para voltar nisso depois; (c) Limites não aparece na barra de baixo (só pelo
  "Ajustar limites" do cartão preto) — o usuário não achava a tela. **(c) encerrada em
  28/09/2026 por decisão do usuário:** Limites continua só por esse caminho e pelos avisos.
  **(b) resolvida** em "Capacidade de poupança = balanço do mês" (seção da IA, 27/09).

Configurações viraram central de gerenciamento (27/09/2026):
- **`ui/components/SettingsScreen.kt`** (antes `AiSettingsDialog.kt`, as menções acima a esse
  arquivo e a `DataPrivacySection` são históricas) — a página principal só mostra *estado*:
  quatro linhas compactas (IA e assistente, Notificações, Contas e cartões, Privacidade e
  dados) com subtítulo de status e chevron, mais "Rever tour guiado" num grupo "Ajuda" à
  parte (saiu de "Dados e privacidade"). Formulário só aparece dentro da subpágina.
- Subpáginas ficam num `rememberSaveable` dentro da própria tela, não em rotas do NavHost
  (pilha rasa: principal → IA → provedor); por isso a tela desenha a própria
  `FinaiSettingsTopBar` e `FinaiApp` não desenha topbar nessa rota. `BackHandler` volta um nível.
- **IA e assistente**: "<provedor> está em uso" + explicação do fallback automático, lista
  numerada na ordem fixa do `AiRouter` com selo Em uso / Reserva / Sem cota hoje. "Em uso" =
  primeiro com chave e não esgotado hoje (`ProviderUsageStore.exhaustedTodayFlow`, novo).
  **Ordem não é configurável** — continua a de planning.md §7.3.
- **Página do provedor**: conectado não mostra campo — "Chave configurada ••••" + Trocar,
  "Testar conexão" e "Desconectar" (com confirmação). Sem chave: campo + "Conectar", que
  salva e já testa; link de onde tirar a chave; modelo gratuito usado (lido de `DEFAULT_MODEL`).
- **Testar conexão** (`AiViewModel.testProvider`, `AiRouter.adapterFor`): chamada mínima direto
  no adaptador, sem roteador/cache, sem dado financeiro no prompt; 429 marca esgotado do dia.
  Limite de 20s via `async` + `withTimeoutOrNull`, porque **o timeout do `HttpURLConnection`
  não cobre DNS** — no emulador sem DNS a chamada ficava presa indefinidamente
  (`InetAddress.getAllByName`). O mesmo vale para as chamadas normais do `AiRouter`, que não
  foram mexidas: numa rede sem DNS o "carregando" da IA pode durar muito.
- Notificações: permissão relida a cada ON_RESUME (`rememberOnResume`), linha que abre os
  ajustes do sistema, "Verificar agora" (o antigo "Testar agora").
- Nota: o `DEFAULT_MODEL` do Gemini hoje é `gemini-3.5-flash-lite` (a seção da Fase 3 cita outro).
- Build e 137 testes JVM verdes; telas conferidas no emulador Pixel 6 API 34 (sem rede real,
  então o teste de conexão só foi visto no caminho de falha).

Painel de avisos refeito + versionCode automático (27/09/2026):
- **`NotificationsPanel`** (`ui/components/NotificationsOverlay.kt`, substitui `NotificationsCard`):
  antes o cartão ficava a 12 dp do topo da tela, **sob a barra de status**. Agora abre logo
  abaixo da topbar (`statusBarsPadding` + `TopBarContentHeight`), com uma seta apontando o
  sino e crescendo a partir dele (`scaleIn` com `TransformOrigin` no centro do sino, medido
  por `FinaiTopBar.onBellCenterX`). Cabeçalho com resumo ("1 pede ação agora · 2 para
  acompanhar") e botão X, grupos por urgência (Agir agora / Acompanhar / Boas notícias),
  linhas com ícone por tipo e "Ver na Agenda"/"Ver em Limites"/"Ver objetivos". Tocar leva
  à tela. Voltar do sistema fecha.
- `FinanceAlert` ganhou `kind: AlertKind` (Bill, Budget, Subscription, Income, Goal), definido
  em `AlertCalculator`, para o painel não depender do formato do id.
- **Ícone do app não atualizava no celular**: os recursos estavam certos; o `versionCode` era
  fixo em 1, e launchers (o da Samsung em especial) guardam o ícone em cache por
  pacote + versionCode. Agora `versionCode` = número de commits (`git rev-list --count HEAD`,
  fallback 1). **Consequência:** um APK gerado de um commit mais antigo tem versionCode menor
  e o Android recusa instalar por cima; é preciso desinstalar, o que apaga os dados.
- Build e 137 testes JVM verdes. **Não conferido em emulador nem no celular** (o emulador foi
  encerrado por falta de memória).

Botão voltar do sistema fecha a camada de cima (27/09/2026):
- **Causa:** chat, menu do "+", simulador "Posso comprar?", tour e assistente inicial não
  tinham `BackHandler`. O voltar ia para o `NavController`, que trocava a tela *atrás* da
  sobreposição (Agenda → Início) com ela ainda aberta, e o voltar seguinte fechava o app.
  Reproduzido no emulador com o chat aberto na Agenda.
- **Correção:** um `BackHandler` central em `FinaiApp` fecha chat > simulador > menu do "+" >
  avisos, nessa ordem. Além disso, `navController.enableOnBackPressed(false)` fica ligado
  enquanto houver qualquer camada aberta (inclusive lançamento, fatura, dívida, tour e
  assistente, que têm tratador próprio). Assim a tela de trás nunca reage, seja qual for a
  ordem de registro dos tratadores. Tour e assistente inicial: voltar = passo anterior.
- **Limites e Importação** deixaram de ser tratados como abas em `navigateTo`: empilham sobre a
  tela atual, então voltar retorna para onde se estava (ex.: Agenda → aviso → Limites → Agenda).
  As quatro abas da barra continuam trocando entre si, e voltar numa aba leva à Início.
- Conferido no emulador Pixel 6 API 34 (chat, "+", simulador, avisos, Limites a partir de um aviso).

IA mais integrada: formatação, conversas, contexto (27/09/2026):
- **`domain/AiReplyFormat.kt`** (Kotlin puro, `AiReplyFormatTest`) lê qualquer resposta de IA:
  `**negrito**` vira ênfase, título/asterisco solto somem, R$ e % são marcados (negativo à parte),
  `{{rótulo|valor}}` vira cartão de destaque (só o chat pede). `ui/components/AiRichText.kt`
  desenha isso em todo texto de IA (chat, objetivos, decisões, coach, simulador, roteiro).
  Notificação usa `AiReplyFormat.plain`. `SYSTEM_BASE` pede no máximo dois valores e que a IA
  interprete em vez de repetir números da tela; "oi" não cita valores.
- **Chat em conversas** (**Room 7→8**, `MIGRATION_7_8`): `mensagens_chat.conversaId` (histórico
  antigo = conversa 0) e `contexto`. O prompt leva só a conversa atual, e o resumo leva a data de
  hoje. Antes, mensagens antigas com números velhos iam no prompt, e isso parecia número inventado.
  Chat tem "Nova conversa" e "Conversas anteriores" (reabrir, apagar). O ícone da topbar continua a
  conversa de hoje ou começa uma nova.
- **Botões contextuais** (`domain/AssistantTopic.kt`): "Conversar sobre isso" (coach), "Simular"
  (objetivo), "Ensaiar a ligação" (dívida: a IA faz o papel do atendente), "Perguntar" (simulador)
  e "Conversar" em cada decisão → `AppViewModel.askAbout`: conversa nova, pergunta já enviada, e o
  contexto invisível do card vai no prompt da conversa inteira.
- **`ObjetivoEntity.descricao`** (mesma migração): campo opcional no diálogo, que aparece no card.
  A leitura da IA vem em "Agora / Próximo passo / Risco" (`AiReplyFormat.labeled`, com fallback
  para o texto inteiro). "Decisões para você" vem em "ação | porquê" (`AiReplyFormat.decisions`).
- 147 testes JVM verdes; migração coberta em `FinaiDatabaseMigrationTest` (compilado, não executado).
  **Não conferido em emulador nem com chave real nesta rodada.** Os formatos pedidos no prompt
  dependem do modelo. As telas têm fallback se ele não seguir, mas vale conferir com o Gemini real.
- **Falso "faltam R$ 2.053" corrigido** (resolve a pendência (b) do ciclo do salário). A
  capacidade de poupança lia só `ContaEntity` recorrente (sem UI), ignorava salário e contas
  lançados como transação e saía negativa. Agora `SavingsCapacityCalculator` usa o balanço do mês
  de `MonthCashFlow` (o mesmo da Agenda e da Início), sem receitas `extra`.
  `GoalPlan.monthlyContributionFundedCents` é a parte do aporte que cabe. "Pode gastar hoje"
  reserva só essa parte, e `safeNote` separa "faltam R$ X para as contas" de "a meta não cabe".
  O resumo da IA diz quanto as metas pedem e quanto cabe, com a regra: meta que não cabe não é
  falta de dinheiro.
- **Metas julgadas pela sobra projetada até o prazo** (`domain/SavingsProjection.kt`): balanço de
  cada mês (`MonthCashFlow`), do mês atual até a meta mais distante (mínimo 12 meses). Parcelas de
  dívida pesam só enquanto existem, e lançamentos futuros e extras contam. **Salário avulso:**
  depois do último salário lançado, mês sem salário recebe o valor do último como estimativa (o
  resumo da IA avisa). `GoalCalculator.plan(..., projection)` define o status pela sobra acumulada
  até o prazo, descontadas as metas de maior prioridade. O aporte reservado no "Pode gastar hoje"
  continua limitado ao mês atual. A IA recebe a sobra do mês ("só o mês atual"), a projetada de
  12 meses e, por meta, a projetada até o prazo (`Goal.projectionNote`).

Metas concluídas e dívidas quitadas (27/09/2026):
- **Room 8→9** (`MIGRATION_8_9`): `objetivos.concluidoEm`, `dividas.quitadaEm`. As regras ficam em
  `domain/Completion.kt`: meta concluída = guardado ≥ alvo; dívida quitada = parcelas do contrato
  zeradas ou saldo zero. A transição grava a data e dispara a comemoração. Subir o alvo reabre a meta.
- **Não existe botão "Quitar"** (o usuário pediu para tirar). A dívida é quitada só quando a última
  parcela é paga ("Paguei a parcela", que agora pede confirmação).
- `ui/components/CelebrationOverlay.kt`: viagem (tipo "Viagem") = avião em arco com rastro
  pontilhado e nuvens; outras metas = troféu + confete; dívida = selo de feito + confete. A
  comemoração vem de `FinanceViewModel.celebration` e fecha com toque ou voltar.
- Telas: "Metas concluídas" (aceita aporte, mostra o que passou do alvo) e "Dívidas quitadas"
  (histórico, fora de total/estratégia/negociação/projeção).
- **Desfazer pagamento:** excluir na Agenda o gasto "X · parcela N de M" chama
  `DebtSchedule.undoPayment` (a parcela volta, o vencimento recua um mês e a dívida sai de quitadas).
  O elo é o nome da dívida, porque o gasto não guarda o id.
- Conferido no emulador Pixel 6 API 34: as três comemorações, as duas listas e o desfazer.
  Screenshots via `adb emu screenrecord screenshot` (o `screencap` saiu branco com a GPU swiftshader).

Fase 7 — Clareza e confiança (branch `v2-clareza`, aberta em 27/09/2026 a partir de
`747ee3e` no `main`): roteiro de 12 itens derivado de uma crítica de UX/produto, registrado
em `planning.md` §9 "Fase 7". Ordem: (1) veredito único de disponibilidade — falta prevista
comanda o "Pode gastar hoje"; (2) "Entenda este valor"; (3) Home por urgência; (4) contraste
e tipografia; depois renda principal explícita, onboarding mínimo e o resto. `main` continua sendo a versão em uso no celular.

- **Item 1 feito — veredito único de disponibilidade.** `PayCycle.floor` = menor saldo
  corrido de hoje até a véspera do salário; `SafeToSpendCalculator.fromCycle` parte dele,
  não de `livreCents` (sobra final). `PayCycle.shortfall` passou a olhar só de hoje em
  diante (buraco passado já coberto não conta) e ganhou `causa` (o compromisso que abriu o
  buraco). Com falta: "Pode gastar hoje" vira "VAI FALTAR ANTES DO SALÁRIO · Faltam R$ X",
  o cartão do ciclo troca "R$ X livres" por "Faltam R$ X", o aporte das metas não é
  reservado, o `safeNote` e o resumo da IA dizem quando/por quê, e o sino/rotina diária
  ganham o aviso `falta-ciclo` no topo. Sem ciclo (nenhum salário), o cálculo pelo mês
  (`calculate`) não enxerga falta intra-mês; nele a falta é a do mês inteiro (conta
  negativa antes das metas vira `shortfall` com a data de hoje, e o cartão diz "VAI FALTAR
  ESTE MÊS"). 5 testes novos em `PayCycleTest`.
- **Item 2 feito — "Entenda este valor".** `domain/SpendExplanation.kt` monta, dos números
  que `PayCycle`/`SafeToSpendCalculator` já calcularam, os passos da conta (já entrou, vai
  entrar, já saiu, vai sair, sobra do ciclo, ajuste do dia mais apertado, reserva das metas,
  livre ÷ dias), a lista do que ainda vai sair, o que o app supõe (não é saldo do banco, sem
  sobra do ciclo anterior, cartão pela fatura, salário estimado) e o que falta cadastrar
  (salário deduzido pela maior recorrente — `PayCycle.salarioPeloNome` —, próximo salário
  não lançado). Folha em `ui/components/SpendExplanationSheet.kt`, aberta pelo link de 48 dp
  "Entenda este valor ›" no cartão preto (`AppViewModel.explainOpen`, fecha com voltar).
  Os passos somam o valor final (testado em `SpendExplanationTest`). Conferido no emulador
  só no caminho sem salário; o do ciclo está coberto por teste JVM.
- **Achado no emulador para o item 5/6:** o assistente inicial grava a renda como "Renda
  mensal" (recorrente), que não casa com `PayCycle.isSalary`: o ciclo funciona pela "maior
  receita recorrente", mas a folha "Entenda este valor" pede para renomear. A marcação
  explícita de renda principal resolve.
- **Decisões do usuário (28/09/2026):** número principal da Início = livre até o salário;
  tour vira dicas por tela com destaque no componente; Limites não ganha acesso novo; ele
  **só testa no celular com a fase inteira pronta** — não mandar APK por item.
- **Itens 3 a 12 feitos (28/09/2026), numa sessão só:**
  - **3 · Início por urgência** (`domain/HomeSituation.kt`): um bloco de situação no topo
    (frase, "LIVRE ATÉ O SALÁRIO · dd/MM" = `safeToday.slackThisMonthCents`, "cerca de R$ Y
    por dia", "Entenda este valor", **uma** ação: Ver compromissos / Informar renda / Posso
    comprar?, e "Ajustar limites" secundário). `PayCycleCard`/`SafeToSpendCard` saíram.
    Depois: Próximos 7 dias, e "PLANEJAMENTO" (balanço, objetivos, decisões, linha do tempo,
    coach). O resumo da IA fala do mesmo número. Conferido em 412×892 e 320×640 dp.
  - **4 · Legibilidade**: Inter em todo o app (`ui/theme/Type.kt`, `EntryFont` virou alias),
    escala curta (34/20/16/15/14/13/12), nada útil < 12 sp. Cores medidas pela fórmula WCAG:
    `TextMuted` #6B6B73, `TextTertiary` #63636B, `EmeraldDark` #047857 (texto e botão com
    texto branco), `RoseDark` #BE123C; `Emerald` só como acento. Ações em `Common.kt`:
    `TextAction`, `ActionButton`, `SectionHeader`, `EmptyStateCard`, `SelectChip`,
    `OverflowMenu` — todas com 48 dp. Topbar em tela < 360 dp mostra só o ícone do Assistente.
  - **5 · Renda principal + dia da dívida** (**Room 9→10**, `MIGRATION_9_10`, constante
    `FINAI_DB_VERSION`): `transacoes.rendaPrincipal` (a migração marca o que casava com
    `isSalary`, ou a maior receita recorrente) e `dividas.diaVencimento` (dia do próximo
    vencimento; último dia do mês vira 31). `PayCycle.salariesOf`: marcada > nome > maior
    recorrente; `salarioPeloNome` virou `salarioDefinido`. `DebtSchedule.dueFor` usa o dia
    combinado (29/01 → 28/02 → 29/03). UI: switch "É minha renda principal" na Receita
    (exclusivo com Extra), "Marcar como renda principal" no ⋮ da Agenda. Editar agora parte
    do registro guardado (`originalOf`), não da ocorrência projetada do recorrente.
    Teste: `androidTest/.../Migration9To10Test.kt` (MigrationTestHelper a partir do 9.json).
  - **6 · Onboarding mínimo**: `InitialSetupWizard` reescrito — renda + próximo recebimento,
    contas desta semana (opcional), resultado real ("Até o próximo pagamento você tem R$ X").
    Regras em `domain/InitialSetup.kt` (a renda é gravada como recorrente a partir do último
    pagamento; `seriesStart` evita o "dia 30 vira 31"). O tour de 7 passos saiu
    (`OnboardingTour.kt` apagado): **dicas por tela** — `domain/TipScript.kt` (roteiro, Kotlin
    puro) + `ui/components/Tips.kt` (`Modifier.tipTarget`, recorte com `PathFillType.EvenOdd`,
    balão). `FinaiPreferences.tipsSeen` (quem concluiu o tour antigo conta como tendo visto
    tudo); "Rever tour guiado" e "Apagar todos os dados" limpam. Voltar fecha a dica.
  - **7/8/9**: Agenda, Limites, Dívidas viraram listas com divisor; ações frequentes são
    botões ("Marcar paga", "Paguei a parcela" — com confirmação também na Agenda, "Registrar
    aporte"); editar/excluir no ⋮. Roxo/índigo saiu (selo Prioridade, Coach, roteiro de
    negociação, chips da Agenda/Importação/Fatura); cartão de dívida é grafite, vermelho só
    para atraso. Vazios com ação ("Lançar conta", "Importar fatura", "Criar objetivo",
    "Cadastrar dívida"); Dívidas sem cadastro não mostra "R$ 0 em aberto".
  - **10 · "+"**: "Lançar gasto" em destaque; importar/simular/novo objetivo como "Mais ações".
  - **11 · Importação**: barra de confirmação fixa no rodapé; filtro "Precisa revisar"
    (`ImportItem.needsReview`).
  - **12 · Exportar/restaurar** (Configurações → Privacidade e dados): `data/backup/
    FinaiBackup.kt` (JSON via org.json; `testImplementation("org.json:json")` para o teste
    JVM), `FinanceRepository.exportar/restaurar` (transação, mesmos ids), SAF, sem chaves de
    IA. Registrado em PRIVACY.md.
- 188 testes JVM verdes (`Fase7ClarezaTest`, `BackupCodecTest` novos). Conferido no emulador
  Pixel 6 API 34: assistente, dicas (Início, Agenda, Objetivos, Dívidas, Limites, chat), a
  folha "Entenda este valor" batendo com o topo da Início **no caso com salário**, "+",
  lançamento com o switch novo, Configurações/backup (tela; exportação não executada até o
  fim), importação do CSV de exemplo com barra fixa e filtro.
- **Entrega final:** ver `FASE7-PROXIMOS-PASSOS.md` (o que falta: o teste do usuário no
  celular e o merge no `main`).

Plano financeiro central (28/09/2026, depois da Fase 7):
- **Crítica que motivou:** cada tela recomendava a própria sobra. Os R$ 114 do mês apareciam como
  aporte possível em três metas ao mesmo tempo, e a IA de "Decisões" e a leitura de cada meta
  podiam mandar o mesmo dinheiro para a dívida. Faltava uma decisão única.
- **`domain/FinancialPlan.kt`** (`FinancialPlan.build`) é a única conta de "quanto está livre e
  para onde vai". Quatro conceitos separados: `saldoDoMesCents` (balanço da Agenda),
  `comprometidoCents` (ainda sai até o salário), `reservadoFuturoCents` (sobra final que precisa
  ficar parada porque uma obrigação vence antes da próxima entrada, pela simulação dia a dia do
  `PayCycle.floor`) e `disponivelCents` (o menor saldo previsto até a véspera do salário). Sem
  renda cadastrada, a conta é do mês inteiro (menor entre a folga e o balanço da Agenda).
- **Destino único (`allocate`)**: falta prevista bloqueia tudo; depois dívida com juros ≥ 3% a.m.
  (`EXPENSIVE_DEBT_BASIS_POINTS`), da maior taxa para a menor, até o saldo em aberto; depois metas
  por prioridade, cada uma até o aporte mensal que pede. O teto é o menor entre o disponível e a
  sobra do mês (`SavingsCapacityCalculator`). O resto fica para o dia a dia. Cada real sai uma vez.
- Quem lê o plano: `FinanceViewModel` (Início, Objetivos, Dívidas, simulador, "Entenda este
  valor", sino) e `FinanceCheckWorker` (mesma conta na rotina diária). `GoalPlan.monthlyContributionFundedCents`
  agora é o que o plano destinou; `SafeToSpendResult.reservedForGoalsCents` virou
  `reservedForPlanCents` (inclui amortização). Meta sem aporte diz por quê (`goalNote`: "a sobra vai
  para "Rotativo"); a dívida que recebe mostra "Destino da sobra…" (`debtNote`). O cartão preto
  de Objetivos virou "Disponível para destinar" com o resumo da divisão. O simulador usa o livre do
  plano (e diz quando a compra tira dinheiro do destino).
- **IA só explica, e gasta menos:** "Decisões para você" deixou de chamar IA (`FinancialPlan.decisions`,
  determinístico; `AiTask.DECISIONS` e `ensureDecisions` removidos). A leitura das metas virou
  **uma** chamada para até 4 metas (`AiPromptBuilder.goalInsights`, resposta em seções `[n]` lidas
  por `AiReplyFormat.numberedSections`), em vez de uma por meta repetindo o bloco de todas. O chat e
  essa leitura recebem `FinancialPlan.aiBlock()` (curto) no lugar da divisão antiga; o
  `SYSTEM_BASE` diz que destino e prioridades já foram decididos.
- Testes: `FinancialPlanTest` (mesma sobra com um destino só, dívida barata deixa para as metas,
  obrigação antes da entrada segura dinheiro, falta bloqueia destino) e dois novos em `AiContextTest`.
  **Build Android não rodado nesta sessão**: o ambiente bloqueia dl.google.com, então o Gradle não
  baixa o AGP. A camada `domain/` foi compilada com kotlinc avulso (stubs de Room/Compose) e os 131
  testes JVM de `domain/` passaram; ViewModels e telas foram revisados à mão. Rodar
  `./gradlew assembleDebug testDebugUnitTest` antes de gerar APK.

Plano central: recomendação separada do livre + correção do dia do salário (28/09/2026):
- **Relato do usuário:** "Balanço de setembro +R$ 114", mas folga R$ 0 no chat e "sem folga" em
  todas as telas. Duas causas:
  1. **Bug em `PayCycle.floor`:** o saldo "antes de hoje" contava como candidato a menor saldo
     antes de somar as entradas do próprio dia. No dia do salário o ciclo começa hoje, esse
     saldo é zero, e o livre saía R$ 0 com o salário caindo no mesmo dia. Agora o floor usa
     saldo de **fim de dia** a partir de hoje (entrada e saída do mesmo dia se compensam).
     Teste `onPaydayTheSalaryThatLandsTodayCounts` falhava com o código antigo (conferido).
  2. **A regra da seção anterior descontava a destinação do livre** (a dívida cara levava os
     R$ 114 e o "Livre até o salário" ia a zero). **Decisão do usuário:** o plano só
     **recomenda**; recomendação nunca reduz o livre, que só muda quando o pagamento ou o aporte
     é registrado. Registrado também em planning.md §11.
- `FinancialPlan` agora tem três camadas: comprometido/reservado (`comprometidoCents`,
  `reservadoFuturoCents`, a reserva só existe quando um dia fica mais apertado que o fim do
  ciclo), livre (`livreCents`, menor saldo de fim de dia até a véspera do salário, com todas
  as entradas lançadas no intervalo, inclusive extras como rescisão) e recomendação
  (`recommendations`/`Recommendation`, antes `allocations`). `recommend` segue a mesma ordem
  (dívida ≥ 3% a.m., depois metas por prioridade), sem teto pela sobra do mês, e cada real é
  recomendado uma vez só. `safe` é calculado com reserva zero.
- "Entenda este valor" auditável: saldo atual do ciclo, entradas previstas até o salário
  (lista com data), compromissos (lista com data), sobra no fim do ciclo, reserva necessária
  (com o dia apertado) e livre; a recomendação aparece à parte, dizendo que não desconta.
- Textos: metas dizem "Recomendação…: guardar R$ X" ou "O plano recomenda usar o livre primeiro
  em "Y""; dívida diz "Recomendação…: usar R$ X do livre para amortizar"; decisões dizem que o
  valor continua livre até registrar; o simulador compara a compra com o livre e avisa quando
  sobra menos para a recomendação; o bloco da IA separa livre ("folga"), recomendação e balanço
  do mês (outra janela de tempo).
- **Reserva ≠ dinheiro que ainda vai entrar** (29/09, relato com print): com R$ 5.000 hoje e o
  ciclo só ganhando depois (salário, rescisão), a folha mostrava "Reserva necessária − R$ 631" e
  "em 28/09 o saldo cai", sem nenhuma saída no dia. A diferença entre sobra final e livre era
  chamada toda de reserva. Agora: reserva = saldo de hoje − menor saldo previsto
  (`PayCycle.saldoHojeCents`, `reservadoFuturoCents`), só quando uma saída vence antes da próxima
  entrada; sobra final − livre é `aindaVaiEntrarCents` ("vira livre quando cair"). A folha tem a
  conta do livre (saldo de hoje, reserva para compromissos, livre) e, à parte, "O ciclo inteiro"
  (hoje + entradas − saídas depois de hoje = sobra final) com a nota do que ainda vai entrar.
- **Pendente, pedido do usuário (29/09):** registrar um aporte em meta deve tirar o valor do
  livre. Hoje `contribuirParaObjetivo` só soma no guardado, sem lançar saída. Ideia: gravar um
  gasto com origem própria (como `DebtSchedule.PAYMENT_ORIGIN`), fora do coach, com "Desfazer
  aporte" na Agenda. Metas sem aporte registrado já não reduzem o livre.
- 135 testes JVM de `domain/` verdes (kotlinc avulso, mesmo motivo da seção anterior: sem acesso
  a dl.google.com). ViewModels e telas revisados à mão; rodar `./gradlew assembleDebug
  testDebugUnitTest` antes de gerar APK.

Tema escuro (29/09/2026, branch `claude/dark-theme-settings-vy84ft`):
- **Escolha só em Configurações → Aparência → Tema** (Claro / Escuro / Igual ao sistema). O
  usuário pediu explicitamente **nenhum botão de tema no cabeçalho ou em outro lugar**. Padrão
  sem escolha gravada = Claro (o visual de antes). Gravado em `FinaiPreferences.themeMode`
  (`ThemeMode`), fora do "Apagar todos os dados".
- **`FinaiColors` virou getters**: cada token tem valor claro e escuro, escolhido por
  `FinaiColors.isDark` (estado do Compose, escrito por `FinaiTheme`). As centenas de
  `FinaiColors.X` não mudaram; quem lê é redesenhado na troca. Por isso **não guardar token em
  `val` fora da composição** (top-level, enum, `remember`): fica congelado no tema da hora.
  Cor fixa do tema claro guardada em enum/modelo (`FinaiModels`, `UiMappers`, ícones da Agenda)
  é adaptada na hora de desenhar com `Color.themedText`/`Color.themedFill` (`ui/theme/Color.kt`).
- Tokens novos: `EmeraldButton`/`RoseButton` (fundo de botão com texto branco, iguais nos dois
  temas: `EmeraldDark`/`RoseDark` viraram cor de *texto* e clareiam no escuro), `InkStrong`/
  `OnInkStrong` (selecionado e botão principal: preto no claro, quase branco no escuro),
  `TextStrong`, `TextDisabled`, `EmeraldSoftStroke`, `RoseSoftStroke`.
- `MainActivity` lê o tema antes do primeiro quadro (sem piscar branco), ajusta ícones das barras
  do sistema e o fundo da janela. `Theme.kt` tem `darkColorScheme` com os containers do M3
  preenchidos (diálogo, menu, calendário) para não saírem arroxeados.
- **Não compilado com o Gradle** (sem acesso a dl.google.com). `ui/theme/` e `FinaiModels.kt`
  passaram pela análise de tipos do kotlinc 1.9.22 contra Compose Desktop 1.6.0; o resto foi
  revisado à mão. Rodar `./gradlew assembleDebug testDebugUnitTest` e olhar as telas no escuro
  antes de gerar APK.

Simulação de cenários de compra no chat (29/09/2026, branch `ccr-180beb76-nngee1`):
- **Motivo:** perguntas como "posso comprar uma cadeira de R$ 1.000 em 4x?" iam para a IA só com o
  resumo em texto, e ela improvisava contas e recomendações.
- **`domain/scenario/`** (Kotlin puro): `PurchaseIntentParser` extrai a intenção sem IA (valor,
  "4x", "4x de R$ 250", "à vista sai 900", "10% de desconto à vista", Pix/débito/cartão/boleto,
  "dia 10", "10/11", "hoje", "depois do salário"; `answer` completa uma pergunta pendente e
  `variation` refaz a última compra com "e em 6x?"). `PurchaseIntent.missing()` diz o que falta:
  parcelado ou cartão sem data da 1ª parcela/fatura → pergunta (`ScenarioQuestions`, sem IA, cita
  faturas em aberto); sem parcelas nem forma → pergunta; "à vista" sem forma = Pix/débito hoje, dito
  na resposta. `PurchaseScenario` tem as saídas com data; `asTransactions()` (id negativo, origem
  `simulacao`) só existe na memória da simulação e `toRecords()` é o que "Registrar compra" grava.
- **`PeriodProjection`** leva o fluxo central adiante: usa o próprio `PayCycle.of` em cada ciclo
  futuro com "hoje" no dia do salário, sobre um retrato rolado em memória (conta vencida antes vira
  paga, parcela de dívida vencida sai via `DebtSchedule.afterPayment`, salário avulso não lançado é
  copiado como estimativa). Sem renda principal, os períodos são meses (`MonthCashFlow`). Até 13
  períodos. Sobra não passa de um período para o outro (regra do `PayCycle`), e a resposta diz isso.
- **`ScenarioSimulator`**: período atual pelo `FinancialPlan.build` (mesmo "Livre até o salário" da
  Início, e a recomendação antes/depois); futuros pelo piso de cada ciclo. Por período: livre
  antes → depois, dia da falta, parcelas de dívida já previstas, fatura em aberto que vence até 3
  dias da parcela. Veredito: cabe / apertado (< 10% das entradas, mínimo R$ 100) / não cabe /
  já faltava. Opções comparadas quando fazem sentido: à vista hoje e esperar o próximo salário
  (preço à vista quando informado). `factsBlock()` é o que a IA recebe; `reply()` é a resposta pronta.
- **Chat** (`AppViewModel.send`): `PurchaseAssistant.handle` roda antes da IA. Pergunta de volta sem
  IA; simulado → `AiPromptBuilder.purchaseScenario` (`AiTask.PURCHASE_SCENARIO`) e a resposta passa
  por `ScenarioReplyGuard`: qualquer R$ ou dd/MM fora do `factsBlock` descarta a resposta e vale
  `reply()`. Sem IA, também `reply()`. O chat normal foi instruído a não calcular compra.
- **Registrar** é separado: cartão "Compra simulada · nada foi lançado" no chat com Descartar e
  Registrar compra (diálogo lista cada gasto) → `FinanceViewModel.registrarCompra` →
  `FinanceRepository.salvarTransacoes` (transação Room, um gasto "X · parcela N de M" por saída,
  categoria Compras). A compra pendente e a intenção pendente vivem só em memória e somem ao trocar
  de conversa. `FinaiApp` passa a cópia das listas do Room por `AppViewModel.setFinanceSnapshot`.
- Testes: `PurchaseIntentParserTest`, `ScenarioSimulatorTest` (21 novos; 193 JVM de `domain/`
  verdes com kotlinc avulso). **Build Android não rodado** (sem acesso a dl.google.com); ViewModels,
  `ChatOverlay` e `FinaiApp` revisados à mão. Rodar `./gradlew assembleDebug testDebugUnitTest`.
- Limitações: parcelas no cartão viram gasto no vencimento informado, sem entrar na fatura; se a
  fatura for importada depois, o `DuplicateDetector` pode não reconhecer (data diferente). A IA pode
  ter a resposta descartada por escrever um valor de outro jeito ("R$ 1 mil").

Tela Objetivos mais enxuta + status real das metas (10/10/2026, branch `claude/loving-brahmagupta-u3sz58`):
- **Status novo na tela** (`GoalPace` em `domain/GoalCalculator.kt`): Não iniciado, No ritmo, Atenção,
  Atrasado, Concluído. `GoalStatus` (capacidade: a sobra projetada cobre o que falta?) continua igual e
  segue alimentando alertas e IA. **Inconsistência corrigida:** o card mostrava `GoalStatus` direto, então
  meta com R$ 0 guardados aparecia "No ritmo" só porque a sobra cobriria; e meta com prazo vencido podia
  dizer "R$ X/mês mantém a data" (`monthsUntil` trava em 1). Regra: alvo atingido = Concluído; prazo
  vencido = Atrasado; sem sobra prevista até o prazo = Atrasado; sobra parcial = Atenção; sobra cobre e
  nada guardado = Não iniciado (Atenção se o prazo é este mês ou o próximo); sobra cobre e já há valor =
  No ritmo. Sem data de criação nem histórico de aportes no Room, não há como medir "ritmo esperado até
  hoje", por isso nada guardado nunca vira No ritmo. `GoalBadge` acompanhou (selos novos, Atrasado em rosa).
- **Card da meta**: nome, selo, guardado/alvo, %, barra (cor pelo selo), "R$ X/mês até <prazo>", uma linha
  curta só quando acrescenta algo (`Goal.headline`) e "Sugestão do plano: guardar R$ X" só na meta que o
  plano recomenda (`Goal.recommended`). "Adicionar valor" e "Detalhes"; os detalhes (falta, sobra prevista,
  por que o plano não sugere aporte, descrição, "Simular cenários", "Ver análise da IA") abrem no próprio card.
- **"Leitura da IA" saiu do card fixo** e virou "Ver análise da IA", sob demanda: `FinaiApp` não chama mais
  `ensureGoalInsights` ao abrir a tela, só quando a primeira análise é aberta (continua uma chamada para até
  4 metas). Prompt pede linhas de até 15 palavras e proíbe "no ritmo" para meta não iniciada; "Risco: nenhum
  relevante" some da tela.
- **Card do topo**: "Livre até o salário · dd/MM", valor, `FinancialPlan.shortRecommendation` (só falta
  prevista ou dívida prioritária; aporte em meta aparece no card da meta, para não repetir) e "Entenda este
  valor", que abre a mesma folha da Início.
- Testes: `GoalPaceTest` (novo) e asserções em `FinancialPlanTest`; 164 testes JVM de `domain/` verdes com
  kotlinc avulso (sem os de importação). **Tela não compilada aqui** (sem acesso ao AGP); o usuário vai
  testar no Android Studio.

Objetivos como central de planejamento (10/10/2026, mesmo branch):
- **Reserva contínua** (tipo Reserva + "Sem valor-alvo" no diálogo): `valorAlvoCentavos = 0`,
  `ObjetivoEntity.isOpenReserve`, `GoalPace.Open`, selo "Reserva contínua". Sem barra, percentual,
  prazo ou "Concluído"; mostra saldo e o aporte mensal planejado opcional (`aporteMensalCentavos`,
  **Room 10→11**, `MIGRATION_10_11`, teste `Migration10To11Test`; o `11.json` é gerado no primeiro
  build e precisa ser commitado). **Bug corrigido:** alvo zero dava "falta zero" e a meta virava
  "Concluído" (era a "Reserva geral concluída com sucesso" da IA). Meta comum agora exige alvo > 0;
  `Goal.progress` não divide por zero. Saldo de meta/reserva nunca é tratado como dinheiro livre (o
  plano não lê `valorGuardado`; a IA recebe a regra explícita).
- **`domain/GoalPlanning.kt`**: `GoalsOverview` (necessário/mês = falta ÷ meses, só metas com alvo, em
  andamento e prazo à frente; vencidas à parte; reservas pelo aporte planejado; recomendação do plano
  até o salário; sobra média projetada de 12 meses, nula sem renda) e `insights` (regras
  determinísticas, cada uma só quando muda uma decisão): falta prevista, sem renda, metas pedem mais
  que a sobra, dívida cara antes das metas, pagamento antes da próxima entrada com livre menor que os
  aportes, adiar a meta de menor prioridade 6 meses (valor recalculado), metas com palavra comum no nome
  (contagem dupla; some se a descrição diz "fora"/"separado"), prazo vencido, parcela de dívida
  terminando em até 6 meses. Topo mostra até 3; os de uma meta aparecem nos detalhes dela. Cada ponto
  tem ação real (`InsightAction`: editar prazo, comparar cenários no chat, Dívidas, Agenda, lançar renda).
- **IA**: a leitura fixa por meta saiu (`goalInsights`/`ensureGoalInsights`/`GOAL_INSIGHT_LABELS`
  removidos). "Analisar com IA" (planejamento inteiro ou uma meta) manda `GoalPlanning.factsBlock`
  (confirmado × projeção × dados que o app não tem, cenários +6/+12 meses já calculados, pontos que o
  usuário já vê) para `AiPromptBuilder.goalsPlanAnalysis`/`goalAnalysis` (`AiTask.GOALS_PLAN`/
  `GOAL_INSIGHT`). Resposta: até 3 linhas "ação | porquê", "Pergunta:" quando falta dado, ou `NADA`.
  Passa por `ScenarioReplyGuard`: valor ou data fora dos fatos descarta a resposta. Memoizado pelos fatos
  (`AiViewModel.analyzeGoals`, chave `PLAN_ANALYSIS_KEY` ou id da meta). Nenhuma chamada sem toque.
- **Tela**: card escuro com "Aportes mensais necessários", nomes, sobra prevista com barra de
  cobertura e "Faltam/Cabe", e o livre até o salário com "Entenda ›" (o `shortRecommendation` saiu:
  a dívida prioritária virou ponto de atenção). Card de meta recolhido: nome + selo, guardado / alvo +
  %, barra, "Meta: <prazo>" + "R$ X/mês", "Adicionar valor" e "Detalhes". Editar/Excluir foram para os
  detalhes. "Nada guardado ainda" saiu (o R$ 0 já diz).
- 175 testes JVM de `domain/` verdes (kotlinc avulso; `GoalPlanningTest` novo). **UI e Room não
  compilados aqui**: rodar `./gradlew assembleDebug testDebugUnitTest` no Android Studio.

Ciclo antes do primeiro salário (10/10/2026, mesmo branch):
- **Relato com print:** "Faltam R$ 1.500" até o salário com R$ 5,2 mil recebidos no mês (rescisão +
  VT). Causa: sem salário antes de hoje, `PayCycle.of` começava o ciclo **hoje**; tudo que entrou antes
  ficava fora, o saldo partia de zero e as faturas (já pagas, vencimento futuro) e o Picpay viravam falta.
- **Correção:** sem salário anterior, o ciclo começa no **dia 1º do mês** (`PayCycle.contaDesde`). O que
  entrou e saiu desde então forma o saldo de hoje. A regra "sobra do ciclo anterior não entra" continua
  valendo quando há salário anterior. A folha "Entenda este valor" diz desde quando soma.
- Teste `beforeFirstSalaryMoneyReceivedThisMonthCounts` com os números do relato (livre R$ 3.192, sem
  falta); conferido que falha com a regra antiga (saldo 0). 176 testes JVM de `domain/` verdes.
- Limite conhecido: dinheiro recebido no mês anterior, antes do primeiro salário, continua fora.

Dispensar pontos de atenção (10/10/2026, mesmo branch):
- **Relato:** o aviso "X e Y podem ser o mesmo gasto" não tinha saída; o usuário já tinha decidido manter as
  duas metas separadas. Agora todo ponto aberto tem um botão de dispensa ao lado da ação.
- Sobreposição: "Manter separadas", permanente (`GoalInsight.permanentDismiss`, chave
  `sobreposicao-<menor id>-<maior id>`, não muda com aporte). A IA recebe "DECISÃO DO USUÁRIO: são metas
  separadas" nos fatos. Os outros pontos: "Dispensar", com a chave `id|título`; como o título leva os
  números, o aviso volta se a situação mudar.
- Gravado em `FinaiPreferences.dismissedInsights`, lido no `combine` do `FinanceViewModel`
  (`GoalPlanning.visible`). "Mostrar dispensados (N)" na seção desfaz tudo; "Apagar todos os dados" limpa.
- 178 testes JVM de `domain/` verdes. UI não compilada aqui.

Objetivos mais limpos (10/10/2026, branch `claude/exciting-meitner-5ptbtq`):
- **Card escuro do topo reordenado:** primeiro "Livre até o salário · dd/MM" (ou "Falta…") com
  "Entenda ›"; embaixo, no máximo **uma** recomendação: o ponto mais grave entre falta prevista,
  dívida antes das metas e pagamento antes da próxima entrada (`GoalPlanning.isPlanTip`), que abre
  porquê/ação/dispensa ao tocar. Depois uma linha "Metas pedem × Sobra prevista" com barra e
  "Faltam/Cabe". Nomes por meta, horizonte da média, salário estimado, metas vencidas e "Comparar
  cenários" ficam em "Ver planejamento". "Analisar com IA" saiu da seção de pontos e foi para o
  rodapé do card; a análise aparece num bloco próprio, com "Fechar".
- **Sem repetição:** o ponto que vira recomendação do card e o "conflito" (`GoalPlanning.CONFLICT_ID`,
  já dito pela linha da sobra) não aparecem em "Pontos de atenção". A seção some quando não sobra
  nenhum ponto (antes mostrava "Nada pede atenção agora").
- **Card da meta mais baixo:** nome + selo; "R$ X de R$ Y" e % na mesma linha; barra; "Até <prazo>"
  e "R$ Z/mês". Os dois botões de 48 dp viraram "+ Adicionar valor" e um chevron; tocar no card
  também abre os detalhes. Detalhes: porquê do selo (`headline`), números, `Goal.planShort`
  (`FinancialPlan.goalNoteShort`, uma linha, no lugar do `goalNote` longo, que continua indo para a
  IA/chat), "Simular aporte" (o antigo "Simular cenários", mesmo destino) e "Analisar com IA" /
  "Ocultar análise". A análise perdeu o rodapé "Feita só com os números…"; "NADA" vira "Nada novo
  além do que já está na tela." e "Atualizar" só aparece como "Tentar de novo" quando falhou (a
  análise é memorizada pelos números, então refazer com os mesmos dados não chamava a IA).
- **Status sem renda cadastrada corrigido:** a projeção só tinha saídas e a meta saía "Atrasado"
  por falta de dado. `GoalPace.of(..., capacityKnown)`: sem renda, nada guardado = Não iniciado
  (Atenção com prazo perto); com valor guardado = Atenção ("Sem renda cadastrada, não dá para
  confirmar o ritmo"), nunca No ritmo; prazo vencido continua Atrasado. A "sobra prevista até o
  prazo" não aparece nos detalhes sem renda.
- 164 testes JVM de `domain/` verdes (kotlinc avulso). `GoalsScreen.kt` passou pela análise de
  tipos do kotlinc 1.9.22 contra Compose Desktop 1.6.0 (sem o plugin do Compose; os únicos erros
  foram dos ícones da biblioteca estendida, não baixada, em código que não mudou). **Gradle Android
  não rodado** (sem acesso a dl.google.com): rodar `./gradlew assembleDebug testDebugUnitTest`.

Novo design de aporte, novo objetivo e edição (10/10/2026, referências em `uploads/design-metas`):
- **Registrar aporte** (`ui/components/GoalContributionSheet.kt`, ref. 1a): folha sobre a tela desfocada
  (`Modifier.blur` no conteúdo em `FinaiApp`, Android 12+), −/+, controle deslizante com halo e balão,
  atalhos e prévia ("vai para R$ X · cobre N dias"/"% da meta"). Confirmação na mesma folha: check com
  dois anéis estáticos, contagem de R$ 0 ao aporte e barra/percentual do antigo ao novo, 1 s, ease-out
  cubic (`EaseOutCubic`), começando depois do fade de entrada; "Desfazer" tira o valor
  (`FinanceViewModel.desfazerAporte`). A comemoração de meta concluída espera o "Concluir"
  (`releaseContributionCelebration`), para não cobrir a confirmação. `contribuirParaObjetivo` virou
  `registrarAporte` (centavos).
- **Novo objetivo** (`GoalCreateScreen.kt`, ref. 1b): tipo (Viagem/Compra/Reserva/Outro) e nome com
  sugestões → valor (R$ 500–50.000) e já guardado → prazo (1–60 meses, linear como a referência) com
  aporte mensal e selo Folgado/Apertado/Não cabe/Sem renda contra a sobra média de `GoalsOverview` →
  "Objetivo criado". Reserva tem o interruptor "Reserva contínua" no passo 2; aí o passo 3 vira
  "Quanto por mês?" (aporte planejado opcional). Descrição só na edição.
- **Editar objetivo** (`GoalEditScreen.kt`, ref. 1c): chips de todos os objetivos (ordem fixa por
  id), prévia, campos em cartões, "O que vai mudar" (`GoalFlow.changes`), "Salvar · N alterações",
  aviso "Alterações salvas" e fica na tela. Trocar de objetivo/voltar com alteração pendente pede
  confirmação.
- Regras em `domain/GoalFlow.kt` (`GoalFlowTest`): faixas dos controles, prazo, aporte necessário,
  encaixe na sobra, cobertura da reserva (gasto médio diário = média das saídas de `MonthCashFlow`
  no mês atual e nos dois anteriores ÷ 30) e textos. Peças visuais em `GoalFlowKit.kt` (`GoalUi`:
  no escuro, as cores das referências; no claro, o mesmo layout com as superfícies claras).
  `AddGoalDialog`/`ContributionDialog` removidos.
- 265 testes JVM verdes. Conferido no emulador Pixel 6 API 34 nos dois temas: as três telas, arraste
  com halo, confirmação, Desfazer, aviso de salvo e a contagem (gravação de tela). Não testado no celular.

Agenda sequencial (10/10/2026, branch `claude/youthful-volta-00b7pa`):
- **Seletor Mensal | Sequencial** no topo da Agenda (`AgendaModeTabs`, estado em
  `FinaiUiState.agendaSequential`; "Ver na Agenda" de outras telas volta para a mensal). A mensal
  não mudou.
- **`domain/CashFlowTimeline.kt`** monta a sequencial **sem conta própria**: o ciclo atual é o
  `plan.cycle` do `FinancialPlan`, os seguintes vêm de `PeriodProjection.projectCycles` (extraído
  do simulador de compra, que passou a usar a mesma função) e o anterior é `PayCycle.of` na véspera
  do último salário, só com o realizado. Daqui saem ordem (`PayCycle.ordered`, agora público),
  saldo depois de cada movimento, estado visto de hoje (`MovementStatus`) e a marca do menor saldo.
  `CycleEntry` ganhou `source` (lançamento, conta ou parcela) e `movedFrom`.
- **"Livre até o salário" conferido:** é o menor saldo de fim de dia de hoje até a véspera do
  salário, com todas as entradas e saídas do intervalo. A fórmula virou `PayCycle.availableCents`
  (o `FinancialPlan` lê dali). O saldo recomeça a cada salário; a sobra do ciclo aparece no rodapé.
- **Mudança de regra no `PayCycle`:** conta paga (ou recebida) antes do vencimento conta **hoje**
  (`movedFrom` = vencimento), porque o dinheiro já saiu; a mensal continua pelo vencimento.
  `PayCycle.of(..., asOf)` evita que ela reapareça num ciclo projetado. Um teste antigo
  (`beforeFirstSalaryMoneyReceivedThisMonthCounts`) mudou o saldo de hoje esperado; o livre não mudou.
- UI em `ui/screens/agenda/SequentialAgenda.kt`: resumo do ciclo (livre, saldo hoje, a receber,
  a pagar, sobra no fim), ciclos com cabeçalho, divisor de mês sem zerar saldo, marca "Hoje",
  salário destacado, selo "Menor saldo", rodapé com a sobra. Tocar na conta alterna paga; na
  parcela mais antiga em aberto pede confirmação e paga. Sem renda: aviso + "Informar renda" e a
  lista sem saldo. Ciclo anterior e ciclos além do próximo ficam atrás de "Ver…".
- **IA:** `CashFlowTimeline.aiBlock()` vai no resumo do chat (`FinanceUiState.aiCashFlowBlock`):
  quanto entrou, já saiu e vai sair do ciclo, % comprometido, saldo de hoje, os próximos
  movimentos com o saldo depois de cada um, menor saldo, livre, sobra e o próximo ciclo. O prompt
  do chat manda usar isso, não o balanço do mês. "Se eu gastar R$ 500 hoje" agora é simulado
  (Pix/débito hoje, dito na resposta) pelo `ScenarioSimulator`, em vez de perguntar a forma.
- Testes: `CashFlowTimelineTest` (13, cenário 30/10 → fatura 12/11 → 30/11 com despesas no meio,
  fatura paga adiantada, parcela paga, ciclo seguinte, itens de fatura/transferência/meta, ciclo
  anterior, sem renda, bloco da IA, "gastar hoje"). 245 testes JVM de `domain/` verdes com kotlinc
  avulso. `AgendaScreen.kt`/`SequentialAgenda.kt` passaram na análise de tipos contra Compose
  Desktop 1.6.0; ViewModels e `FinaiApp` revisados à mão. **Gradle Android não rodado** (sem
  acesso a dl.google.com): rodar `./gradlew assembleDebug testDebugUnitTest` e olhar a tela nos
  dois temas.

Parcelamentos: cartão compacto na Agenda + tela própria (10/10/2026, referência em `uploads/design-parcelas`):
- **`domain/InstallmentsSummary.kt`** (Kotlin puro, `InstallmentsSummaryTest`) só agrupa o que
  `CardPurchase`/`salvarCompraCartao` já gravam (parcela = `TransacaoEntity` com `compraId`, na fatura do
  mês). Mês da parcela = vencimento da fatura; paga = conta da fatura paga (mesma regra da Agenda).
  Nada gravado, e as parcelas continuam dentro da fatura na linha do tempo: o cartão só resume.
  Parcelas de fatura **importada** ("(01/03)", "PARC 3/10", sem `compraId`, lidas por `InvoiceSummary.installmentOf`)
  também entram: faturas de meses diferentes da mesma compra (nome, cartão, nº de parcelas, mês da 1ª, valor)
  viram uma compra; parcelas antes da primeira fatura importada contam como pagas e as depois da última são
  **estimadas** (mesmo valor, mês a mês, avisado na tela). Importar a fatura seguinte troca a estimada pela real.
- Agenda (mensal): o antigo `InstallmentsOverview` (removido) virou `InstallmentsAgendaCard`, abaixo dos
  totais: "R$ X neste mês · N compras" do mês navegado + miniatura dos 5 meses seguintes. Some sem parcela
  no mês e sem compra ativa.
- `ui/screens/installments/InstallmentsScreen.kt`: camada por cima de tudo (`showInstallments` em `FinaiApp`),
  entrando da direita, como a fatura. Foi rota do NavHost, mas a topbar do app sumia na troca e a Agenda pulava
  (glitch relatado). `FinaiSettingsTopBar` ganhou `subtitle`. Conteúdo: total em
  aberto, parcelas restantes, "livre a partir de", gráfico "Comprometido por mês" (em aberto, tocável; > 6
  meses rola de lado) e as compras ativas por valor em aberto (traço por parcela; > 24 parcelas vira barra).
- Build e testes JVM verdes; conferido no emulador Pixel 6 API 34 (tema escuro): cartão, tela, seleção de
  mês e voltar do sistema. Tema claro e telas estreitas não conferidos.

Barra de navegação de vidro (10/10/2026, branch `claude/nav-vidro-3a`; playbook em
`uploads/design-nav/finai-nav-playbook.pdf`):
- **`ui/components/FinaiBottomNav.kt` reescrito** no design 3a: pílula flutuante (12 dp das
  laterais, 16 dp + inset da navegação acima da base), 64 dp expandida / 52 dp recolhida com
  escala 0,94, quatro abas + "+" (56 × altura − 14). Todas as medidas, curvas e durações ficam
  em `FinaiNavBarSpec`. Indicador: `Animatable` com mola (amortecimento 0,6, rigidez 380) e um
  coletor só, para toques seguidos partirem da posição e velocidade atuais; esticar
  1,22 × 0,86 em 280 ms, voltando aos 240 ms. Altura, escala e posição do indicador são lidas
  só em layout/desenho (sem recompor por quadro). Abas com `Role.Tab` e o nome lido mesmo
  recolhida; "+" e abas sem ripple, escala 0,92 ao pressionar. Tocar na aba ativa só expande.
- **Vidro próprio, sem Haze** (`ui/components/GlassBackdrop.kt`): o Haze 0.7.x (o único
  compatível com o Compose 1.6) não tem saturação, só desfoca na API 32+ e subiria o Compose e
  o core-ktx do app. O `glassSource` grava as telas num `RenderNode`; a barra desenha esse nó
  num segundo nó com `RenderEffect` (blur 22 dp + saturação 180%). **Dois achados no
  emulador:** (1) o nó com efeito vira camada em cache no HWUI e não é refeito quando só o
  conteúdo muda, por isso a fonte regrava o efeito a cada quadro dela; (2) converter os 22 dp
  para o "sigma do CSS" dava raio de ~113 px, e o blur saía **vazio** (sem erro). Ficou o raio
  22 dp direto, como o Haze faria com o valor do playbook. API < 31: tinta #27272A a 92%, com
  borda e brilho; a sombra (y 12, blur 32, preto 45%, desenhada só por fora da pílula) só
  existe na API 28+. Campos `RenderNode` são `Any?` para nada carregado na API 26–30 depender
  do tipo.
- **Recolher ao rolar** (`ui/components/NavBarCollapse.kt`, `NavBarCollapseStateTest` JVM): um
  `NestedScrollConnection` em volta do `NavHost` cobre todas as telas. Conta só a rolagem
  consumida (overscroll não conta) e exige 6 dp seguidos na mesma direção, com a contagem
  zerada a cada troca, para não piscar. Trocar de tela volta expandida. Animações do sistema
  desligadas = nunca recolhe e tudo troca direto. Teclado aberto esconde a barra
  (`derivedStateOf` sobre o inset, sem recompor o app a cada quadro do teclado).
- **Decisões do usuário:** vidro escuro nos dois temas, com o degradê da base (130 dp,
  85% → 0) na cor de fundo do tema; as abas continuam restaurando a posição de rolagem (o
  playbook dizia "começa no topo"). A distância do topo é contada desde a troca de tela, então
  numa aba restaurada já rolada a barra só recolhe depois de mais 60 dp.
- `ScreenContentPadding` virou getter `@Composable`: 140 dp + inset da navegação. A barra de
  confirmação da importação, o aviso de erro de gravação e o menu do "+" passaram a somar o inset.
- 286 testes JVM verdes. Conferido no emulador Pixel 6 API 34 (tema escuro) em 360, 412 e
  480 dp: vidro, recolher/expandir, troca de aba quadro a quadro (overshoot contido na pílula)
  e animações desligadas. **Não conferido:** API < 31 (o AVD API 27 não subiu por falta de
  memória), tema claro, TalkBack, celular. `lintDebug` falha com 5 erros que já existiam no
  `main` (`PathParser` restrito, BOM, `AnimatedContent`), nenhum nos arquivos novos.

Cabeçalho 4a (10/10/2026, branch `claude/youthful-cori-f2x03u`; playbook em
`uploads/design-header/finai-header-playbook.pdf`):
- **`ui/components/FinaiPageHeader.kt` (novo)** substitui o `FinaiTopBar` (removido): símbolo
  oficial (`finai_mark`, 30 dp, raio 10) à esquerda, sino e engrenagem (traço 2 dp, #D4D4D8,
  desenho do Lucide) e o orbe do Assistente (radial #34D399 → #047857, brilho 18 dp a 35%,
  escala 0,92 com mola ao pressionar). 56 dp abaixo da status bar, 18/14 dp de lado, 6 dp
  visíveis entre botões (toque de 48 dp, visual de 40 dp, passo de 46 dp). Medidas em
  `PageHeaderSpec`; contas da transição em `PageHeaderMotion` (`PageHeaderMotionTest`, JVM).
- **Fica por cima do conteúdo**: o `FinaiApp` deixou de ser `Column(topbar, conteúdo)`; o
  conteúdo ocupa a tela e começa em status bar + 56 + 6 dp (`ScreenContentPadding`). O
  cabeçalho fica **fora** do `glassSource` (senão entraria no próprio desfoque). Rolado
  (> 8 dp): `drawGlassRect` (novo em `GlassBackdrop.kt`, mesmo `RenderNode` da barra de baixo,
  blur 20 dp + saturação 160%) sob #09090B 72% e linha branca 5%, ligando em 300 ms. API < 31:
  #09090B 96%. A superfície só se registra na fonte enquanto o vidro aparece. Toque no vidro
  não atravessa para o card de baixo.
- **Títulos**: `PageTitle` (32 sp, 800, −0,04 em, heading) é o primeiro item de cada
  `LazyColumn`; o compacto (16 sp, 700) mora no cabeçalho. Os dois leem
  `p = clamp((rolagem − 10)/30)` só em `graphicsLayer` (rolar não recompõe). Rolagem =
  offset do item 0; passou dele, p = 1. Animações desligadas: só opacidade. Tocar no compacto
  volta a lista ao topo. Centralizado na largura da tela; quando isso o jogaria sobre os
  botões (360 dp), desliza para o espaço livre, e só então corta com reticências.
- **Rolagem por aba**: as listas das quatro abas são criadas no `FinaiApp` (`tabLists`), para o
  cabeçalho ler a aba aberta no mesmo quadro da troca. Limites e Importação criam a própria a
  cada visita (`rememberSecondaryList`) e mostram a seta de voltar no lugar do símbolo. A
  barra de baixo continua com o `NestedScrollConnection` dela, sem mudança.
- **Ponto de notificação** (8 dp, #F43F5E, anel de 2 dp, sem número): avisos ainda não vistos.
  Abrir a central marca todos; aviso que some e volta conta como novo. Guardado só em
  `rememberSaveable` (sem persistência nova), então reabrir o processo mostra o ponto de novo.
- Títulos: `FinaiDestination.screenLabel` virou `headerTitle` (Início, Agenda, Objetivos,
  Dívidas, Limites do mês, Importar fatura, Configurações). A saudação da Início virou o
  subtítulo do título grande. `FinaiSettingsTopBar` (Configurações, Parcelamentos) usa o mesmo
  cabeçalho, com seta, sem ações e sempre no estado rolado (fundo sólido).
- Tema claro: o mesmo layout com o fundo do tema a 72%/96%, linha preta 6% e ícones `TextBody`.
- **Validação**: Gradle Android não roda aqui (dl.google.com bloqueado). `FinaiPageHeader.kt`,
  `FinaiTopBar.kt` e o teste foram compilados com kotlinc 1.9.22 + plugin do Compose 1.5.8
  contra Compose Desktop 1.6.0 (4 testes verdes) e o cabeçalho foi renderizado com
  `ImageComposeScene` (Desktop 1.5.12) em 360/412/480 dp, nos dois temas, em 0/9/25/60 dp de
  rolagem. Sem blur nessa renderização (stub), sem toque/TalkBack. `FinaiApp` e as telas foram
  revisados à mão. **Rodar `./gradlew assembleDebug testDebugUnitTest`** e conferir no aparelho:
  vidro real, API < 31, TalkBack, troca de aba rolada, mola do assistente.

**Decisão (26/09/2026): o app é para uso pessoal, não vai ser publicado na Play
Store.** Isso fecha a Fase 6: os itens que só existiam por exigência da loja
(keystore de assinatura de produção, `targetSdk` mínimo da Play, ficha/imagens/
formulário de segurança de dados, política de privacidade em URL pública —
`RELEASE.md` §2, §3, §6) **não serão feitos**. `RELEASE.md` e `play-store/`
ficam no repo só como referência caso essa decisão mude. O teste em aparelho
físico foi feito pelo usuário nesta data; o feedback detalhado dele ainda não
foi registrado aqui — próxima sessão deve perguntar e anotar.

**Próximo passo:** nenhuma pendência de lançamento. Para instalar em uso
pessoal, `./gradlew assembleDebug` (ou `assembleRelease` sem
`keystore.properties`, que gera APK não assinado) já basta — ver a nota em
`planning.md` §9 Fase 6. Se houver trabalho de sessão anterior ainda não
commitado no working tree, resolver isso primeiro (ver passo 2 de "Como
retomar uma sessão" abaixo).

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
- Endurecimento e lançamento (Fase 6): `RELEASE.md` (build de release, assinatura,
  checklist da Play — tudo que depende do usuário está marcado **[VOCÊ]**),
  `PRIVACY.md` (o que sai do aparelho vs. o que fica), `play-store/` (ficha, data
  safety, política de privacidade), `app/proguard-rules.pro`, `util/FinaiLog.kt`
  (único ponto de log do app), `app/schemas/` (schema do Room por versão) e
  `androidTest/.../FinaiDatabaseMigrationTest.kt` (migração 1→4 com dados).
- Protótipo original (referência visual/copy, não para copiar estrutura de código):
  `project/FinAI Mobile.dc.html`, `project/support.js`, `project/ref/*.png`.
- Transcript de design (intenção por trás de cada decisão de UX): `chats/chat1.md`.
- Design tokens já extraídos: `app/src/main/java/com/finai/app/ui/theme/`.
- Cálculo real (Fase 1): `app/src/main/java/com/finai/app/domain/` — inclui
  `BillStatusCalculator` (status de conta derivado da data), `AlertCalculator`
  (eventos financeiros — sino da topbar e notificações, planning.md §3.9) e
  `BehaviorCoach` (padrões de gasto do coach comportamental, planning.md §3.1).
- Simulação de compra no chat: `domain/scenario/` (intenção, projeção por ciclo, simulador,
  perguntas, trava contra número inventado), ligada em `state/AppViewModel.kt`.
- Importação de fatura (Fase 4): `domain/importer/` (parsing, classificação,
  duplicata, recorrência — Kotlin puro, testável na JVM), `data/importer/`
  (Uri/OCR/PDF/planilha), `data/ai/ImportAiAssistant.kt` (o único ponto de IA da
  importação), `state/ImportViewModel.kt`, `ui/screens/importer/ImportScreen.kt`.
  Faturas de exemplo para teste manual e instrumentado: `samples/`.
- Dado real (Room): `data/local/`, `data/repository/` (`FinanceRepository` para os
  números, `ChatRepository` para o histórico do chat). O que resta em
  `data/fixtures/FinaiFixtures.kt` é só config de UI sem dado pessoal.
- Camada de IA (Fase 2/3): `data/ai/` — `AiProvider` (interface),
  `GeminiAiProvider`/`OpenAiCompatibleAiProvider`+4 subclasses (adaptadores),
  `AiRouter` (fallback), `ProviderUsageStore` (cota diária),
  `AiResponseCache` (cache em memória); `data/prefs/AiKeyStore.kt` (chaves, uma
  por provedor); `domain/AiPromptBuilder.kt` (prompts); `state/AiViewModel.kt`
  (goal insight/veredito/negociação/decisões + gestão das chaves) e o chat em
  `state/AppViewModel.kt`. Testes do roteador:
  `app/src/test/java/.../data/ai/AiRouterTest.kt`.
- Notificações proativas e coach (Fase 5): `data/work/FinanceCheckWorker.kt` (a rotina
  periódica, orquestra as camadas abaixo, nenhuma regra de detecção mora nele),
  `data/notifications/` (`NotificationContentBuilder` — texto; `FinaiNotifier` — entrega,
  único lugar que usa `NotificationManagerCompat`; `NotificationDedupeStore` — "não
  notificar o mesmo evento duas vezes no dia"; `FinaiNotificationChannels`),
  `FinaiApplication.kt` (agenda o Worker e cria os canais na subida do processo).
  Detecção continua em `domain/` (`AlertCalculator`, `BehaviorCoach`). Testes:
  `app/src/test/java/.../domain/{AlertCalculatorTest,BehaviorCoachTest}.kt` e
  `.../data/notifications/{NotificationContentBuilderTest,NotificationDedupeStoreTest}.kt`.
