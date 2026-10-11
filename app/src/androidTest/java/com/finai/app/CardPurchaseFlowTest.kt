package com.finai.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.ui.components.TransactionEntryScreen
import com.finai.app.domain.InstallmentsSummary
import com.finai.app.ui.screens.installments.InstallmentsAgendaCard
import com.finai.app.ui.screens.installments.InstallmentsScreen
import java.time.YearMonth
import com.finai.app.ui.theme.FinaiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CardPurchaseFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun enterInstallmentsAndSelectInvoiceDate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FinaiDatabase::class.java).build()
        val repository = FinanceRepository(db)
        try {
            compose.setContent {
                var visible by remember { mutableStateOf(true) }
                var installmentsVisible by remember { mutableStateOf(false) }
                val entries by repository.transacoes.collectAsState(emptyList())
                val invoices by repository.faturasCartao.collectAsState(emptyList())
                val accounts by repository.contas.collectAsState(emptyList())
                FinaiTheme {
                    val summary = InstallmentsSummary.of(entries, invoices, accounts)
                    if (!visible) {
                        InstallmentsAgendaCard(summary, YearMonth.now(), onOpen = { installmentsVisible = true })
                        InstallmentsScreen(installmentsVisible, summary, onBack = { installmentsVisible = false })
                    }
                    TransactionEntryScreen(visible, emptyList(), onDismiss = {}, onSave = { error("Expected card") },
                        onSaveCard = repository::salvarCompraCartao, cards = listOf("Nubank"), onViewEntry = { visible = false })
                }
            }
            compose.onNodeWithText("6").performClick()
            compose.onNodeWithText("00").performClick()
            compose.onNodeWithText("00").performClick()
            compose.onNodeWithContentDescription("Cartão de crédito").performScrollTo().performClick()
            compose.onNodeWithText("Selecionar cartão").performScrollTo().performClick()
            compose.onNodeWithText("Nubank").performClick()
            compose.onNodeWithText("Quantidade de parcelas (1 = à vista)").performScrollTo().performTextReplacement("6")
            compose.onNodeWithText("Selecionar vencimento").performScrollTo().performClick()
            compose.onNodeWithText("Confirmar").performClick()
            compose.onNodeWithText("Selecionar categoria").performScrollTo().performClick()
            compose.onNodeWithText("Lazer").performClick()
            compose.onNodeWithText("Salvar gasto").assertIsDisplayed().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Ver lançamento").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Ver lançamento").performClick()
            compose.onNodeWithText("Parcelamentos").assertExists()
            compose.onNodeWithText("Parcelamentos").performClick()
            compose.onNodeWithText("Comprometido por mês").assertExists()
            runBlocking {
                assertEquals(6, repository.faturasCartao.first().size)
                assertEquals(60000L, repository.contas.first().sumOf { it.valorCentavos })
                assertTrue(repository.transacoes.first().all { it.parcelasTotal == 6 && it.faturaId != null })
                val account = repository.contas.first().first()
                repository.salvarConta(account.copy(status = "pago"))
            }
            compose.waitUntil(5000) { compose.onAllNodesWithText("5 parcelas restantes", substring = true).fetchSemanticsNodes().isNotEmpty() }
        } finally { db.close() }
    }
}
