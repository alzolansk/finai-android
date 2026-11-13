package com.finai.app

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.debug.DemoScenarioV2
import com.finai.app.domain.AgendaBalance
import com.finai.app.domain.AgendaMonth
import com.finai.app.domain.MonthCashFlow
import com.finai.app.ui.screens.agenda.AgendaScreen
import com.finai.app.ui.theme.FinaiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/**
 * Agenda v2 (planning-v2, fase 4) com o cenário de demonstração gravado no Room: tocar paga, tocar
 * de novo reabre, o selo aparece e o TalkBack lê "pago"; nenhuma caixinha nas duas abas; os três
 * números do mês; segurar a conta abre as outras ações.
 */
class AgendaV2Test {
    @get:Rule val compose = createComposeRule()
    private val s = DemoScenarioV2
    private val today = s.today

    private fun withDemo(sequential: Boolean, block: (FinanceRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "agenda-v2-test.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
        val repo = FinanceRepository(db)
        runBlocking {
            s.contas.forEach { db.contaDao().upsert(it) }
            s.transacoes.forEach { db.transacaoDao().upsert(it) }
            s.dividas.forEach { db.dividaDao().upsert(it) }
        }
        try {
            compose.setContent {
                val contas by repo.contas.collectAsState(emptyList())
                var seq by remember { mutableStateOf(sequential) }
                val scope = rememberCoroutineScope()
                val month = YearMonth.of(2026, 11)
                val flow = MonthCashFlow.of(contas, s.transacoes, s.dividas, month, today)
                FinaiTheme {
                    AgendaScreen(
                        listState = rememberLazyListState(), monthIndex = 10, year = 2026, onPrevMonth = {}, onNextMonth = {},
                        monthMovements = AgendaMonth.movements(contas, s.transacoes, s.dividas, month, today),
                        entraCents = flow.entraCents, saiCents = flow.saiCents, sobraCents = flow.fechamentoCents,
                        competenceShifted = flow.competenceShifted,
                        onPayInstallment = {},
                        onToggleContaPaga = { id, pago ->
                            scope.launch { contas.first { it.id == id }.let { repo.salvarConta(it.copy(status = if (pago) "pago" else "pendente")) } }
                        },
                        onDeleteTransaction = {}, onDeleteConta = {}, onEditConta = {},
                        today = today, sequential = seq, onSequentialChange = { seq = it },
                        agendaBalance = AgendaBalance.build(s.transacoes, contas, s.dividas, today),
                    )
                }
            }
            block(repo)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun internetRow() = compose.onNode(hasContentDescription("Internet", substring = true) and hasClickAction())

    private fun tapPaysAndTapAgainReopens(repo: FinanceRepository) {
        internetRow().performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { repo.contas.first().first { it.nome == "Internet" }.status == "pago" } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pago", useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        internetRow().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "pago"))
        compose.onAllNodesWithText("Pago", useUnmergedTree = true).assertCountEquals(1)
        internetRow().performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { repo.contas.first().first { it.nome == "Internet" }.status == "pendente" } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pago", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        internetRow().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "pendente"))
        compose.onAllNodesWithText("Pago", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun noCheckbox() {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun aheadTapPaysAndReopensWithoutCheckbox() = withDemo(sequential = true) { repo ->
        compose.onNodeWithText("em conta R$ 3.480", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("fica R$ 2.160", useUnmergedTree = true).performScrollTo().assertExists()
        compose.onNodeWithText("menor saldo R$ 1.520", useUnmergedTree = true).performScrollTo().assertExists()
        noCheckbox()
        tapPaysAndTapAgainReopens(repo)
        // Pagar adiantado não move a linha: o "fica" das próximas não muda.
        compose.onNodeWithText("menor saldo R$ 1.520", useUnmergedTree = true).performScrollTo().assertExists()
    }

    @Test fun monthTabShowsThreeNumbersAndGroups() = withDemo(sequential = false) { repo ->
        compose.onNodeWithText("+R$ 6.000", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("−R$ 3.360", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("R$ 2.640", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("JÁ PASSOU", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("A PARTIR DE HOJE", useUnmergedTree = true).assertExists()
        noCheckbox()
        tapPaysAndTapAgainReopens(repo)
    }

    @Test fun longPressOpensBillActions() = withDemo(sequential = false) { _ ->
        internetRow().performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Editar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Excluir", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Marcar como paga", useUnmergedTree = true).assertExists()
    }
}
