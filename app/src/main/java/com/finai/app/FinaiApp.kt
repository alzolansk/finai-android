package com.finai.app

import androidx.compose.animation.AnimatedVisibility
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
import com.finai.app.ui.components.AddGoalDialog
import com.finai.app.ui.components.TransactionEntryScreen
import com.finai.app.domain.transactionsInMonth
import androidx.compose.runtime.saveable.rememberSaveable
import com.finai.app.ui.components.SettingsScreen
import com.finai.app.ui.components.rememberOnResume
import com.finai.app.ui.components.BudgetLimitDialog
import com.finai.app.ui.components.BuySimulatorContent
import com.finai.app.ui.components.ChatOverlay
import com.finai.app.ui.components.ContributionDialog
import com.finai.app.ui.components.FinaiBottomNav
import com.finai.app.ui.components.FinaiTopBar
import com.finai.app.ui.components.InitialSetupWizard
import com.finai.app.ui.components.NotificationsPanel
import com.finai.app.ui.components.TopBarContentHeight
import com.finai.app.ui.components.OnboardingTour
import com.finai.app.ui.components.QuickActionSheet
import com.finai.app.ui.screens.agenda.AgendaScreen
import com.finai.app.ui.screens.budgets.BudgetsScreen
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
 * insight, purchase verdict, debt negotiation, home decisions — Fase 2).
 * This composable only wires "ensure this AI text" calls to the screens that
 * need them via `LaunchedEffect`; it holds no AI logic itself.
 */
