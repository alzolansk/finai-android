package com.finai.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.SideEffect
import com.finai.app.domain.AlertKind
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.finai.app.data.capture.toMovement
import com.finai.app.ui.theme.FinaiMotion
import com.finai.app.ui.theme.finaiTween
import com.finai.app.ui.theme.rememberReducedMotion
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.model.Bill
import com.finai.app.data.model.Goal
import com.finai.app.data.model.Subscription
import com.finai.app.data.notifications.FinaiNotifier
import com.finai.app.data.work.FinanceCheckWorker
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.toUiBill
import com.finai.app.navigation.FinaiDestination
import com.finai.app.state.AiViewModel
import com.finai.app.state.AppViewModel
import com.finai.app.state.FinanceViewModel
import com.finai.app.state.ImportViewModel
import com.finai.app.state.toAiSummaryText
import com.finai.app.ui.components.AddContaDialog
import com.finai.app.ui.components.DebtEntryScreen
import com.finai.app.ui.components.CelebrationOverlay
import com.finai.app.domain.AssistantTopic
import com.finai.app.domain.AssistantTopics
import com.finai.app.domain.InsightAction
import com.finai.app.domain.AiPromptBuilder
import com.finai.app.ui.components.TransactionEntryScreen
import com.finai.app.domain.transactionsInMonth
import com.finai.app.domain.occurrenceFor
import androidx.compose.runtime.saveable.rememberSaveable
import com.finai.app.ui.components.SettingsScreen
import com.finai.app.ui.components.rememberOnResume
import com.finai.app.ui.components.BudgetLimitDialog
import com.finai.app.ui.components.BuySimulatorContent
import com.finai.app.ui.components.ChatOverlay
import com.finai.app.ui.components.GoalContributionSheet
import com.finai.app.ui.components.GoalCreateScreen
import com.finai.app.ui.components.GoalEditScreen
import com.finai.app.ui.components.FinaiBottomNav
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.finai.app.ui.components.NavBarBottomScrim
import com.finai.app.ui.components.glassSource
import com.finai.app.ui.components.rememberGlassBackdrop
import com.finai.app.ui.components.rememberNavBarCollapseState
import com.finai.app.ui.components.FinaiPageHeader
import com.finai.app.ui.components.HeaderActions
import com.finai.app.ui.components.HeaderLeading
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.DisposableEffect
import com.finai.app.ui.components.InitialSetupWizard
import com.finai.app.ui.components.TopBarContentHeight
import com.finai.app.ui.components.TipOverlay
import com.finai.app.ui.components.TipTargetRegistry
import com.finai.app.ui.components.LocalTipTargets
import com.finai.app.domain.Tip
import com.finai.app.domain.TipScript
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import com.finai.app.ui.components.QuickActionSheet
import com.finai.app.ui.components.capture.QueuedSuggestionToast
import com.finai.app.ui.components.capture.SuggestionDialog
import com.finai.app.ui.screens.agenda.AgendaScreen
import com.finai.app.ui.screens.budgets.BudgetsScreen
import com.finai.app.ui.screens.installments.InstallmentsScreen
import com.finai.app.domain.InstallmentsSummary
import com.finai.app.ui.screens.debts.DebtsScreen
import com.finai.app.ui.screens.goals.GoalsScreen
import com.finai.app.ui.screens.home.HomeScreen
import com.finai.app.ui.screens.importer.ImportScreen
import com.finai.app.ui.screens.invoice.InvoiceScreen
import com.finai.app.ui.theme.FinaiColors
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement

/**
 * App root: sticky top bar + the six screens behind Navigation-Compose, the
 * floating bottom nav, the manual-entry dialogs, and the four overlays
 * (quick actions, buy simulator, notifications, chat) that can appear above
 * any of them. [FinanceViewModel] owns every real, Room-backed number
 * (planning.md §9 Fase 1); [AppViewModel] owns overlay-only state and the
 * chat's AI call; [AiViewModel] owns every other AI-generated text (goal
 * insights, purchase verdict, debt negotiation, coach). "Decisões para você"
 * vem do plano central ([com.finai.app.domain.FinancialPlan]), sem IA.
 * This composable only wires "ensure this AI text" calls to the screens that
 * need them via `LaunchedEffect`; it holds no AI logic itself.
 */
