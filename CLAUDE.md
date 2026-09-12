# FinAI Android — guia para sessões do Claude Code

Leia isto primeiro em toda sessão nova. Depois leia `planning.md` inteiro (requisitos,
arquitetura, roadmap por fases, critérios de aceite) — ele é a fonte de verdade do
que construir e em que ordem. Este arquivo é sobre *como* trabalhar no repo e
*onde estamos agora*; não duplica o planning.

## Status atual

**Fase 0 (Fundamentos), Fase 1 (MVP sem IA), Fase 2 (camada de IA com um
provedor), Fase 3 (multi-provedor e resiliência) e Fase 4 (importação de
fatura) — concluídas.** Ver `planning.md` §9 para a lista de fases.

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

**Próximo passo:** Fase 5 (notificações proativas e coach comportamental) — ver
planning.md §9.

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
- Importação de fatura (Fase 4): `domain/importer/` (parsing, classificação,
  duplicata, recorrência — Kotlin puro, testável na JVM), `data/importer/`
  (Uri/OCR/PDF/planilha), `data/ai/ImportAiAssistant.kt` (o único ponto de IA da
  importação), `state/ImportViewModel.kt`, `ui/screens/importer/ImportScreen.kt`.
  Faturas de exemplo para teste manual e instrumentado: `samples/`.
- Dado real (Room): `data/local/`, `data/repository/`. O que resta de fixture
  (Fase 5 e config de UI sem dado pessoal): `data/fixtures/FinaiFixtures.kt`.
- Camada de IA (Fase 2/3): `data/ai/` — `AiProvider` (interface),
  `GeminiAiProvider`/`OpenAiCompatibleAiProvider`+4 subclasses (adaptadores),
  `AiRouter` (fallback), `ProviderUsageStore` (cota diária),
  `AiResponseCache` (cache em memória); `data/prefs/AiKeyStore.kt` (chaves, uma
  por provedor); `domain/AiPromptBuilder.kt` (prompts); `state/AiViewModel.kt`
  (goal insight/veredito/negociação/decisões + gestão das chaves) e o chat em
  `state/AppViewModel.kt`. Testes do roteador:
  `app/src/test/java/.../data/ai/AiRouterTest.kt`.