@Composable
fun FinaiApp(
    viewModel: AppViewModel = viewModel(),
    financeViewModel: FinanceViewModel = viewModel(),
    aiViewModel: AiViewModel = viewModel(),
    importViewModel: ImportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val financeState by financeViewModel.uiState.collectAsState()
    val configuredAiProviders by aiViewModel.configuredProviders.collectAsState()
    val goalInsights by aiViewModel.goalInsights.collectAsState()
    val purchaseVerdict by aiViewModel.purchaseVerdict.collectAsState()
    val debtNegotiation by aiViewModel.debtNegotiation.collectAsState()
    val decisions by aiViewModel.decisions.collectAsState()
    val coachInsight by aiViewModel.coachInsight.collectAsState()
    val importState by importViewModel.uiState.collectAsState()
    val persistenceError by financeViewModel.persistenceError.collectAsState()
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()
    val initialSetupComplete by viewModel.initialSetupComplete.collectAsState()
    val knownAccounts by viewModel.knownAccounts.collectAsState()
    val financeSummary = remember(financeState) { financeState.toAiSummaryText() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = FinaiDestination.fromRoute(backStackEntry?.destination?.route)
    // Tab switches and overlays share one fade duration; `reducedMotion` collapses it to
    // 0ms so "Remove animations" is honored the same way finaiTween() honors it elsewhere,
    // since NavHost's transition lambdas run outside a @Composable scope.
    val reducedMotion = rememberReducedMotion()
    val tabFadeMillis = if (reducedMotion) 0 else FinaiMotion.Quick

    fun navigateTo(destination: FinaiDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    var showAddTransaction by rememberSaveable { mutableStateOf(false) }
    var bellCenterX by remember { mutableStateOf<Float?>(null) }
    // Único ponto de entrada pra lançar algo novo (planning.md §11: "Conta" deixou de ter
    // um "+" próprio — tudo que o usuário cadastra passa por aqui, e é isso que faz o
    // resumo da Agenda refletir o que foi lançado). initialType só é aplicado se a tela
    // abrir com o rascunho vazio (ver TransactionEntryScreen), pra não sobrescrever um
    // rascunho em andamento.
    var transactionEntryInitialType by rememberSaveable { mutableStateOf(com.finai.app.domain.TransactionType.Gasto.name) }
    var transactionEntryInitialExtra by rememberSaveable { mutableStateOf(false) }
    var showAddGoal by remember { mutableStateOf(false) }
    var showAddDivida by remember { mutableStateOf(false) }
    var contributionTarget by remember { mutableStateOf<Goal?>(null) }
    var budgetLimitTarget by remember { mutableStateOf<String?>(null) }
    // Edição de itens já cadastrados — o mesmo diálogo de "Novo X" pré-preenchido
    // via `initial`, mantendo id (e, no caso de conta, o status pago/pendente)
    // intactos ao salvar (ver EntryDialogs.kt).
    var editingContaId by remember { mutableStateOf<Long?>(null) }
    var editingDividaId by remember { mutableStateOf<Long?>(null) }
    var openedInvoiceContaId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingObjetivoId by remember { mutableStateOf<Long?>(null) }
    val editingConta = financeState.rawContas.firstOrNull { it.id == editingContaId }
    val editingDivida = financeState.rawDividas.firstOrNull { it.id == editingDividaId }
    val editingObjetivo = financeState.rawObjetivos.firstOrNull { it.id == editingObjetivoId }

    val agenda = remember(financeState.rawContas, financeState.rawFaturasCartao, financeState.rawTransacoes, financeState.rawDividas, uiState.monthIndex, uiState.agendaYear) {
        agendaDataFor(financeState.rawContas, financeState.rawFaturasCartao, financeState.rawTransacoes, financeState.rawDividas, uiState.monthIndex, uiState.agendaYear)
    }

    Box(modifier = Modifier.fillMaxSize().background(FinaiColors.Background)) {
        Column(modifier = Modifier.fillMaxSize().then(if (showAddTransaction) Modifier.clearAndSetSemantics {} else Modifier)) {
            // Configurações desenha a própria topbar: o título e o "voltar"
            // acompanham a subpágina aberta dentro dela.
            if (current != FinaiDestination.AiSettings) {
                FinaiTopBar(
                    screenLabel = current.screenLabel,
                    notifCount = financeState.alerts.size,
                    onOpenNotifications = viewModel::openNotifications,
                    onOpenChat = viewModel::openChat,
                    onOpenAiSettings = { navController.navigate(FinaiDestination.AiSettings.route) { launchSingleTop = true } },
                    onBellCenterX = { bellCenterX = it },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
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
                        LaunchedEffect(financeState.debts, financeState.budgets, financeState.subscriptions, financeState.goals, financeState.safeNote) {
                            aiViewModel.ensureDecisions(
                                topDebt = financeState.debts.firstOrNull(),
                                budgets = financeState.budgets,
                                subscriptions = financeState.subscriptions,
                                goals = financeState.goals,
                                safeNote = financeState.safeNote,
                            )
                        }
                        LaunchedEffect(financeState.behaviorPattern) {
                            aiViewModel.ensureCoachInsight(financeState.behaviorPattern)
                        }
                        HomeScreen(
                            greeting = financeState.greeting,
                            subGreeting = financeState.subGreeting,
                            goals = financeState.goals,
                            saldoLabel = financeState.saldoLabel,
                            saldoPositivo = financeState.saldoCents >= 0,
                            payCycle = financeState.payCycle,
                            safeToday = financeState.safeToday,
                            safeTodayLabel = financeState.safeTodayLabel,
                            safeNote = financeState.safeNote,
                            week = financeState.nextWeekBills,
                            timeline = financeState.timeline,
                            timelineNote = financeState.timelineNote,
                            showCoach = uiState.showCoach,
                            behaviorPattern = financeState.behaviorPattern,
                            coachInsight = coachInsight,
                            decisions = decisions,
                            onOpenGoals = { navigateTo(FinaiDestination.Goals) },
                            onNewGoal = { showAddGoal = true },
                            onOpenSimulator = viewModel::openSimulator,
                            onOpenBudgets = { navigateTo(FinaiDestination.Budgets) },
                            onOpenAgenda = { navigateTo(FinaiDestination.Agenda) },
                            onOpenChat = viewModel::openChat,
                            onNewIncomeEntry = { transactionEntryInitialType = com.finai.app.domain.TransactionType.Receita.name; transactionEntryInitialExtra = true; showAddTransaction = true },
                        )
                    }
                    composable(FinaiDestination.Agenda.route) {
                        AgendaScreen(
                            monthIndex = uiState.monthIndex,
                            year = uiState.agendaYear,
                            onPrevMonth = viewModel::prevMonth,
                            onNextMonth = viewModel::nextMonth,
                            bills = agenda.bills,
                            toPayCents = agenda.toPayCents,
                            toPayCount = agenda.toPayCount,
                            toGetCents = agenda.toGetCents,
                            toGetCount = agenda.toGetCount,
                            recurringTransactions = agenda.recurringTransactions,
                            transactions = agenda.transactions,
                            invoiceItemsByAccountId = agenda.invoiceItemsByAccountId,
                            debtsDue = agenda.debtsDue,
                            onPayInstallment = financeViewModel::pagarParcela,
                            onToggleContaPaga = financeViewModel::marcarContaPaga,
                            onDeleteTransaction = financeViewModel::deleteTransacao,
                            onDeleteConta = { bill ->
                                financeState.rawContas.firstOrNull { it.id == bill.id }?.let(financeViewModel::deleteConta)
                            },
                            onEditConta = { bill -> editingContaId = bill.id },
                            onOpenInvoice = { contaId -> openedInvoiceContaId = contaId },
                            onToggleExtra = financeViewModel::setTransacaoExtra,
                            onEditDebt = { id -> editingDividaId = id },
                        )
                    }
                    composable(FinaiDestination.Goals.route) {
                        LaunchedEffect(financeState.goals, financeState.monthlyCapacityLabel) {
                            financeState.goals.forEach { goal ->
                                aiViewModel.ensureGoalInsight(goal, financeState.monthlyCapacityLabel, financeState.goals.size - 1)
                            }
                        }
                        GoalsScreen(
                            goals = financeState.goals,
                            monthlyCapacityLabel = financeState.monthlyCapacityLabel,
                            goalInsights = goalInsights,
                            onNewGoal = { showAddGoal = true },
                            onContribute = { goal -> contributionTarget = goal },
                            onEdit = { goal -> editingObjetivoId = goal.id.toLong() },
                            onDelete = { goal -> financeViewModel.deleteObjetivoById(goal.id.toLong()) },
                            onSimulate = viewModel::openChat,
                        )
                    }
                    composable(FinaiDestination.Debts.route) {
                        LaunchedEffect(financeState.debts) {
                            aiViewModel.ensureDebtNegotiation(financeState.debts)
                        }
                        DebtsScreen(
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
                            onRehearseCall = viewModel::openChat,
                        )
                    }
                    composable(FinaiDestination.Budgets.route) {
                        BudgetsScreen(
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
                        SettingsScreen(
                            onExit = { navController.navigateUp() },
                            configuredProviders = configuredAiProviders,
                            exhaustedProviders = exhaustedAiProviders,
                            connectionTests = aiConnectionTests,
                            onSaveKey = aiViewModel::setProviderKey,
                            onClearKey = aiViewModel::clearProviderKey,
                            onTestProvider = aiViewModel::testProvider,
                            notificationsEnabled = rememberOnResume { FinaiNotifier(context).hasNotificationPermission() },
                            onRunNotificationCheck = { FinanceCheckWorker.runOnce(context) },
                            onRestartTour = viewModel::restartOnboarding,
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
                        )
                    }
                }
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
            )
        }

        if (current != FinaiDestination.AiSettings) {
            FinaiBottomNav(
                current = current,
                addOpen = uiState.addOpen,
                onSelect = ::navigateTo,
                onToggleAdd = viewModel::toggleAddMenu,
                modifier = Modifier
                    .then(if (showAddTransaction) Modifier.clearAndSetSemantics {} else Modifier)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 14.dp),
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
                    monthlyCapacity = financeState.monthlyCapacityCents / 100.0,
                    goals = financeState.goals,
                    aiExplain = purchaseVerdict,
                    onEnsureExplain = { amountLabel, verdictLabel, capacityLabel, slackLabel, topGoalName, topGoalAffected ->
                        aiViewModel.ensurePurchaseVerdict(amountLabel, verdictLabel, capacityLabel, slackLabel, topGoalName, topGoalAffected)
                    },
                    onPickPreset = viewModel::setSimAmount,
                    onDecideLater = viewModel::closeSimulator,
                    onAsk = viewModel::openChat,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.notifsOpen,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)),
        ) {
            Scrim(onDismiss = viewModel::closeNotifications)
        }
        // Painel de avisos: fica logo abaixo da topbar (nunca sob a barra de
        // status) e cresce a partir do sino, com a seta apontando para ele.
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val panelMargin = 12.dp
        val panelWidthPx = with(density) { (configuration.screenWidthDp.dp - panelMargin * 2).toPx() }
        val caretX = bellCenterX?.let { with(density) { (it - panelMargin.toPx()).toDp() } }
        val panelOrigin = TransformOrigin(
            pivotFractionX = bellCenterX?.let { ((it - with(density) { panelMargin.toPx() }) / panelWidthPx).coerceIn(0f, 1f) } ?: 0.6f,
            pivotFractionY = 0f,
        )
        BackHandler(enabled = uiState.notifsOpen) { viewModel.closeNotifications() }
        AnimatedVisibility(
            visible = uiState.notifsOpen,
            enter = fadeIn(finaiTween(FinaiMotion.Standard)) +
                scaleIn(finaiTween(FinaiMotion.Standard), initialScale = 0.9f, transformOrigin = panelOrigin),
            exit = fadeOut(finaiTween(FinaiMotion.Quick)) +
                scaleOut(finaiTween(FinaiMotion.Quick), targetScale = 0.95f, transformOrigin = panelOrigin),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = TopBarContentHeight - 6.dp, start = panelMargin, end = panelMargin),
        ) {
            NotificationsPanel(
                alerts = financeState.alerts,
                caretX = caretX,
                maxListHeight = (configuration.screenHeightDp * 0.6f).dp,
                onClose = viewModel::closeNotifications,
                onOpenAlert = { alert ->
                    viewModel.closeNotifications()
                    navigateTo(
                        when (alert.kind) {
                            AlertKind.Bill, AlertKind.Income -> FinaiDestination.Agenda
                            AlertKind.Budget, AlertKind.Subscription -> FinaiDestination.Budgets
                            AlertKind.Goal -> FinaiDestination.Goals
                        },
                    )
                },
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
                onDraftChange = viewModel::onDraftChange,
                onSend = { viewModel.sendDraft(financeSummary) },
                onSuggestion = { text -> viewModel.sendMessage(text, financeSummary) },
                onClose = viewModel::closeChat,
            )
        }

        TransactionEntryScreen(
            visible = showAddTransaction,
            // Only gasto/transferência origins feed the "conta/cartão" picker — a receita's
            // origem (ex.: nome da empresa) não é uma conta e não deve virar opção permanente
            // aqui (ver nota em TransactionEntryScreen sobre incomeSources).
            accounts = (financeState.rawTransacoes.filter { it.tipo != com.finai.app.domain.TransactionType.Receita.name }
                .map { it.contaOrigem } + knownAccounts).distinct(),
            incomeSources = financeState.rawTransacoes.filter { it.tipo == com.finai.app.domain.TransactionType.Receita.name }
                .map { it.contaOrigem }.distinct(),
            initialType = com.finai.app.domain.TransactionType.valueOf(transactionEntryInitialType),
            initialExtra = transactionEntryInitialExtra,
            onDismiss = { showAddTransaction = false; transactionEntryInitialType = com.finai.app.domain.TransactionType.Gasto.name; transactionEntryInitialExtra = false },
            onSave = financeViewModel::saveTransactionEntry,
            onViewEntry = { date ->
                viewModel.showAgendaDate(date)
                showAddTransaction = false
                transactionEntryInitialType = com.finai.app.domain.TransactionType.Gasto.name
                transactionEntryInitialExtra = false
                navigateTo(FinaiDestination.Agenda)
            },
        )

        if (showAddGoal) {
            AddGoalDialog(
                onDismiss = { showAddGoal = false },
                onConfirm = { nome, tipo, valorAlvo, valorGuardado, prazo, prioridade ->
                    financeViewModel.saveObjetivo(
                        ObjetivoEntity(
                            tipo = tipo, nome = nome,
                            valorAlvoCentavos = Math.round(valorAlvo * 100),
                            valorGuardadoCentavos = Math.round(valorGuardado * 100),
                            prazo = prazo.toEpochMillis(),
                            prioridade = prioridade,
                        ),
                    )
                    showAddGoal = false
                },
            )
        }

        editingObjetivo?.let { objetivo ->
            AddGoalDialog(
                initial = objetivo,
                onDismiss = { editingObjetivoId = null },
                onConfirm = { nome, tipo, valorAlvo, valorGuardado, prazo, prioridade ->
                    financeViewModel.saveObjetivo(
                        objetivo.copy(
                            tipo = tipo, nome = nome,
                            valorAlvoCentavos = Math.round(valorAlvo * 100),
                            valorGuardadoCentavos = Math.round(valorGuardado * 100),
                            prazo = prazo.toEpochMillis(),
                            prioridade = prioridade,
                        ),
                    )
                    editingObjetivoId = null
                },
            )
        }

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
            items = openedInvoiceContaId?.let { agenda.invoiceItemsByAccountId[it] }.orEmpty(),
            onTogglePaid = financeViewModel::marcarContaPaga,
            onClose = { openedInvoiceContaId = null },
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

        contributionTarget?.let { goal ->
            ContributionDialog(
                goalName = goal.name,
                onDismiss = { contributionTarget = null },
                onConfirm = { valor ->
                    financeViewModel.contribuirParaObjetivo(goal.id.toLong(), valor)
                    contributionTarget = null
                },
            )
        }

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

        // `null` = DataStore ainda não respondeu; não desenha nada para não
        // piscar o tour por um frame numa instalação que já o concluiu. A
        // configuração inicial de dados só aparece depois do tour concluído/
        // pulado, nunca antes nem em paralelo (planning.md §4: nada de dado
        // fictício automático — este assistente é a única forma dos números
        // do app começarem preenchidos, e é inteiramente opcional).
        if (onboardingComplete == false) {
            OnboardingTour(
                onFinish = viewModel::completeOnboarding,
                onSkip = viewModel::completeOnboarding,
            )
        } else if (onboardingComplete == true && initialSetupComplete == false) {
            InitialSetupWizard(
                onAddConta = financeViewModel::saveConta,
                onAddDivida = financeViewModel::saveDivida,
                onAddObjetivo = financeViewModel::saveObjetivo,
                onAddAccounts = viewModel::addKnownAccounts,
                onFinish = viewModel::completeInitialSetup,
            )
        }
    }
}