@Composable
fun FinaiApp(
    viewModel: AppViewModel = viewModel(),
    financeViewModel: FinanceViewModel = viewModel(),
    aiViewModel: AiViewModel = viewModel(),
    importViewModel: ImportViewModel = viewModel(),
    captureViewModel: com.finai.app.state.CaptureViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val financeState by financeViewModel.uiState.collectAsState()
    val configuredAiProviders by aiViewModel.configuredProviders.collectAsState()
    val goalAnalyses by aiViewModel.goalAnalyses.collectAsState()
    val purchaseVerdict by aiViewModel.purchaseVerdict.collectAsState()
    val debtNegotiation by aiViewModel.debtNegotiation.collectAsState()
    val importState by importViewModel.uiState.collectAsState()
    val persistenceError by financeViewModel.persistenceError.collectAsState()
    val celebration by financeViewModel.celebration.collectAsState()
    val tipsSeen by viewModel.tipsSeen.collectAsState()
    val tipRegistry = remember { TipTargetRegistry() }
    var rootHeightPx by remember { mutableStateOf(0f) }
    val initialSetupComplete by viewModel.initialSetupComplete.collectAsState()
    val knownAccounts by viewModel.knownAccounts.collectAsState()
    // Lançamento por notificações: sugestões, fila e área de testes.
    val captureState by captureViewModel.uiState.collectAsState()
    val captureApps by captureViewModel.apps.collectAsState()
    val captureSimulation by captureViewModel.simulation.collectAsState()
    val listenerConnected by captureViewModel.listenerConnected.collectAsState()
    val captureLink by com.finai.app.data.capture.CaptureLinks.open.collectAsState()
    val lifecycleState by androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val appResumed = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    LaunchedEffect(appResumed) { if (appResumed) financeViewModel.refreshInboxDay() }
    val financeSummary = remember(financeState) { financeState.toAiSummaryText() }
    // O simulador de compra do chat lê uma cópia das mesmas listas do Room; nunca grava nela.
    LaunchedEffect(financeState.rawContas, financeState.rawTransacoes, financeState.rawDividas, financeState.rawObjetivos, financeState.rawFaturasCartao, financeState.safetyMarginCents) {
        viewModel.setFinanceSnapshot(
            com.finai.app.domain.scenario.FinanceSnapshot(
                contas = financeState.rawContas,
                transacoes = financeState.rawTransacoes,
                dividas = financeState.rawDividas,
                objetivos = financeState.rawObjetivos,
                faturas = financeState.rawFaturasCartao,
                safetyMarginCents = financeState.safetyMarginCents,
            ),
        )
    }
    val conversations by viewModel.conversations.collectAsState()
    val askAbout: (AssistantTopic) -> Unit = { topic -> viewModel.askAbout(topic, financeSummary) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = FinaiDestination.fromRoute(backStackEntry?.destination?.route)
    // Tab switches and overlays share one fade duration; `reducedMotion` collapses it to
    // 0ms so "Remove animations" is honored the same way finaiTween() honors it elsewhere,
    // since NavHost's transition lambdas run outside a @Composable scope.
    val reducedMotion = rememberReducedMotion()
    val tabFadeMillis = if (reducedMotion) 0 else FinaiMotion.Quick

    // Barra de baixo de vidro (playbook 3a): o conteúdo das telas é a fonte do desfoque,
    // a rolagem decide se ela recolhe, e trocar de tela a devolve expandida.
    val glassBackdrop = rememberGlassBackdrop()
    val navCollapse = rememberNavBarCollapseState()
    navCollapse.enabled = !reducedMotion
    LaunchedEffect(current) { navCollapse.reset() }
    // Teclado aberto esconde a barra. derivedStateOf: o inset do teclado muda a cada
    // quadro da animação dele, e isso não deve recompor o app inteiro.
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember(imeInsets, density) { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val showNavBar = current != FinaiDestination.AiSettings && current != FinaiDestination.Inbox && !imeVisible

    // Cabeçalho 4a: cada aba guarda a própria lista (e a rolagem dela) aqui em cima, para o
    // cabeçalho ler a posição da aba aberta no mesmo quadro da troca, sem esperar a tela
    // nova se registrar. Limites e Importação empilham e começam do topo a cada visita:
    // a lista delas nasce na própria entrada do NavHost e se registra em secondaryLists.
    val tabLists = bottomTabs.associateWith { rememberLazyListState() }
    val secondaryLists = remember { mutableStateMapOf<FinaiDestination, LazyListState>() }
    val activeList = tabLists[current] ?: secondaryLists[current]

    val unreadAlerts = financeState.unreadInbox
    LaunchedEffect(current, financeState.inboxItems.map { it.id }) {
        if (current == FinaiDestination.Inbox) financeViewModel.markInboxSeen(financeState.inboxItems.map { it.id }.toSet())
    }

    fun navigateTo(destination: FinaiDestination) {
        navController.navigate(destination.route) {
            // Abas da barra de baixo trocam entre si (voltar leva à Início).
            // Limites e Importação são telas secundárias: empilham sobre a
            // tela atual, para o voltar retornar exatamente de onde se veio.
            if (destination in bottomTabs) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                restoreState = true
            }
            launchSingleTop = true
        }
    }

    val inboxLink by com.finai.app.data.notifications.InboxLinks.open.collectAsState()
    LaunchedEffect(inboxLink) {
        if (inboxLink) {
            navigateTo(FinaiDestination.Inbox)
            com.finai.app.data.notifications.InboxLinks.consume()
        }
    }

    var showAddTransaction by rememberSaveable { mutableStateOf(false) }
    /** Lançamento aberto para edição pela Agenda: id gravado e mês da ocorrência tocada. */
    var editEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editEntryMonth by rememberSaveable { mutableStateOf<String?>(null) }
    /** Data inicial do lançamento aberto pelo "+" da Agenda (o mês que está na tela). */
    var transactionEntryInitialDate by rememberSaveable { mutableStateOf<String?>(null) }
    var bellCenterX by remember { mutableStateOf<Float?>(null) }
    // Único ponto de entrada pra lançar algo novo (planning.md §11: "Conta" deixou de ter
    // um "+" próprio — tudo que o usuário cadastra passa por aqui, e é isso que faz o
    // resumo da Agenda refletir o que foi lançado). initialType só é aplicado se a tela
    // abrir com o rascunho vazio (ver TransactionEntryScreen), pra não sobrescrever um
    // rascunho em andamento.
    var transactionEntryInitialType by rememberSaveable { mutableStateOf(com.finai.app.domain.TransactionType.Gasto.name) }
    var transactionEntryInitialExtra by rememberSaveable { mutableStateOf(false) }
    /** "Informar renda" da Início: abre a receita já marcada como renda principal (Fase 7, item 5). */
    var transactionEntryInitialMain by rememberSaveable { mutableStateOf(false) }
    var showAddGoal by remember { mutableStateOf(false) }
    /** Criação de objetivo pedida pelo destino da transferência (volta ao lançamento ao terminar). */
    var goalCreateFromEntry by rememberSaveable { mutableStateOf(false) }
    var goalCreateKind by rememberSaveable { mutableStateOf<String?>(null) }
    /** Item recém-lançado que a Agenda destaca ao abrir pelo "Ver na agenda". */
    var agendaHighlight by remember { mutableStateOf<com.finai.app.domain.AgendaHighlight?>(null) }
    var showAddDivida by remember { mutableStateOf(false) }
    var contributionTarget by remember { mutableStateOf<Goal?>(null) }
    var budgetLimitTarget by remember { mutableStateOf<String?>(null) }
    // Edição de itens já cadastrados — o mesmo diálogo de "Novo X" pré-preenchido
    // via `initial`, mantendo id (e, no caso de conta, o status pago/pendente)
    // intactos ao salvar (ver EntryDialogs.kt).
    var editingContaId by remember { mutableStateOf<Long?>(null) }
    var editingDividaId by remember { mutableStateOf<Long?>(null) }
    var openedInvoiceContaId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** Tela de Parcelamentos: camada por cima de tudo (entra da direita), não rota do NavHost. */
    var showInstallments by rememberSaveable { mutableStateOf(false) }
    var showIncome by rememberSaveable { mutableStateOf(false) }
    var showReconcile by rememberSaveable { mutableStateOf(false) }
    var homeMovement by remember { mutableStateOf<com.finai.app.domain.TimelineMovement?>(null) }
    var homeDelete by remember { mutableStateOf<com.finai.app.data.local.entity.TransacaoEntity?>(null) }
    val incomeConfig = remember(financeState.rawTransacoes) {
        com.finai.app.domain.IncomePlanner.current(financeState.rawTransacoes, java.time.LocalDate.now())
    }
    /** Sugestão aberta no quadro "Movimentação encontrada" (uma por vez). */
    var openSuggestionId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** Lista "Para confirmar". */
    /** "Editar" de uma sugestão: o formulário do "+" numa instância própria, já preenchido. */
    var editSuggestionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editSuggestionPrefill by rememberSaveable(stateSaver = com.finai.app.ui.components.EntryPrefillSaver) {
        mutableStateOf<com.finai.app.ui.components.EntryPrefill?>(null)
    }
    var settingsAtCapture by rememberSaveable { mutableStateOf(false) }
    // Toque em "Movimentação encontrada": abre essa sugestão, com o app fechado ou aberto.
    LaunchedEffect(captureLink) {
        captureLink?.let { id ->
            openSuggestionId = id
            com.finai.app.data.capture.CaptureLinks.consume()
        }
    }
    var editingObjetivoId by remember { mutableStateOf<Long?>(null) }
    val editingConta = financeState.rawContas.firstOrNull { it.id == editingContaId }
    val editingDivida = financeState.rawDividas.firstOrNull { it.id == editingDividaId }
    val editingObjetivo = financeState.rawObjetivos.firstOrNull { it.id == editingObjetivoId }
    val contributionObjetivo = contributionTarget?.let { goal -> financeState.rawObjetivos.firstOrNull { it.id.toString() == goal.id } }

    val agenda = remember(financeState.rawContas, financeState.rawFaturasCartao, financeState.rawTransacoes, financeState.rawDividas, uiState.monthIndex, uiState.agendaYear) {
        agendaDataFor(financeState.rawContas, financeState.rawTransacoes, financeState.rawDividas, uiState.monthIndex, uiState.agendaYear)
    }

    // Compras parceladas no cartão: o cartão da Agenda e a tela de Parcelamentos leem a mesma conta.
    val entryCards = remember(financeState.rawFaturasCartao, financeState.rawTransacoes) {
        com.finai.app.domain.EntryOptions.knownCards(financeState.rawFaturasCartao, financeState.rawTransacoes)
    }
    val entryAccounts = remember(financeState.rawTransacoes, entryCards, knownAccounts) {
        com.finai.app.domain.EntryOptions.recentAccounts(financeState.rawTransacoes, entryCards, knownAccounts)
    }
    // Compras dentro de cada fatura, por conta: "cartão · 14 compras" nas linhas da Agenda.
    val invoiceItemCounts = remember(financeState.rawTransacoes, financeState.rawFaturasCartao) {
        val contaDaFatura = financeState.rawFaturasCartao.associate { it.id to it.contaId }
        financeState.rawTransacoes.mapNotNull { t -> t.faturaId?.let { contaDaFatura[it] } }.groupingBy { it }.eachCount()
    }
    val installments = remember(financeState.rawTransacoes, financeState.rawFaturasCartao, financeState.rawContas) {
        InstallmentsSummary.of(financeState.rawTransacoes, financeState.rawFaturasCartao, financeState.rawContas)
    }

    CompositionLocalProvider(LocalTipTargets provides tipRegistry) {
    Box(modifier = Modifier.fillMaxSize().background(FinaiColors.Background).onSizeChanged { rootHeightPx = it.height.toFloat() }) {
        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (showAddTransaction || editEntryId != null || contributionTarget != null) Modifier.clearAndSetSemantics {} else Modifier)
                // Fundo da folha de aporte desfocado, como na referência (Android 12+; antes disso só o escurecido).
                .then(if (contributionTarget != null) Modifier.blur(8.dp) else Modifier),
        ) {
            // O conteúdo ocupa a tela toda e rola por baixo do cabeçalho; é a mesma fonte do
            // vidro da barra de baixo e do cabeçalho. O cabeçalho fica fora dela (logo abaixo),
            // senão ele próprio entraria no desfoque.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(navCollapse.nestedScrollConnection)
                    .glassSource(glassBackdrop),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = FinaiDestination.Home.route,
                    // A quiet crossfade between tabs — no slide, since each screen has its
                    // own height/scroll position and a directional slide would fight that.
                    enterTransition = { fadeIn(tween(tabFadeMillis)) },
                    exitTransition = { fadeOut(tween(tabFadeMillis)) },
                    popEnterTransition = { fadeIn(tween(tabFadeMillis)) },
                    popExitTransition = { fadeOut(tween(tabFadeMillis)) },
                ) {
                    composable(FinaiDestination.Home.route) {
                        HomeScreen(
                            listState = tabLists.getValue(FinaiDestination.Home),
                            goals = financeState.goals,
                            situation = financeState.situation,
                            free = financeState.plan?.free,
                            reconciliation = financeState.reconciliation,
                            week = financeState.homeWeek,
                            showCoach = uiState.showCoach,
                            behaviorPattern = financeState.behaviorPattern,
                            goalPlan = financeState.plan?.recommendations?.firstOrNull { it.target == com.finai.app.domain.RecommendationTarget.Goal }?.let {
                                "Plano de ${com.finai.app.domain.MONTH_NAMES_PT[java.time.LocalDate.now().monthValue - 1].lowercase()}: guardar ${com.finai.app.util.formatBrl0(it.cents / 100.0)} em ${it.name}"
                            }.orEmpty(),
                            onOpenGoals = { navigateTo(FinaiDestination.Goals) },
                            onNewGoal = { showAddGoal = true },
                            onOpenSimulator = viewModel::openSimulator,
                            onExplainSafe = viewModel::openSpendExplanation,
                            onOpenAgenda = { navigateTo(FinaiDestination.Agenda) },
                            onMovement = { movement ->
                                val source = movement.source
                                if (source is com.finai.app.domain.CycleSource.Conta) financeViewModel.marcarContaPaga(source.conta.id, source.conta.status != "pago")
                                else homeMovement = movement
                            },
                            onDismissCoach = { financeViewModel.dismissInsight(it) },
                            onCoachDetails = { navigateTo(FinaiDestination.Budgets) },
                            onAddMainIncome = { transactionEntryInitialType = com.finai.app.domain.TransactionType.Receita.name; transactionEntryInitialMain = true; showAddTransaction = true },
                            onNewEntry = { showAddTransaction = true },
                        )
                    }
                    composable(FinaiDestination.Inbox.route) {
                        com.finai.app.ui.screens.inbox.InboxScreen(
                            listState = rememberSecondaryList(FinaiDestination.Inbox, secondaryLists),
                            items = financeState.inboxItems, dismissedCount = financeState.dismissedInbox,
                            onOpen = { item ->
                                val insight = item.insight
                                when {
                                    item.suggestion != null -> openSuggestionId = item.suggestion.id
                                    insight != null -> when (insight.action) {
                                        InsightAction.EditGoal -> editingObjetivoId = insight.actionGoalId
                                        InsightAction.SimulateGoal -> financeState.goals.firstOrNull { it.id == insight.actionGoalId?.toString() }
                                            ?.let { askAbout(AssistantTopics.goal(it, financeState.goalFacts)) }
                                        InsightAction.ComparePlan -> askAbout(AssistantTopics.goalsPlan(financeState.goalFacts))
                                        InsightAction.OpenDebts -> navigateTo(FinaiDestination.Debts)
                                        InsightAction.AddIncome -> { transactionEntryInitialType = com.finai.app.domain.TransactionType.Receita.name; showAddTransaction = true }
                                        else -> navigateTo(FinaiDestination.Agenda)
                                    }
                                    item.kind == "limite" || item.kind == "coach" -> navigateTo(FinaiDestination.Budgets)
                                    item.movement?.source is com.finai.app.domain.CycleSource.Parcela -> homeMovement = item.movement
                                    else -> navigateTo(FinaiDestination.Agenda)
                                }
                            },
                            onEditSuggestion = { item -> item.suggestion?.let { suggestion ->
                                editSuggestionPrefill = prefillOf(suggestion, com.finai.app.domain.capture.SuggestionRules.draftOf(suggestion.toMovement()))
                                editSuggestionId = suggestion.id
                            } },
                            onPay = { item -> (item.movement?.source as? com.finai.app.domain.CycleSource.Conta)?.let { financeViewModel.marcarContaPaga(it.conta.id, true) } },
                            onDismiss = { financeViewModel.dismissInbox(it) },
                            onSnooze = { item, date -> financeViewModel.snoozeInbox(item, date) },
                            onRestore = { financeViewModel.restoreInbox() }, onIgnore = captureViewModel::ignore,
                            onSaveSuggestion = { item ->
                                val suggestion = item.suggestion!!
                                val draft = com.finai.app.domain.capture.SuggestionRules.draftOf(suggestion.toMovement())
                                val due = draft.card?.let { card -> com.finai.app.domain.EntryOptions.resolveCardDue(card, draft.date, financeState.rawFaturasCartao, financeState.rawContas) }
                                if (suggestion.possivelRepeticao || com.finai.app.domain.capture.SuggestionRules.missing(draft, due).isNotEmpty()) {
                                    openSuggestionId = suggestion.id
                                    null
                                } else captureViewModel.confirm(suggestion.id, draft, due)
                            },
                            onUndoSuggestion = captureViewModel::undo,
                        )
                    }
                    composable(FinaiDestination.Agenda.route) {
                        AgendaScreen(
                            listState = tabLists.getValue(FinaiDestination.Agenda),
                            monthIndex = uiState.monthIndex,
                            year = uiState.agendaYear,
                            onPrevMonth = viewModel::prevMonth,
                            onNextMonth = viewModel::nextMonth,
                            monthMovements = agenda.movements,
                            entraCents = agenda.entraCents,
                            saiCents = agenda.saiCents,
                            sobraCents = agenda.sobraCents,
                            competenceShifted = agenda.competenceShifted,
                            invoiceItemCount = { id -> invoiceItemCounts[id] ?: 0 },
                            onPayInstallment = financeViewModel::pagarParcela,
                            onToggleContaPaga = financeViewModel::marcarContaPaga,
                            onDeleteTransaction = financeViewModel::deleteTransacao,
                            onDeleteConta = financeViewModel::deleteConta,
                            onEditConta = { id -> editingContaId = id },
                            onOpenInvoice = { contaId -> openedInvoiceContaId = contaId },
                            onToggleExtra = financeViewModel::setTransacaoExtra,
                            onToggleMainIncome = financeViewModel::setTransacaoRendaPrincipal,
                            onEditDebt = { id -> editingDividaId = id },
                            onNewEntry = {
                                // Abre no mês que está na tela: hoje, se for o mês atual; senão o dia 1º.
                                val shown = java.time.YearMonth.of(uiState.agendaYear, uiState.monthIndex + 1)
                                val now = java.time.LocalDate.now()
                                transactionEntryInitialDate = (if (shown == java.time.YearMonth.from(now)) now else shown.atDay(1)).toString()
                                showAddTransaction = true
                            },
                            onImport = { navigateTo(FinaiDestination.Import) },
                            sequential = uiState.agendaSequential,
                            onSequentialChange = viewModel::setAgendaSequential,
                            agendaBalance = financeState.agendaBalance,
                            today = java.time.LocalDate.now(),
                            highlight = agendaHighlight,
                            onHighlightShown = { agendaHighlight = null },
                            seriesOf = { id -> financeState.rawTransacoes.firstOrNull { it.id == id } },
                            onEditTransaction = { t ->
                                editEntryId = t.id
                                editEntryMonth = com.finai.app.domain.RecurringSeries.referenceMonth(t).toString()
                            },
                            onDeleteOccurrence = financeViewModel::deleteOccurrence,
                            onAdjustInstallmentDate = financeViewModel::adjustInstallmentDate,
                            onChangeInstallmentDay = financeViewModel::changeInstallmentDayFrom,
                        )
                    }
                    composable(FinaiDestination.Goals.route) {
                        // Pontos de atenção são calculados sem IA. A IA só roda quando o usuário toca em
                        // "Analisar com IA" (planejamento inteiro ou uma meta), sobre os fatos calculados.
                        val facts = financeState.goalFacts
                        GoalsScreen(
                            listState = tabLists.getValue(FinaiDestination.Goals),
                            goals = financeState.goals,
                            completedGoals = financeState.completedGoals,
                            plan = financeState.plan,
                            overview = financeState.goalsOverview,
                            insights = emptyList(),
                            planAnalysis = goalAnalyses[AiViewModel.PLAN_ANALYSIS_KEY],
                            goalAnalyses = goalAnalyses,
                            onNewGoal = { showAddGoal = true },
                            onContribute = { goal -> contributionTarget = goal },
                            onEdit = { goal -> editingObjetivoId = goal.id.toLong() },
                            onDelete = { goal -> financeViewModel.deleteObjetivoById(goal.id.toLong()) },
                            onSimulate = { goal -> askAbout(AssistantTopics.goal(goal, facts)) },
                            onExplain = viewModel::openSpendExplanation,
                            onAnalyzePlan = {
                                aiViewModel.analyzeGoals(AiViewModel.PLAN_ANALYSIS_KEY, AiPromptBuilder.goalsPlanAnalysis(facts), facts)
                            },
                            onAnalyzeGoal = { goal ->
                                aiViewModel.analyzeGoals(goal.id, AiPromptBuilder.goalAnalysis(goal.name, facts), facts)
                            },
                            onInsightAction = { insight ->
                                // Cada ação leva a uma função que já existe: nada de botão decorativo.
                                when (insight.action) {
                                    InsightAction.EditGoal -> insight.actionGoalId?.let { editingObjetivoId = it }
                                    InsightAction.SimulateGoal -> financeState.goals.firstOrNull { it.id == insight.actionGoalId?.toString() }
                                        ?.let { askAbout(AssistantTopics.goal(it, facts)) }
                                    InsightAction.ComparePlan -> askAbout(AssistantTopics.goalsPlan(facts))
                                    InsightAction.OpenDebts -> navigateTo(FinaiDestination.Debts)
                                    InsightAction.OpenAgenda -> navigateTo(FinaiDestination.Agenda)
                                    InsightAction.AddIncome -> {
                                        transactionEntryInitialType = com.finai.app.domain.TransactionType.Receita.name
                                        transactionEntryInitialExtra = false
                                        showAddTransaction = true
                                    }
                                    InsightAction.None -> Unit
                                }
                            },
                            dismissedCount = 0,
                            onDismissInsight = { insight -> financeViewModel.dismissInsight(insight.dismissKey) },
                            onRestoreDismissed = { navigateTo(FinaiDestination.Inbox) },
                            contributions = remember(financeState.rawTransacoes) {
                                com.finai.app.domain.ContributionRecord.byGoal(financeState.rawTransacoes)
                            },
                        )
                    }
                    composable(FinaiDestination.Debts.route) {
                        LaunchedEffect(financeState.debts) {
                            aiViewModel.ensureDebtNegotiation(financeState.debts)
                        }
                        DebtsScreen(
                            listState = tabLists.getValue(FinaiDestination.Debts),
                            debts = financeState.debts,
                            totalOpenLabel = financeState.debtTotalLabel,
                            monthlyInterestLabel = financeState.debtInterestLabel,
                            debtFreeLabel = financeState.debtFreeLabel,
                            strategyNote = financeState.debtStrategyNote,
                            negotiationTitle = financeState.negotiationTitle,
                            negotiationScript = debtNegotiation,
                            onNewDebt = { showAddDivida = true },
                            onEditDebt = { debt -> editingDividaId = debt.id },
                            onDeleteDebt = { debt -> financeViewModel.deleteDividaById(debt.id) },
                            onPayInstallment = { debt -> financeViewModel.pagarParcela(debt.id) },
                            onRehearseCall = { askAbout(AssistantTopics.debtCall(financeState.debts)) },
                            paidDebts = financeState.paidDebts,
                            onDeletePaidDebt = { paid -> financeViewModel.deleteDividaById(paid.id) },
                            // Parcelamentos saíram da Agenda (fase 4) e ficam aqui até Planos › Compromissos (fase 7).
                            installments = installments,
                            onOpenInstallments = { showInstallments = true },
                        )
                    }
                    composable(FinaiDestination.Budgets.route) {
                        BudgetsScreen(
                            listState = rememberSecondaryList(FinaiDestination.Budgets, secondaryLists),
                            budgets = financeState.budgets,
                            subscriptions = financeState.subscriptions,
                            recentTransactions = financeState.rawTransacoesDoMes,
                            onEditLimit = { categoria -> budgetLimitTarget = categoria },
                            onSubscriptionAction = { sub: Subscription ->
                                financeViewModel.toggleAssinatura(sub.name, ativa = sub.cta != "Cancelar")
                            },
                            onNewTransaction = { showAddTransaction = true },
                            onDeleteTransaction = financeViewModel::deleteTransacao,
                        )
                    }
                    composable(FinaiDestination.Import.route) {
                        ImportScreen(
                            listState = rememberSecondaryList(FinaiDestination.Import, secondaryLists),
                            state = importState,
                            onPickDocument = importViewModel::importDocument,
                            onToggleItem = importViewModel::toggleItem,
                            onSetCategory = importViewModel::setCategory,
                            onSetAllSelected = importViewModel::setAllSelected,
                            onSetInvoiceReference = importViewModel::setInvoiceReference,
                            onSetInvoiceDueDate = importViewModel::setInvoiceDueDate,
                            onConfirm = importViewModel::confirmImport,
                            onReset = importViewModel::reset,
                            onOpenAgenda = {
                                importViewModel.reset()
                                navigateTo(FinaiDestination.Agenda)
                            },
                        )
                    }
                    composable(FinaiDestination.AiSettings.route) {
                        val context = LocalContext.current
                        val exhaustedAiProviders by aiViewModel.exhaustedToday.collectAsState()
                        val aiConnectionTests by aiViewModel.connectionTests.collectAsState()
                        val backupStatus by financeViewModel.backupStatus.collectAsState()
                        val pendingRestore by financeViewModel.pendingRestore.collectAsState()
                        val themeMode by viewModel.themeMode.collectAsState()
                        val listenerAccess = rememberOnResume { captureViewModel.hasListenerAccess() }
                        val ownNotifications = rememberOnResume { FinaiNotifier(context).hasNotificationPermission() }
                        val captureBindings = com.finai.app.ui.components.CaptureSettingsBindings(
                            state = captureState, apps = captureApps, simulation = captureSimulation,
                            listenerAccess = listenerAccess, listenerConnected = listenerConnected,
                            ownNotificationsAllowed = ownNotifications,
                            onOpenAccessSettings = {
                                runCatching { context.startActivity(captureViewModel.listenerSettingsIntent()) }
                                    .onFailure { runCatching { context.startActivity(captureViewModel.fallbackListenerSettingsIntent()) } }
                            },
                            onOpenOwnNotificationSettings = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            },
                            onSetEnabled = { captureViewModel.setEnabled(it) },
                            onTogglePackage = { captureViewModel.togglePackage(it) },
                            onLoadApps = { captureViewModel.loadApps() },
                            onOpenPending = { navigateTo(FinaiDestination.Inbox) },
                            onOpenSuggestion = { openSuggestionId = it },
                            onSimulate = captureViewModel::simulate,
                            onSendTest = captureViewModel::sendTest,
                            onClearTests = { captureViewModel.clearTests() },
                            onClearSimulation = captureViewModel::clearSimulation,
                        )
                        val startAtCapture = remember { settingsAtCapture.also { settingsAtCapture = false } }
                        SettingsScreen(
                            capture = captureBindings,
                            startAtCapture = startAtCapture,
                            onExit = { navController.navigateUp() },
                            configuredProviders = configuredAiProviders,
                            exhaustedProviders = exhaustedAiProviders,
                            connectionTests = aiConnectionTests,
                            onSaveKey = aiViewModel::setProviderKey,
                            onClearKey = aiViewModel::clearProviderKey,
                            onTestProvider = aiViewModel::testProvider,
                            notificationsEnabled = rememberOnResume { FinaiNotifier(context).hasNotificationPermission() },
                            onRunNotificationCheck = { FinanceCheckWorker.runOnce(context) },
                            onOpenIncome = { showIncome = true },
                            incomeStatus = incomeConfig?.let { c -> if (c.split != null) "Em duas partes" else "Integral" },
                            onRestartTour = {
                                viewModel.restartOnboarding()
                                navigateTo(FinaiDestination.Home)
                            },
                            knownAccounts = knownAccounts,
                            onAddAccount = { name -> viewModel.addKnownAccounts(setOf(name)) },
                            onRemoveAccount = viewModel::removeKnownAccount,
                            onEraseAllData = {
                                financeViewModel.apagarTodosOsDados {
                                    aiViewModel.resetMemoizedState()
                                    importViewModel.reset()
                                    viewModel.restartOnboarding()
                                    viewModel.restartInitialSetup()
                                    navigateTo(FinaiDestination.Home)
                                }
                            },
                            onExportData = financeViewModel::exportBackup,
                            onPickRestore = financeViewModel::readBackup,
                            pendingRestoreSummary = pendingRestore?.summary(),
                            onConfirmRestore = {
                                financeViewModel.confirmRestore {
                                    aiViewModel.resetMemoizedState()
                                    importViewModel.reset()
                                    viewModel.completeInitialSetup()
                                }
                            },
                            onCancelRestore = financeViewModel::cancelRestore,
                            backupStatus = backupStatus,
                            onDismissBackupStatus = financeViewModel::dismissBackupStatus,
                            themeMode = themeMode,
                            onSetThemeMode = viewModel::setThemeMode,
                        )
                    }
                }
                NavBarBottomScrim(visible = showNavBar, modifier = Modifier.align(Alignment.BottomCenter))
            }
            // Configurações desenha a própria barra: o título e o "voltar" acompanham a
            // subpágina aberta dentro dela.
            if (current != FinaiDestination.AiSettings) {
                FinaiPageHeader(
                    title = current.headerTitle,
                    leading = if (current in bottomTabs) HeaderLeading.Logo else HeaderLeading.Back { navController.navigateUp() },
                    actions = HeaderActions(
                        unreadCount = unreadAlerts,
                        onOpenNotifications = { navigateTo(FinaiDestination.Inbox) },
                        onOpenSettings = { navController.navigate(FinaiDestination.AiSettings.route) { launchSingleTop = true } },
                        onOpenAssistant = viewModel::openChat,
                        onBellCenterX = { bellCenterX = it },
                        inbox = true,
                    ),
                    listState = activeList,
                    backdrop = glassBackdrop,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        // Fase 6: escrita no Room que falhou. Antes esta exceção derrubava o app;
        // agora `FinanceViewModel.launchSafely` a captura, e o aviso aparece aqui —
        // sem isso, uma falha viraria "toquei em salvar e nada aconteceu".
        persistenceError?.let { mensagem ->
            PersistenceErrorBanner(
                message = mensagem,
                onDismiss = financeViewModel::dismissPersistenceError,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
            )
        }

        AnimatedVisibility(
            visible = showNavBar,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + slideInVertically(finaiTween(FinaiMotion.Standard)) { it / 2 },
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + slideOutVertically(finaiTween(FinaiMotion.Quick)) { it / 2 },
            modifier = Modifier
                .then(if (showAddTransaction || editEntryId != null) Modifier.clearAndSetSemantics {} else Modifier)
                .align(Alignment.BottomCenter),
        ) {
            FinaiBottomNav(
                current = current,
                addOpen = uiState.addOpen,
                collapsed = navCollapse.collapsed,
                backdrop = glassBackdrop,
                onSelect = ::navigateTo,
                onReselect = navCollapse::expand,
                onToggleAdd = viewModel::toggleAddMenu,
            )
        }

        // Every overlay below shares the same fade-scrim + slide-sheet/card language: fast
        // out (dismiss shouldn't make the user wait), a touch slower in. Bottom sheets slide
        // up a short distance, the notifications card slides down from the bell, chat is a
        // plain fade since it already fills the screen.
        AnimatedVisibility(
            visible = uiState.addOpen,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)),
        ) {
            Scrim(onDismiss = viewModel::closeAddMenu)
        }
        AnimatedVisibility(
            visible = uiState.addOpen,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + slideInVertically(finaiTween(FinaiMotion.Standard)) { it / 6 },
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + slideOutVertically(finaiTween(FinaiMotion.Quick)) { it / 6 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 104.dp),
        ) {
            QuickActionSheet(
                onPick = { action ->
                    viewModel.closeAddMenu()
                    when (action.title) {
                        "Lançar gasto" -> showAddTransaction = true
                        "Importar fatura" -> navigateTo(FinaiDestination.Import)
                        "Posso comprar?" -> viewModel.openSimulator()
                        "Novo objetivo" -> showAddGoal = true
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = uiState.simOpen,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)),
        ) {
            Scrim(onDismiss = viewModel::closeSimulator)
        }
        AnimatedVisibility(
            visible = uiState.simOpen,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) + slideInVertically(finaiTween(FinaiMotion.Standard)) { it / 8 },
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) + slideOutVertically(finaiTween(FinaiMotion.Quick)) { it / 8 },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxHeight = 720.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = FinaiColors.Surface,
            ) {
                BuySimulatorContent(
                    amount = uiState.simAmount,
                    plan = financeState.plan,
                    goals = financeState.goals,
                    aiExplain = purchaseVerdict,
                    onEnsureExplain = { amountLabel, verdictLabel, capacityLabel, slackLabel, topGoalName, topGoalAffected ->
                        aiViewModel.ensurePurchaseVerdict(amountLabel, verdictLabel, capacityLabel, slackLabel, topGoalName, topGoalAffected)
                    },
                    onPickPreset = viewModel::setSimAmount,
                    onDecideLater = viewModel::closeSimulator,
                    onAsk = askAbout,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        if (uiState.explainOpen) {
            com.finai.app.ui.screens.home.FreeExplainedScreen(
                free = financeState.plan?.free,
                accountCents = financeState.agendaBalance?.todayCents ?: 0L,
                marginCents = financeState.safetyMarginCents,
                reconciliation = financeState.reconciliation,
                accounts = entryAccounts,
                onBack = viewModel::closeSpendExplanation,
                onMargin = { financeViewModel.setSafetyMargin(it) },
                onReconcile = { account, cents -> financeViewModel.reconcile(account, cents, financeState.agendaBalance?.todayCents ?: 0L) },
            )
        }

        AnimatedVisibility(
            visible = uiState.chatOpen,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)),
        ) {
            ChatOverlay(
                messages = uiState.messages,
                thinking = uiState.thinking,
                draft = uiState.draft,
                conversations = conversations,
                currentConversationId = uiState.conversationId,
                onNewConversation = viewModel::newConversation,
                onOpenConversation = viewModel::openConversation,
                onDeleteConversation = viewModel::deleteConversation,
                onDraftChange = viewModel::onDraftChange,
                onSend = { viewModel.sendDraft(financeSummary) },
                onSuggestion = { text -> viewModel.sendMessage(text, financeSummary) },
                pendingPurchase = uiState.pendingPurchase,
                onRegisterPurchase = { scenario ->
                    financeViewModel.registrarCompra(scenario) { viewModel.onPurchaseRegistered(scenario) }
                },
                onDiscardPurchase = viewModel::discardPendingPurchase,
                onClose = viewModel::closeChat,
            )
        }

        // Listas do lançamento: o usado por último primeiro, lido do Room (nada fixo).
        val entryCategories = remember(financeState.rawTransacoes) {
            com.finai.app.domain.EntryOptions.categoriesByRecentUse(financeState.rawTransacoes)
        }
        val closeEntry = {
            showAddTransaction = false
            transactionEntryInitialType = com.finai.app.domain.TransactionType.Gasto.name
            transactionEntryInitialExtra = false; transactionEntryInitialMain = false
            transactionEntryInitialDate = null
        }
        TransactionEntryScreen(
            visible = showAddTransaction,
            // Only gasto/transferência origins feed the "conta/cartão" picker — a receita's
            // origem (ex.: nome da empresa) não é uma conta e não deve virar opção permanente
            // aqui (ver nota em TransactionEntryScreen sobre incomeSources).
            accounts = entryAccounts,
            incomeSources = remember(financeState.rawTransacoes) {
                com.finai.app.domain.EntryOptions.recentIncomeSources(financeState.rawTransacoes)
            },
            initialType = com.finai.app.domain.TransactionType.valueOf(transactionEntryInitialType),
            initialExtra = transactionEntryInitialExtra,
            initialMainIncome = transactionEntryInitialMain,
            initialDate = transactionEntryInitialDate?.let(java.time.LocalDate::parse),
            onDismiss = closeEntry,
            onSave = financeViewModel::saveTransactionEntry,
            onSaveCard = financeViewModel::saveCardPurchase,
            onSaveSplitIncome = financeViewModel::saveSplitIncome,
            incomeSplitDefault = incomeConfig?.split ?: com.finai.app.domain.IncomeSplit(com.finai.app.domain.AdvanceRule.Percent(40)),
            onOpenIncome = { closeEntry(); showIncome = true },
            cards = entryCards,
            categories = entryCategories,
            objetivos = financeState.rawObjetivos,
            faturas = financeState.rawFaturasCartao,
            contas = financeState.rawContas,
            dailySpendingCents = financeState.averageDailySpendingCents,
            onSaveContribution = financeViewModel::saveContribution,
            onUndo = financeViewModel::undoEntry,
            // Sem reserva/objetivo de destino: abre a criação por cima e volta ao lançamento.
            onCreateGoal = { reserve -> goalCreateKind = if (reserve) "Reserva" else null; goalCreateFromEntry = true; showAddGoal = true },
            onFinished = financeViewModel::releaseContributionCelebration,
            onViewEntry = { receipt ->
                agendaHighlight = com.finai.app.domain.AgendaHighlight.of(receipt)
                viewModel.showAgendaDate(receipt.agendaDate)
                closeEntry()
                navigateTo(FinaiDestination.Agenda)
            },
        )

        // Edição pela Agenda: o mesmo formulário do "+", numa instância própria (o rascunho do "+"
        // continua intacto), montada só enquanto há um item aberto. O alvo é lido uma vez, ao
        // abrir: a gravação muda o registro e a tela não pode trocar de item no meio.
        val editTarget = remember(editEntryId, editEntryMonth) {
            financeState.rawTransacoes.firstOrNull { it.id == editEntryId }?.let { raw ->
                if (raw.recorrente && raw.faturaId == null) {
                    val month = editEntryMonth?.let(java.time.YearMonth::parse)
                    month?.let { m -> raw.occurrenceFor(m)?.let { com.finai.app.ui.components.EntryEditTarget(it, m, accountLocked = false) } }
                } else {
                    com.finai.app.ui.components.EntryEditTarget(raw, null, accountLocked = raw.faturaId != null)
                }
            }
        }
        val closeEdit = { editEntryId = null; editEntryMonth = null }
        LaunchedEffect(editEntryId, editTarget) { if (editEntryId != null && editTarget == null) closeEdit() }
        editTarget?.let { target ->
            androidx.compose.runtime.key(target.occurrence.id, target.month) {
                TransactionEntryScreen(
                    visible = true,
                    accounts = entryAccounts,
                    incomeSources = remember(financeState.rawTransacoes) {
                        com.finai.app.domain.EntryOptions.recentIncomeSources(financeState.rawTransacoes)
                    },
                    onDismiss = closeEdit,
                    onSave = financeViewModel::saveTransactionEntry,
                    onViewEntry = {},
                    cards = entryCards,
                    categories = entryCategories,
                    edit = target,
                    onSaveEdit = { change, scope ->
                        val month = target.month ?: java.time.YearMonth.from(change.data)
                        val id = financeViewModel.editTransaction(target.occurrence.id, month, change, scope)
                        // Mostra o resultado: a Agenda abre no mês do lançamento com a linha destacada.
                        agendaHighlight = com.finai.app.domain.AgendaHighlight(setOf(id), null)
                        // Na sequencial fica onde está; na mensal, vai ao mês da nova data.
                        if (!uiState.agendaSequential) viewModel.showAgendaDate(change.data)
                    },
                )
            }
        }

        // ── Lançamento por notificações ──────────────────────────────────────
        fun openAgendaFor(receipt: com.finai.app.domain.EntryReceipt?, s: com.finai.app.data.local.entity.SugestaoLancamentoEntity) {
            if (receipt != null) {
                agendaHighlight = com.finai.app.domain.AgendaHighlight.of(receipt)
                viewModel.showAgendaDate(receipt.agendaDate)
            } else {
                // Aberta de novo depois: o vínculo gravado leva ao lançamento (ou à fatura, no cartão).
                val ids = s.lancamentoIds.split(';').mapNotNull { it.toLongOrNull() }.toSet()
                val saved = financeState.rawTransacoes.filter { it.id in ids }
                val fatura = saved.firstNotNullOfOrNull { t -> t.faturaId?.let { fid -> financeState.rawFaturasCartao.firstOrNull { it.id == fid } } }
                agendaHighlight = if (fatura != null) com.finai.app.domain.AgendaHighlight(emptySet(), fatura.contaId)
                    else com.finai.app.domain.AgendaHighlight(ids, null)
                viewModel.showAgendaDate((fatura?.vencimento ?: saved.firstOrNull()?.data ?: s.data).toLocalDate())
            }
            openSuggestionId = null
            editSuggestionId = null
            navigateTo(FinaiDestination.Agenda)
        }

        // "Editar": o mesmo formulário do "+", preenchido com o que a notificação disse. Salvar
        // grava e confirma a sugestão numa transação; fechar sem salvar deixa ela pendente.
        val suggestionPrefill = editSuggestionPrefill
        val suggestionEditId = editSuggestionId
        if (suggestionEditId != null && suggestionPrefill != null) {
            androidx.compose.runtime.key(suggestionEditId) {
                val closeSuggestionEdit = { editSuggestionId = null; editSuggestionPrefill = null }
                TransactionEntryScreen(
                    visible = true,
                    accounts = entryAccounts,
                    incomeSources = remember(financeState.rawTransacoes) {
                        com.finai.app.domain.EntryOptions.recentIncomeSources(financeState.rawTransacoes)
                    },
                    initialType = suggestionPrefill.type,
                    initialDate = suggestionPrefill.date,
                    onDismiss = closeSuggestionEdit,
                    onSave = { entry -> captureViewModel.confirmWithEntry(suggestionEditId, entry) },
                    onSaveCard = { entry, payment -> captureViewModel.confirmWithCard(suggestionEditId, entry, payment) },
                    onSaveContribution = { entry -> captureViewModel.confirmWithContribution(suggestionEditId, entry) },
                    onUndo = { receipt -> captureViewModel.undo(suggestionEditId, receipt) },
                    cards = entryCards,
                    categories = entryCategories,
                    objetivos = financeState.rawObjetivos,
                    faturas = financeState.rawFaturasCartao,
                    contas = financeState.rawContas,
                    dailySpendingCents = financeState.averageDailySpendingCents,
                    onViewEntry = { receipt ->
                        val s = captureState.suggestions.firstOrNull { it.id == suggestionEditId }
                        if (s != null) openAgendaFor(receipt, s) else closeSuggestionEdit()
                    },
                    prefill = suggestionPrefill,
                )
            }
        }

        // Quadro "Movimentação encontrada": abre sozinho só quando nada está em andamento
        // (formulário, edição, confirmação, outra camada, telas com campos); senão a sugestão
        // espera na fila com um aviso passageiro. Aberto pelo toque na notificação, abre sempre.
        val captureBlocked = homeMovement != null || homeDelete != null || uiState.chatOpen || uiState.simOpen || uiState.explainOpen || uiState.addOpen || uiState.notifsOpen ||
            celebration != null || showAddTransaction || editEntryId != null || openedInvoiceContaId != null || showInstallments || showIncome || showReconcile ||
            showAddDivida || editingDivida != null || showAddGoal || editingObjetivo != null || editingConta != null ||
            contributionTarget != null || budgetLimitTarget != null || initialSetupComplete != true ||
            editSuggestionId != null ||
            current == FinaiDestination.Inbox || current == FinaiDestination.AiSettings || current == FinaiDestination.Import
        val nextUnseenId = captureState.nextUnseen?.id
        LaunchedEffect(nextUnseenId, captureBlocked, appResumed, openSuggestionId) {
            if (nextUnseenId != null && !captureBlocked && appResumed && openSuggestionId == null) {
                kotlinx.coroutines.delay(400)
                openSuggestionId = nextUnseenId
            }
        }
        var queuedToast by remember { mutableStateOf(false) }
        var lastQueuedId by rememberSaveable { mutableStateOf<Long?>(null) }
        LaunchedEffect(nextUnseenId, captureBlocked, appResumed) {
            if (nextUnseenId != null && captureBlocked && appResumed && nextUnseenId != lastQueuedId) {
                lastQueuedId = nextUnseenId
                queuedToast = true
                kotlinx.coroutines.delay(4_000)
                queuedToast = false
            }
        }
        LaunchedEffect(openSuggestionId) { openSuggestionId?.let { captureViewModel.markShown(it) } }
        val openedSuggestionId = openSuggestionId
        if (openedSuggestionId != null && editSuggestionId == null) {
            val opened = captureState.suggestions.firstOrNull { it.id == openedSuggestionId }
            // Ainda não está na lista em memória (acabou de chegar) ou foi apagada: confere no banco.
            var gone by remember(openedSuggestionId) { mutableStateOf(false) }
            LaunchedEffect(openedSuggestionId, opened == null) {
                if (opened == null) { kotlinx.coroutines.delay(600); gone = captureViewModel.get(openedSuggestionId) == null }
            }
            val pendingNow = captureState.pending
            val next = pendingNow.firstOrNull { it.id != openedSuggestionId }
            SuggestionDialog(
                suggestion = opened,
                missing = gone,
                pendingCount = pendingNow.size,
                position = (pendingNow.indexOfFirst { it.id == openedSuggestionId } + 1).coerceAtLeast(1),
                context = com.finai.app.ui.components.capture.SuggestionContext(
                    accounts = entryAccounts, cards = entryCards, categories = entryCategories,
                    faturas = financeState.rawFaturasCartao, contas = financeState.rawContas, transacoes = financeState.rawTransacoes,
                ),
                actions = com.finai.app.ui.components.capture.SuggestionActions(
                    save = { id, draft, due -> captureViewModel.confirm(id, draft, due) },
                    ignore = { id -> captureViewModel.ignore(id) },
                    undo = { id, receipt -> captureViewModel.undo(id, receipt) },
                    edit = { s, draft ->
                        editSuggestionPrefill = prefillOf(s, draft)
                        editSuggestionId = s.id
                        openSuggestionId = null
                    },
                    restore = { id -> captureViewModel.restore(id) },
                    viewAgenda = { receipt, s -> openAgendaFor(receipt, s) },
                ),
                onLater = { openSuggestionId = null },
                onNext = next?.let { n -> { openSuggestionId = n.id } },
            )
        }

        // Novo objetivo em três passos e edição (referências 1b e 1c em uploads/design-metas).
        GoalCreateScreen(
            visible = showAddGoal,
            averageSurplusCents = financeState.goalsOverview?.averageSurplusCents,
            otherGoalsMonthlyCents = financeState.goalsOverview?.totalMonthlyCents ?: 0,
            onCreate = financeViewModel::saveObjetivo,
            // Aberta pelo lançamento: "ver objetivos" volta para o lançamento, que já mostra o novo destino.
            onViewGoals = {
                showAddGoal = false
                if (goalCreateFromEntry) goalCreateFromEntry = false else navigateTo(FinaiDestination.Goals)
                goalCreateKind = null
            },
            onDismiss = { showAddGoal = false; goalCreateFromEntry = false; goalCreateKind = null },
            initialTipo = goalCreateKind,
        )

        GoalEditScreen(
            goalId = editingObjetivo?.id,
            objetivos = financeState.rawObjetivos,
            onSave = financeViewModel::saveObjetivo,
            onDelete = financeViewModel::deleteObjetivo,
            onClose = { editingObjetivoId = null },
        )

        // Criar uma Conta nova não tem mais UI própria — "+" duplicado com o lançamento
        // confundia o usuário sobre onde cadastrar as coisas (ver planning.md §11).
        // AddContaDialog continua existindo só pra editar/excluir contas que já existiam
        // antes dessa decisão (abaixo) — nunca mais para criar uma do zero.
        editingConta?.let { conta ->
            AddContaDialog(
                initial = conta,
                onDismiss = { editingContaId = null },
                onConfirm = { nome, valor, vencimento, tipo, recorrente ->
                    financeViewModel.saveConta(
                        conta.copy(
                            nome = nome, valorCentavos = Math.round(valor * 100),
                            vencimento = vencimento.toEpochMillis(), tipo = tipo, recorrente = recorrente,
                        ),
                    )
                    editingContaId = null
                },
            )
        }

        InvoiceScreen(
            visible = openedInvoiceContaId != null,
            conta = financeState.rawContas.firstOrNull { it.id == openedInvoiceContaId },
            fatura = financeState.rawFaturasCartao.firstOrNull { it.contaId == openedInvoiceContaId },
            items = openedInvoiceContaId?.let { contaId ->
                val faturas = financeState.rawFaturasCartao.filter { it.contaId == contaId }.map { it.id }.toSet()
                financeState.rawTransacoes.filter { it.faturaId in faturas }
            }.orEmpty(),
            onTogglePaid = financeViewModel::marcarContaPaga,
            onClose = { openedInvoiceContaId = null },
        )

        InstallmentsScreen(visible = showInstallments, summary = installments, onBack = { showInstallments = false })

        homeMovement?.let { movement ->
            when (val source = movement.source) {
                is com.finai.app.domain.CycleSource.Parcela -> {
                    val p = source.parcela
                    val month = java.time.YearMonth.from(movement.date)
                    com.finai.app.ui.screens.agenda.InstallmentSheet(
                        parcela = p, month = month, today = java.time.LocalDate.now(),
                        onDismiss = { homeMovement = null },
                        onPay = { financeViewModel.pagarParcela(p.divida.id) },
                        onEditDebt = { editingDividaId = p.divida.id },
                        onSaveDate = { financeViewModel.adjustInstallmentDate(p.divida.id, month, it) },
                        onChangeDayFrom = { financeViewModel.changeInstallmentDayFrom(p.divida.id, month, it) },
                    )
                }
                is com.finai.app.domain.CycleSource.Lancamento -> {
                    val t = source.transacao
                    com.finai.app.ui.screens.agenda.TransactionSheet(
                        transacao = t, series = financeState.rawTransacoes.firstOrNull { it.id == t.id }, today = java.time.LocalDate.now(),
                        onDismiss = { homeMovement = null },
                        onEdit = { homeMovement = null; editEntryId = t.id; editEntryMonth = com.finai.app.domain.RecurringSeries.referenceMonth(t).toString() },
                        onToggleMainIncome = financeViewModel::setTransacaoRendaPrincipal,
                        onToggleExtra = financeViewModel::setTransacaoExtra,
                        onDelete = { homeDelete = it; homeMovement = null },
                        onDeleteOccurrence = { transaction, scope -> financeViewModel.deleteOccurrence(transaction, scope); homeMovement = null },
                    )
                }
                else -> Unit
            }
        }

        homeDelete?.let { transaction ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { homeDelete = null },
                title = { Text("Excluir lançamento?") },
                text = { Text(transaction.descricao) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { financeViewModel.deleteTransacao(transaction); homeDelete = null }) { Text("Excluir") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { homeDelete = null }) { Text("Cancelar") } },
            )
        }

        if (showReconcile && financeState.agendaBalance != null) {
            com.finai.app.ui.components.ReconcileSheet(
                registeredCents = financeState.agendaBalance!!.todayCents,
                accounts = entryAccounts,
                onDismiss = { showReconcile = false },
                onConfirm = { account, bankCents ->
                    financeViewModel.reconcile(account, bankCents, financeState.agendaBalance!!.todayCents)
                },
            )
        }

        com.finai.app.ui.screens.income.IncomeScreen(
            visible = showIncome,
            config = incomeConfig,
            onBack = { showIncome = false },
            onSave = financeViewModel::saveIncomeSplit,
            onAddIncome = {
                showIncome = false
                transactionEntryInitialType = com.finai.app.domain.TransactionType.Receita.name
                transactionEntryInitialMain = true
                showAddTransaction = true
            },
        )

        DebtEntryScreen(
            visible = showAddDivida || editingDivida != null,
            initial = editingDivida,
            onDismiss = { showAddDivida = false; editingDividaId = null },
            onSave = { divida ->
                financeViewModel.saveDivida(divida)
                showAddDivida = false
                editingDividaId = null
            },
        )

        // Registrar aporte (referência 1a): folha sobre a tela desfocada.
        GoalContributionSheet(
            visible = contributionTarget != null,
            objetivo = contributionObjetivo,
            dailySpendingCents = financeState.averageDailySpendingCents,
            onSave = { objetivo, cents -> financeViewModel.registrarAporte(objetivo.id, cents) },
            onUndo = { objetivo, cents ->
                financeViewModel.desfazerAporte(objetivo.id, cents)
                contributionTarget = null
            },
            onDone = {
                contributionTarget = null
                financeViewModel.releaseContributionCelebration()
            },
            onDismiss = { contributionTarget = null },
        )

        budgetLimitTarget?.let { categoria ->
            val currentLimit = financeState.budgets.firstOrNull { it.name == categoria }?.limit ?: 0.0
            BudgetLimitDialog(
                categoria = categoria,
                currentLimitReais = currentLimit,
                onDismiss = { budgetLimitTarget = null },
                onConfirm = { valor ->
                    financeViewModel.setBudgetLimit(categoria, valor)
                    budgetLimitTarget = null
                },
            )
        }

        // Voltar do sistema fecha o que está por cima, nunca a tela de trás.
        // Chat, menu do "+", simulador e avisos não têm tratador próprio:
        // sem este, o toque ia direto para o NavController, que trocava a
        // tela *atrás* da sobreposição (ou fechava o app, se já estava na
        // Início) e deixava a sobreposição aberta. Lançamento, fatura,
        // dívida e Configurações têm o próprio BackHandler.
        val overlayOpen = uiState.chatOpen || uiState.simOpen || uiState.explainOpen || uiState.addOpen || uiState.notifsOpen
        BackHandler(enabled = overlayOpen) {
            when {
                uiState.chatOpen -> viewModel.closeChat()
                uiState.simOpen -> viewModel.closeSimulator()
                uiState.explainOpen -> viewModel.closeSpendExplanation()
                uiState.addOpen -> viewModel.closeAddMenu()
                uiState.notifsOpen -> viewModel.closeNotifications()
            }
        }

        // Dicas por tela (Fase 7, item 6): na primeira visita, depois do assistente inicial, e
        // nunca com outra camada aberta (o chat tem as próprias dicas). Espera o layout
        // assentar antes de ler onde estão os componentes.
        var activeTips by remember { mutableStateOf<Pair<String, List<Tip>>?>(null) }
        val tipScreen = if (uiState.chatOpen) TipScript.CHAT else when (current) {
            FinaiDestination.Home -> TipScript.HOME
            FinaiDestination.Agenda -> TipScript.AGENDA
            FinaiDestination.Goals -> TipScript.GOALS
            FinaiDestination.Debts -> TipScript.DEBTS
            FinaiDestination.Budgets -> TipScript.BUDGETS
            else -> null
        }
        val tipsBlocked = homeMovement != null || homeDelete != null || uiState.simOpen || uiState.explainOpen || uiState.addOpen || uiState.notifsOpen ||
            celebration != null || showAddTransaction || editEntryId != null || openedInvoiceContaId != null || showInstallments || showIncome || showReconcile || showAddDivida ||
            editingDivida != null || showAddGoal || editingObjetivo != null || editingConta != null ||
            contributionTarget != null || budgetLimitTarget != null || initialSetupComplete != true ||
            openSuggestionId != null || editSuggestionId != null
        LaunchedEffect(tipScreen, tipsBlocked, tipsSeen, activeTips == null) {
            val seen = tipsSeen ?: return@LaunchedEffect
            if (activeTips != null || tipScreen == null || tipsBlocked || tipScreen in seen) return@LaunchedEffect
            kotlinx.coroutines.delay(350)
            val tips = TipScript.visibleTips(tipScreen, tipRegistry.visibleTargets(rootHeightPx), seen)
            if (tips.isNotEmpty()) activeTips = tipScreen to tips
        }
        val closeTips = {
            activeTips?.let { viewModel.markTipsSeen(it.first) }
            activeTips = null
        }
        BackHandler(enabled = activeTips != null) { closeTips() }

        // Segunda trava, independente da ordem em que os tratadores foram
        // registrados: com qualquer camada aberta por cima, a navegação entre
        // telas não reage ao voltar.
        val anyLayerOpen = homeMovement != null || homeDelete != null || overlayOpen || celebration != null || showAddTransaction || editEntryId != null || openedInvoiceContaId != null || showInstallments || showIncome || showReconcile ||
            showAddDivida || editingDivida != null || activeTips != null || initialSetupComplete == false ||
            showAddGoal || editingObjetivo != null || contributionTarget != null ||
            editSuggestionId != null
        SideEffect { navController.enableOnBackPressed(!anyLayerOpen) }

        QueuedSuggestionToast(visible = queuedToast && current != FinaiDestination.Inbox, modifier = Modifier.align(Alignment.TopCenter))

        // Meta concluída / dívida quitada: por cima de tudo, fecha com toque ou voltar.
        AnimatedVisibility(
            visible = celebration != null,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)),
        ) {
            // Guarda o último valor para a animação de saída não piscar vazia.
            var shown by remember { mutableStateOf(celebration) }
            celebration?.let { shown = it }
            shown?.let { CelebrationOverlay(it, onDismiss = financeViewModel::dismissCelebration) }
        }

        // Assistente inicial: só renda principal e próximo recebimento (Fase 7, item 6). Nada de
        // dado fictício automático (planning.md §4) — é inteiramente opcional.
        if (initialSetupComplete == false) {
            InitialSetupWizard(
                situation = financeState.situation,
                onSave = financeViewModel::saveInitialSetup,
                onFinish = viewModel::completeInitialSetup,
            )
        }

        activeTips?.let { (_, tips) -> TipOverlay(tips, tipRegistry, onDone = closeTips) }
    }
    }
}

