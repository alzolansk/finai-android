# Handoff · FinAI v2, fases 5–11 + notificações com botões

Atualizado em 14/11/2026, branch `claude/awesome-hawking-pdynac`. Substitui o handoff antigo
(novo lançamento, 11/10), que continua no histórico do Git.

**Estado em uma frase:** o domínio das fases 5–10, as telas novas e o sistema de notificações
estão escritos, mas **nada disto foi compilado nem testado**, e as telas novas **ainda não estão
ligadas ao app** (`FinaiApp.kt` e a navegação não foram alterados). O APK pessoal não foi gerado.

## Por que parou sem compilar

O Gradle não terminou de baixar as dependências nesta sessão. `dl.google.com` foi liberado no
ambiente "trilha" e o SDK foi instalado em `/opt/android-sdk` (`local.properties` aponta para ele,
fora do Git). Depois disso o proxy passou a responder 429/404 para jars do Maven Central
(`repo.maven.apache.org`) por excesso de downloads paralelos. A última tentativa usava
`--max-workers=2` e mais tentativas por artefato e foi interrompida a pedido.

Para retomar o build:

```bash
sh ./gradlew compileDebugKotlin --max-workers=2 -Dorg.gradle.internal.repository.max.tentatives=8 -Dorg.gradle.internal.repository.initial.backoff=3000 --console=plain -q
```

(`./gradlew` não tem bit de execução neste checkout: chamar com `sh`.) Se voltar "Could not find
…jar", repetir com `--refresh-dependencies` depois de um minuto.

## O que está escrito (sem compilar)

### Banco: Room 17 → 18
- `data/local/entity/V2Entities.kt`, `data/local/dao/V2Daos.kt`: `memoria`, `regras_categoria`,
  `fechamentos`. `MIGRATION_17_18` em `FinaiDatabase.kt` (só `CREATE TABLE`), `FINAI_DB_VERSION = 18`.
- `FinanceRepository`: exporta, restaura e apaga as três tabelas; `lembrar`, `esquecer`,
  `aprenderCategoria`, `responderFechamento`. `FinaiBackup` codifica e lê os três blocos.
- `androidTest/.../Migration17To18Test.kt` escrito.
- **Falta:** o `18.json` (gerado pelo KSP no primeiro build; commitar) e conferir que o SQL da
  migração bate com ele.

### Domínio (Kotlin puro)
| Fase | Arquivo | O que faz |
| --- | --- | --- |
| 5 | `domain/quickentry/QuickEntryParser.kt` | Frase → `QuickDraft` (valor, data relativa, tipo, conta/cartão/meta conhecidos, parcelas, categoria por regra aprendida e `MerchantClassifier`). `looksLikeEntry` para o chat. |
| 6 | `domain/spending/SpendingAnalysis.kt` | Total do período, comparação, 6 meses, categorias com limite e ritmo, estabelecimentos, busca sem acento, `LimitSuggestion` (regra 10), `SubscriptionLines` ("cobrada há N meses"). |
| 7 | `domain/plans/SavingsCapacity.kt` | `SavingsCapacity` (regra 4), `GoalConflictResolver` (regra 5, um conflito por vez), `GoalSeal`. |
| 7 | `domain/plans/CommitmentsSchedule.kt` | Comprometido por mês (empréstimos, cartão, contas fixas), livre de parcelas, `DebtPrepayment`. |
| 8 | `domain/scenario/PurchaseSimulatorV2.kt` | Pix, 1×, 3×, 6×, 10×: menor saldo por mês com e sem a compra, veredito, melhor forma, atraso nas metas, `factsBlock`. |
| 9 | `domain/assistant/AssistantTools.kt` | Sete ferramentas locais, `parseCall` (linha JSON `{"consulta": …}`), `CATALOG` para o prompt. |
| 10 | `domain/closing/MonthClosing.kt` | Entrou/saiu/sobrou, diferenças por categoria, cobrança que não veio, quatro passos, `summary`/`factsBlock`. |
| notif. | `domain/notify/NotificationPlanner.kt` | Texto, valor, barra e botões de cada notificação; sequência de dias; lembrete diário; exemplos. |