private data class AgendaData(
    val bills: List<Bill>,
    val toPayCents: Long,
    val toPayCount: Int,
    val toGetCents: Long,
    val toGetCount: Int,
    val recurringTransactions: List<TransacaoEntity>,
    val transactions: List<TransacaoEntity>,
    val invoiceItemsByAccountId: Map<Long, List<TransacaoEntity>>,
    val debtsDue: List<com.finai.app.domain.DebtInstallment>,
)

/**
 * Decisão (26/09/2026, ver planning.md §11): "Contas a pagar"/"Recebimentos" somam
 * [ContaEntity] (a_pagar/a_receber), os lançamentos (Gasto/Receita) do mês e a parcela
 * de cada dívida que vence no mês exibido ([debtsDue], projetadas até a última) —
 * a versão anterior só contava Conta, e um usuário que lança tudo pelo "+" central
 * (o único jeito de cadastrar algo hoje, já que o "+" próprio da Agenda foi removido)
 * via o resumo sempre zerado mesmo tendo cadastrado o mês inteiro. Transferência não
 * entra em nenhum dos dois lados (é neutra, planning.md §8).
 * [bills] lista as duas pontas (a_pagar e a_receber) de Conta — antes só mostrava
 * a_pagar como linha, e uma renda recorrente cadastrada no onboarding ficava sem
 * nenhuma linha própria, só somada no total.
 * [recurringTransactions] separa da lista de lançamentos avulsos os gastos/
 * receitas que o usuário marcou como recorrentes ao lançar manualmente —
 * antes eles caíam em "Lançamentos deste mês" junto com gastos avulsos e
 * importados, longe de onde as contas e a renda recorrentes do onboarding
 * aparecem, o que confundia "isso se repete todo mês" com "isso foi um gasto
 * único". [transactions] continua só com o que não se repete.
 */