private data class AgendaData(
    /** Movimentos do mês, em ordem: as regras do mês ([com.finai.app.domain.MonthCashFlow]) mais as transferências entre contas. */
    val movements: List<com.finai.app.domain.TimelineMovement>,
    val entraCents: Long,
    val saiCents: Long,
    val sobraCents: Long,
    val competenceShifted: Boolean,
)

/**
 * A aba do mês da Agenda (fase 4): os três números (Entra / Sai / Sobra, a mesma conta do antigo
 * "Balanço do mês" da Início) e os movimentos do mês. As regras são as de [com.finai.app.domain.MonthCashFlow],
 * via [com.finai.app.domain.CashFlowTimeline.monthMovements], as mesmas da "A seguir". A transferência
 * entre contas não mexe no saldo, mas aparece na lista para poder ser editada ou excluída.
 */
private fun agendaDataFor(
    contas: List<ContaEntity>,
    transacoes: List<TransacaoEntity>,
    dividas: List<DividaEntity>,
    monthIndex: Int,
    year: Int,
    today: java.time.LocalDate = java.time.LocalDate.now(),
): AgendaData {
    val month = java.time.YearMonth.of(year, monthIndex + 1)
    val flow = com.finai.app.domain.MonthCashFlow.of(contas, transacoes, dividas, month, today)
    return AgendaData(
        movements = com.finai.app.domain.AgendaMonth.movements(contas, transacoes, dividas, month, today),
        entraCents = flow.entraCents,
        saiCents = flow.saiCents,
        sobraCents = flow.fechamentoCents,
        competenceShifted = flow.competenceShifted,
    )
}

