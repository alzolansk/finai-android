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
import com.finai.app.data.model.Decision
import com.finai.app.navigation.FinaiDestination
import com.finai.app.state.AppViewModel
import com.finai.app.ui.components.BuySimulatorContent
import com.finai.app.ui.components.ChatOverlay
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
 * App root: sticky top bar + the six screens behind Navigation-Compose,
 * the floating bottom nav, and the four overlays (quick actions, buy
 * simulator, notifications, chat) that can appear above any of them —
 * exactly the shape of the Claude Design prototype's single-state-object
 * component, split into a proper NavHost + shared AppViewModel.
 */
@Composable
fun FinaiApp(viewModel: AppViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
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

    fun handleDecisionAccept(decision: Decision) {
        when (decision.id) {
            "d3" -> navigateTo(FinaiDestination.Debts)
            "d2" -> navigateTo(FinaiDestination.Budgets)
            else -> viewModel.sendMessage(decision.question)
        }
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
                            surface = uiState.surface,
                            decisions = uiState.decisions,
                            showCoach = uiState.showCoach,
                            onPickSurface = viewModel::pickSurface,
                            onDismissDecision = viewModel::dismissDecision,
                            onAcceptDecision = ::handleDecisionAccept,
                            onAskDecision = { d -> viewModel.sendMessage(d.question) },
                            onOpenGoals = { navigateTo(FinaiDestination.Goals) },
                            onOpenSimulator = viewModel::openSimulator,
                            onOpenBudgets = { navigateTo(FinaiDestination.Budgets) },
                            onOpenAgenda = { navigateTo(FinaiDestination.Agenda) },
                            onOpenChat = viewModel::openChat,
                        )
                    }
                    composable(FinaiDestination.Agenda.route) {
                        AgendaScreen(
                            monthIndex = uiState.monthIndex,
                            onPrevMonth = viewModel::prevMonth,
                            onNextMonth = viewModel::nextMonth,
                        )
                    }
                    composable(FinaiDestination.Goals.route) {
                        GoalsScreen(
                            onBoost = { goal ->
                                viewModel.sendMessage("Quero ${goal.action.lowercase()} em \"${goal.name}\"")
                            },
                            onSimulate = viewModel::openChat,
                        )
                    }
                    composable(FinaiDestination.Debts.route) {
                        DebtsScreen(onRehearseCall = viewModel::openChat)
                    }
                    composable(FinaiDestination.Budgets.route) {
                        BudgetsScreen(
                            onSubscriptionAction = { sub -> viewModel.sendMessage("${sub.cta} ${sub.name}") },
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
                    when (action.title) {
                        "Lançar gasto" -> viewModel.sendMessage("Quero adicionar uma despesa")
                        "Importar fatura" -> { viewModel.closeAddMenu(); navigateTo(FinaiDestination.Import) }
                        "Posso comprar?" -> viewModel.openSimulator()
                        "Novo objetivo" -> { viewModel.closeAddMenu(); navigateTo(FinaiDestination.Goals) }
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
    }
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