private fun agendaDataFor(
    contas: List<ContaEntity>,
    faturasCartao: List<com.finai.app.data.local.entity.FaturaCartaoEntity>,
    transacoes: List<TransacaoEntity>,
    dividas: List<DividaEntity>,
    monthIndex: Int,
    year: Int,
    today: java.time.LocalDate = java.time.LocalDate.now(),
): AgendaData {
    val month = java.time.YearMonth.of(year, monthIndex + 1)
    // Mesma conta do "Saldo atual" da Início (MonthCashFlow) — os dois não podem divergir.
    val flow = com.finai.app.domain.MonthCashFlow.of(contas, transacoes, dividas, month, today)
    val faturaPorId = faturasCartao.associateBy { it.id }
    val invoiceItemsByAccountId = transacoes.filter { it.faturaId != null }
        .groupBy { faturaPorId[it.faturaId]?.contaId }
        .filterKeys { it != null }
        .mapKeys { it.key!! }
    return AgendaData(
        bills = (flow.payable + flow.receivable).sortedBy { it.vencimento }.map { it.toUiBill(today) },
        toPayCents = flow.toPayCents,
        toPayCount = flow.toPayCount,
        toGetCents = flow.toGetCents,
        toGetCount = flow.toGetCount,
        recurringTransactions = flow.recorrentes,
        transactions = flow.avulsas,
        invoiceItemsByAccountId = invoiceItemsByAccountId,
        debtsDue = flow.debtsDue,
    )
}

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