/**
 * Lista de uma tela secundária (Limites, Importação): nasce com a entrada do NavHost, então
 * cada visita começa do topo, e fica registrada para o cabeçalho enquanto a tela existe.
 */
@Composable
private fun rememberSecondaryList(
    destination: FinaiDestination,
    registry: MutableMap<FinaiDestination, LazyListState>,
): LazyListState {
    val list = rememberLazyListState()
    DisposableEffect(destination, list) {
        registry[destination] = list
        onDispose { if (registry[destination] === list) registry.remove(destination) }
    }
    return list
}

private val bottomTabs = setOf(FinaiDestination.Home, FinaiDestination.Agenda, FinaiDestination.Goals, FinaiDestination.Debts)

@Composable
private fun Scrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(onClick = onDismiss),
    )
}

/**
 * Aviso de falha ao gravar no banco (Fase 6). Deliberadamente discreto e
 * dispensável com um toque: é um caminho que não deve acontecer, e quando
 * acontece a informação útil é curta — "não salvou, tente de novo" —, não um
 * diálogo modal que bloqueia o app inteiro.
 */
@Composable
private fun PersistenceErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = FinaiColors.Ink,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                message,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Text("Fechar", color = FinaiColors.Emerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}


/** O que o formulário do "+" recebe ao abrir "Editar" numa movimentação encontrada. */
private fun prefillOf(
    s: com.finai.app.data.local.entity.SugestaoLancamentoEntity,
    d: com.finai.app.domain.capture.SuggestionDraft,
): com.finai.app.ui.components.EntryPrefill {
    val kind = d.kind
    val type = when {
        kind == com.finai.app.domain.capture.MovementKind.Transfer -> com.finai.app.domain.TransactionType.Transferencia
        kind.isIncome -> com.finai.app.domain.TransactionType.Receita
        else -> com.finai.app.domain.TransactionType.Gasto
    }
    val test = s.origem != com.finai.app.domain.capture.SuggestionOrigin.Notification.key
    val note = when {
        kind.reviewOnly -> " · ${kind.label.lowercase()}: confira o tipo antes de salvar"
        type == com.finai.app.domain.TransactionType.Gasto && d.payment == null -> " · confira a forma de pagamento"
        else -> ""
    }
    return com.finai.app.ui.components.EntryPrefill(
        type = type,
        cents = d.amountCents,
        category = d.category?.takeIf { type == com.finai.app.domain.TransactionType.Gasto },
        description = when (kind) {
            com.finai.app.domain.capture.MovementKind.PixSent, com.finai.app.domain.capture.MovementKind.PixReceived -> d.description
            else -> d.counterpart
        },
        account = if (type == com.finai.app.domain.TransactionType.Receita) d.account ?: d.counterpart.ifBlank { null } else d.account,
        date = d.date,
        payment = d.payment,
        card = d.card,
        installments = d.installments,
        dueDate = d.dueDate,
        banner = if (test) "Teste do FinAI · nada será gravado" else "Detectado por notificação · ${s.appNome}$note",
        demo = test,
    )
}
