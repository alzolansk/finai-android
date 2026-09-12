package com.finai.app

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.finai.app.data.fixtures.FinaiFixtures
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.model.Bill
import com.finai.app.data.model.Goal
import com.finai.app.data.model.Subscription
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.domain.toUiBill
import com.finai.app.navigation.FinaiDestination
import com.finai.app.state.AppViewModel
import com.finai.app.state.FinanceViewModel
import com.finai.app.ui.components.AddContaDialog
import com.finai.app.ui.components.AddDividaDialog
import com.finai.app.ui.components.AddGoalDialog
import com.finai.app.ui.components.AddTransactionDialog
import com.finai.app.ui.components.BudgetLimitDialog
import com.finai.app.ui.components.BuySimulatorContent
import com.finai.app.ui.components.ChatOverlay
import com.finai.app.ui.components.ContributionDialog
import com.finai.app.ui.components.FinaiBottomNav
import com.finai.app.ui.components.FinaiTopBar
import com.finai.app.ui.components.NotificationsCard
import com.finai.app.ui.components.QuickActionSheet
import com.finai.app.ui.screens.agenda.AgendaScreen
import com.finai.app.ui.screens.budgets.BudgetsScreen
import com.finai.app.ui.screens.debts.DebtsScreen
import com.finai.app.ui.screens.goals.GoalsScreen
import com.finai.app.ui.screens.home.HomeScreen
import com.finai.app.ui.screens.importer.ImportScreen
import com.finai.app.ui.theme.FinaiColors

/**
 * App root: sticky top bar + the six screens behind Navigation-Compose, the
 * floating bottom nav, the manual-entry dialogs, and the four overlays
 * (quick actions, buy simulator, notifications, chat) that can appear above
 * any of them. [FinanceViewModel] now owns every real, Room-backed number
 * (planning.md §9 Fase 1); [AppViewModel] keeps owning overlay-only state.
 */