### Estado
- `FinanceUiState.v2: V2State` e `rawAssinaturas`; `FinanceViewModel.buildV2` (nova etapa do
  `combine`) e as ações `keepGoalConflict`, `moveGoalDeadline`, `applyLimits`, `setLimitCents`,
  `answerClosing`, `rememberFact`, `forgetFact`, `learnCategory`, `acknowledgePattern`,
  `saveQuickDraft`.

### Telas (escritas, **não ligadas**)
- `ui/screens/spending/SpendingScreen.kt` (Gastos, absorve Limites).
- `ui/screens/plans/PlansScreen.kt` (Metas | Compromissos).
- `ui/screens/simulator/SimulatorScreen.kt` (Posso comprar?).
- `ui/screens/closing/ClosingScreen.kt` (`ClosingCard` + `ClosingScreen`).
- `ui/components/V2Charts.kt` (`V2BarChart`, `LegendDot`).

### Notificações com botões (pedido novo)
- Desenho: `res/layout/notification_finai_small.xml` e `notification_finai_big.xml` (selo em
  degradê com símbolo, rótulo colorido, título, texto, valor grande, barra, até três botões),
  `res/drawable/notif_*`, cores em `values/` e `values-night/notification_colors.xml`.
- `data/notifications/RichNotifier.kt`: monta os `RemoteViews` com `DecoratedCustomViewStyle`.
  Botão que abre tela usa `PendingIntent.getActivity`; os outros, broadcast.
- `data/notifications/NotificationActionReceiver.kt` (declarado no manifesto, não exportado):
  Já paguei (com Desfazer por 12 s), Lembrar amanhã, Dispensar, Já sei, Nada hoje, Salvar e
  Ignorar movimentação lida.
- `data/notifications/AppLinks.kt` + `MainActivity`: rota pedida pelo toque (`caixa`, `agenda`,
  `gastos`, `planos`, `lancar`, `fechamento`). **Ninguém consome `AppLinks.open` ainda.**
- `FinanceCheckWorker`: uma notificação por evento (até 3 por execução) em vez do lote com texto
  de IA; convite do fechamento (dias 1–7); lembrete de compra do simulador.
- `data/work/DailyReminderWorker.kt` + canal `finai_lembrete`: lembrete do dia, só se nada foi
  lançado. Preferências novas em `FinaiPreferences` (`dailyReminder`, `dailyReminderHour`,
  `quietDays`, `purchaseReminder`). Agendado em `FinaiApplication`.
- `CaptureNotifications.notifySuggestion` passou a usar o desenho novo; `CaptureProcessor`
  decide se "Salvar" pode gravar direto.
- Configurações → Notificações: ligar/desligar o lembrete, hora (12h/18h/20h/22h), "Ver exemplos".

## O que falta, na ordem

1. **Compilar e corrigir.** Nada passou pelo compilador. Pontos que eu conferiria primeiro:
   `PlansScreen` (`MoneyRowBackground.Card`, `Color.themedText`), `SpendingScreen`,
   `FinanceCheckWorker` (bloco novo usa `prefs`, `visibleItems`, `closingKey`), `SettingsScreen`
   (imports de `collectAsState`/`launch`), `NotificationActionReceiver` (`isTest`, `toMovement`).
   `NotificationContentBuilder` ficou sem uso no worker: decidir se sai (e o teste dele junto).
