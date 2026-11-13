package com.finai.app

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.*
import com.finai.app.ui.screens.home.FreeCard
import com.finai.app.ui.screens.home.FreeExplainedScreen
import com.finai.app.ui.screens.home.HomeScreen
import com.finai.app.ui.theme.FinaiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeV2Test {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 8)
    private val income = TransacaoEntity(id = 2, data = today.plusDays(12).toEpochMillis(), descricao = "Salário · adiantamento", valorCentavos = 240_000, categoria = "Salário", contaOrigem = "Nubank", recorrente = true, tipo = "Receita", origem = "manual", rendaPrincipal = true, rendaParte = "adiantamento")
    private val balance = income.copy(id = 1, data = today.toEpochMillis(), tipo = ADJUSTMENT_TYPE, descricao = ADJUSTMENT_LABEL, valorCentavos = 348_000, recorrente = false, rendaPrincipal = false)
    private val bill = ContaEntity(id = 1, nome = "Internet", valorCentavos = 12_000, vencimento = today.plusDays(7).toEpochMillis(), status = "pendente", tipo = "a_pagar", recorrente = false)
    private fun plan(contas: List<ContaEntity> = listOf(bill), transactions: List<TransacaoEntity> = listOf(balance, income), margin: Long = 30_000) = FinancialPlan.build(contas, transactions, emptyList(), emptyList(), today, margin)

    @Test fun paidRowReopensAndPersistsInRoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "home-v2-test.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
        val repo = FinanceRepository(db)
        runBlocking { db.contaDao().upsert(bill) }
        try {
            compose.setContent {
                val contas by repo.contas.collectAsState(emptyList())
                val p = plan(contas)
                val scope = rememberCoroutineScope()
                FinaiTheme {
                    HomeScreen(rememberLazyListState(), emptyList(), HomeSituation.of(p.safe, p.cycle, p.free?.nextIncome), p.free, null,
                        HomeWeek.movements(contas, listOf(balance, income), emptyList(), today), null, false, "",
                        {}, {}, {}, {}, {}, {}, {}, { movement ->
                            val c = (movement.source as CycleSource.Conta).conta
                            scope.launch { repo.salvarConta(c.copy(status = if (c.status == "pago") "pendente" else "pago")) }
                        }, {}, {}, today)
                }
            }
            compose.onNode(hasContentDescription("Internet", substring = true) and hasClickAction()).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.contas.first().single().status == "pago" } }
            compose.onNodeWithText("Pago").assertExists()
            compose.onNode(hasContentDescription("Internet", substring = true) and hasClickAction())
                .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "pago"))
            compose.onNode(hasContentDescription("Internet", substring = true) and hasClickAction()).performClick()
            compose.waitUntil(10_000) { runBlocking { repo.contas.first().single().status == "pendente" } }
            // A tela recompõe depois do Room: espera o selo sumir em vez de checar no mesmo quadro.
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Pago").fetchSemanticsNodes().isEmpty() }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun shortfallReplacesFreeInsteadOfAddingAnotherMainNumber() {
        val p = plan(contas = listOf(bill.copy(valorCentavos = 400_000)))
        compose.setContent { FinaiTheme { FreeCard(HomeSituation.of(p.safe, p.cycle, p.free?.nextIncome), p.free, null, today, {}, {}, {}) } }
        compose.onNodeWithText("Faltam R$ 520").assertExists()
        compose.onNodeWithText("Livre").assertDoesNotExist()
        compose.onNodeWithText("Internet").assertExists()
    }

    @Test fun marginPersistsAndBankConfirmationUsesTypedCents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = FinaiPreferences(context)
        val previous = runBlocking { prefs.safetyMarginCents.first() }
        runBlocking { prefs.setSafetyMarginCents(30_000) }
        var bank: Long? = null
        try {
            compose.setContent {
                val margin by prefs.safetyMarginCents.collectAsState(30_000)
                val scope = rememberCoroutineScope()
                val p = plan(margin = margin)
                FinaiTheme { FreeExplainedScreen(p.free, 348_000, margin, null, listOf("Nubank"), {}, { scope.launch { prefs.setSafetyMarginCents(it) } }, { _, cents -> bank = cents }, today) }
            }
            compose.onNodeWithText("Sem", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { prefs.safetyMarginCents.first() == 0L } }
            compose.onNodeWithText("Saldo no banco agora").performScrollTo().performTextInput("348000")
            compose.onNodeWithText("Conferir", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(10_000) { bank == 348_000L }
            compose.onNodeWithText("Saldo conferido.").assertExists()
            assertEquals(0L, runBlocking { FinaiPreferences(context).safetyMarginCents.first() })
        } finally { runBlocking { prefs.setSafetyMarginCents(previous) } }
    }

    @Test fun bankAcceptsAnExplicitZeroBalance() {
        var confirmed: Long? = null
        val p = plan()
        compose.setContent { FinaiTheme {
            FreeExplainedScreen(p.free, 348_000, 30_000, null, listOf("Nubank"), {}, {}, { _, cents -> confirmed = cents }, today)
        } }
        compose.onNodeWithText("Saldo no banco agora").performScrollTo().performTextInput("0")
        compose.onNodeWithText("Conferir", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(10_000) { confirmed == 0L }
    }
}