@Composable
fun FinaiApp(
    viewModel: AppViewModel = viewModel(),
    financeViewModel: FinanceViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val financeState by financeViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = FinaiDestination.fromRoute(backStackEntry?.destination?.route)

    fun navigateTo(destination: FinaiDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    var showAddTransaction by remember { mutableStateOf(false) }
    var showAddGoal by remember { mutableStateOf(false) }
    var showAddConta by remember { mutableStateOf(false) }
    var showAddDivida by remember { mutableStateOf(false) }
    var contributionTarget by remember { mutableStateOf<Goal?>(null) }
    var budgetLimitTarget by remember { mutableStateOf<String?>(null) }

    val agenda = remember(financeState.rawContas, uiState.monthIndex, uiState.agendaYear) {
        agendaDataFor(financeState.rawContas, uiState.monthIndex, uiState.agendaYear)
    }

    Box(modifier = Modifier.fillMaxSize().background(FinaiColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            FinaiTopBar(
                screenLabel = current.screenLabel,
                notifCount = FinaiFixtures.notifCount,
                onOpenNotifications = viewModel::openNotifications,
                onOpenChat = viewModel::openChat,
            )
            Box(modifier = Modifier.weight(1f)) {
                NavHost(navController = navController, startDestination = FinaiDestination.Home.route) {
                    composable(FinaiDestination.Home.route) {
                        HomeScreen(
                            greeting = financeState.greeting,
                            subGreeting = financeState.subGreeting,
                            goals = financeState.goals,
                            safeToday = financeState.safeToday,
                            safeTodayLabel = financeState.safeTodayLabel,
                            safeNote = financeState.safeNote,
                            week = financeState.nextWeekBills,
                            timeline = financeState.timeline,
                            timelineNote = financeState.timelineNote,
                            showCoach = uiState.showCoach,
                            coachTitle = financeState.coachTitle,
                            coachBody = financeState.coachBody,
                            onOpenGoals = { navigateTo(FinaiDestination.Goals) },
                            onNewGoal = { showAddGoal = true },
                            onOpenSimulator = viewModel::openSimulator,
                            onOpenBudgets = { navigateTo(FinaiDestination.Budgets) },
                            onOpenAgenda = { navigateTo(FinaiDestination.Agenda) },
                            onOpenChat = viewModel::openChat,
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
                            onNewConta = { showAddConta = true },
                        )
                    }
                    composable(FinaiDestination.Goals.route) {
                        GoalsScreen(
                            goals = financeState.goals,
                            monthlyCapacityLabel = financeState.monthlyCapacityLabel,
                            onNewGoal = { showAddGoal = true },
                            onContribute = { goal -> contributionTarget = goal },
                            onDelete = { goal -> financeViewModel.deleteObjetivoById(goal.id.toLong()) },
                            onSimulate = viewModel::openChat,
                        )
                    }
                    composable(FinaiDestination.Debts.route) {
                        DebtsScreen(
                            debts = financeState.debts,
                            totalOpenLabel = financeState.debtTotalLabel,
                            monthlyInterestLabel = financeState.debtInterestLabel,
                            debtFreeLabel = financeState.debtFreeLabel,
                            strategyNote = financeState.debtStrategyNote,
                            negotiationTitle = financeState.negotiationTitle,
                            onNewDebt = { showAddDivida = true },
                            onDeleteDebt = { debt -> financeViewModel.deleteDividaById(debt.id) },
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
                            importStage = uiState.importStage,
                            onRunImport = {
                                if (uiState.importStage == 2) navigateTo(FinaiDestination.Agenda)
                                else viewModel.runImportStep()
                            },
                        )
                    }
                }
            }
        }

        FinaiBottomNav(
            current = current,
            addOpen = uiState.addOpen,
            onSelect = ::navigateTo,
            onToggleAdd = viewModel::toggleAddMenu,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .padding(bottom = 14.dp),
        )

        if (uiState.addOpen) {
            Scrim(onDismiss = viewModel::closeAddMenu)
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 104.dp),
            )
        }

        if (uiState.simOpen) {
            Scrim(onDismiss = viewModel::closeSimulator)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .sizeIn(maxHeight = 720.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = FinaiColors.Surface,
            ) {
                BuySimulatorContent(
                    amount = uiState.simAmount,
                    monthlyCapacity = financeState.monthlyCapacityCents / 100.0,
                    goals = financeState.goals,
                    onPickPreset = viewModel::setSimAmount,
                    onDecideLater = viewModel::closeSimulator,
                    onAsk = viewModel::openChat,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        if (uiState.notifsOpen) {
            Scrim(onDismiss = viewModel::closeNotifications)
            NotificationsCard(
                onClose = viewModel::closeNotifications,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            )
        }

        if (uiState.chatOpen) {
            ChatOverlay(
                messages = uiState.messages,
                thinking = uiState.thinking,
                draft = uiState.draft,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::sendDraft,
                onSuggestion = viewModel::sendMessage,
                onClose = viewModel::closeChat,
            )
        }

        if (showAddTransaction) {
            AddTransactionDialog(
                onDismiss = { showAddTransaction = false },
                onConfirm = { descricao, valor, categoria, contaOrigem, data, recorrente ->
                    financeViewModel.addTransacao(descricao, valor, categoria, contaOrigem, data, recorrente)
                    showAddTransaction = false
                },
            )
        }

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

        if (showAddConta) {
            AddContaDialog(
                onDismiss = { showAddConta = false },
                onConfirm = { nome, valor, vencimento, tipo, recorrente ->
                    financeViewModel.saveConta(
                        ContaEntity(
                            nome = nome, valorCentavos = Math.round(valor * 100),
                            vencimento = vencimento.toEpochMillis(),
                            status = "pendente", tipo = tipo, recorrente = recorrente,
                        ),
                    )
                    showAddConta = false
                },
            )
        }

        if (showAddDivida) {
            AddDividaDialog(
                onDismiss = { showAddDivida = false },
                onConfirm = { nome, valorOriginal, valorAberto, taxaPercentual, parcelasRestantes, valorParcela ->
                    financeViewModel.saveDivida(
                        DividaEntity(
                            nome = nome,
                            valorOriginalCentavos = Math.round(valorOriginal * 100),
                            valorAbertoCentavos = Math.round(valorAberto * 100),
                            taxaJurosMensalBasisPoints = Math.round(taxaPercentual * 100).toInt(),
                            parcelasRestantes = parcelasRestantes,
                            valorParcelaCentavos = Math.round(valorParcela * 100),
                        ),
                    )
                    showAddDivida = false
                },
            )
        }

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
    }
}

private data class AgendaData(val bills: List<Bill>, val toPayCents: Long, val toPayCount: Int, val toGetCents: Long, val toGetCount: Int)

private fun agendaDataFor(contas: List<ContaEntity>, monthIndex: Int, year: Int): AgendaData {
    val inMonth = contas.filter {
        val d = it.vencimento.toLocalDate()
        d.monthValue - 1 == monthIndex && d.year == year
    }
    val payable = inMonth.filter { it.tipo == "a_pagar" }.sortedBy { it.vencimento }
    val receivable = inMonth.filter { it.tipo == "a_receber" }
    return AgendaData(
        bills = payable.map { it.toUiBill() },
        toPayCents = payable.sumOf { it.valorCentavos },
        toPayCount = payable.size,
        toGetCents = receivable.sumOf { it.valorCentavos },
        toGetCount = receivable.size,
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