2. **Ligar a navegação em `FinaiApp.kt`** (não alterado):
   - `FinaiDestination`: tirar `Debts` e `Budgets`, criar `Spending("gastos", "Gastos")`, `Goals` com título "Planos".
   - `bottomTabs` e `FinaiBottomNav`: Hoje · Agenda · [+] · Gastos · Planos.
   - Trocar `GoalsScreen`/`DebtsScreen`/`BudgetsScreen` por `PlansScreen` e `SpendingScreen`;
     tirar `InstallmentsScreen`, `BuySimulatorContent`, `BudgetLimitDialog`.
   - Camadas novas: `SimulatorScreen` ("Lançar" abre `TransactionEntryScreen` com `EntryPrefill`
     de Crédito/Parcelado) e `ClosingScreen`; incluir nas travas `captureBlocked`, `tipsBlocked`, `anyLayerOpen`.
   - Consumir `AppLinks.open`. Coach: "Já sei" → `acknowledgePattern`; "Ver gastos" → Gastos.
   - `HomeScreen`: `ClosingCard` quando `v2.showClosingCard`.
   - `QuickActionSheet`: linha "Falar ou escrever" e "N das notificações".
3. **Fase 5, formulário:** faixa Falar · Escrever · Notificações (N) · Importar no topo de
   `TransactionEntryContent` (depois de `PrefillBanner`, só sem `edit` e sem `prefill`). O
   rascunho é `rememberSaveable` local, então dá para aplicar o `QuickDraft` ali. Voz:
   `RecognizerIntent` com `EXTRA_PREFER_OFFLINE` e `<queries>` no manifesto.
4. **Fase 9, chat (`AppViewModel.send`):** frase de lançamento → cartão "Lançamento pronto"
   (Salvar/Editar/Desfazer); laço de até 3 consultas com `AssistantTools` e resposta validada
   pelo `ScenarioReplyGuard`; memória no prompt (até 10 fatos) e painel "Sei sobre você";
   linha "consultei: …" no `ChatOverlay`. Aprender categoria na confirmação da importação.
5. **Fase 10:** resumo do fechamento por IA (uma chamada por mês, guardada em `fechamentos`,
   passo `resumo`) e as ações reais dos quatro passos.
6. **Testes JVM** (nenhum escrito): `QuickEntryParserTest` (≥ 25 frases), `SpendingAnalysisTest`,
   `LimitSuggestionTest`, `SavingsCapacityTest`, `GoalConflictResolverTest`,
   `CommitmentsScheduleTest`, `PurchaseSimulatorV2Test`, `AssistantToolsTest`, `MonthClosingTest`,
   `NotificationPlannerTest`.
7. **Fase 11:** apagar o código morto, reescrever `TipScript`, atualizar `CLAUDE.md`,
   `planning-v2.md`, `planning.md` §9/§11 e `PRIVACY.md`.
8. `sh ./gradlew assembleDebug testDebugUnitTest assemblePessoal` e entregar
   `app/build/outputs/apk/pessoal/app-pessoal.apk`.

## Diferenças em relação ao planning-v2 (decididas ou a decidir)

- **Ferramentas do assistente:** só o protocolo de linha JSON (o "fallback de duas chamadas"),
  em todos os provedores. Tool calling nativo não foi implementado.
- **Busca de Gastos:** em memória, sobre a lista já carregada; sem `TransacaoDao.search`.
- **Números do protótipo:** não consegui abrir o protótipo (pede login). O cenário "pedem
  R$ 1.625, cabe R$ 820, Notebook → nov/27 com R$ 220/mês" não fecha com a regra escrita
  (R$ 220/mês levaria mais de 12 meses); os testes terão de usar números próprios.
- **Simulador no chat:** o chat continua no `ScenarioSimulator` antigo; `PurchaseScenario.toRecords` não saiu.
- **Notificações:** o texto por evento vem de modelos fixos, sem IA. O horário do lembrete é
  aproximado (WorkManager). Nada foi visto num aparelho.
- **Busca no topo da Agenda** (fase 4) continua pendente.

## Ambiente
- "trilha" ganhou `dl.google.com` e `maven.google.com` na lista de hosts (aprovado em 14/11).
- Sem emulador nesta sessão: validação visual, TalkBack e instrumentados continuam por fazer.
