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
                val entries by repository.transacoes.collectAsState(emptyList())
                val invoices by repository.faturasCartao.collectAsState(emptyList())
                val accounts by repository.contas.collectAsState(emptyList())
                FinaiTheme {
                    TransactionEntryScreen(visible, emptyList(), onDismiss = {}, onSave = { error("Expected card") },
                        onSaveCard = repository::salvarCompraCartao, cards = listOf("Nubank"), onViewEntry = { visible = false },
                        faturas = invoices, contas = accounts)
                }
            }
            compose.onNodeWithText("6").performClick()
            compose.onNodeWithText("00").performClick()
            compose.onNodeWithText("00").performClick()
            compose.onNode(hasContentDescription("Categoria") and hasScrollAction()).performScrollToNode(hasText("Lazer"))
            compose.onNodeWithText("Lazer").performClick()
            compose.onNodeWithText("Parcelado").performScrollTo().performClick()
            // Arraste de 2× a 24×: passos de 1, anunciado como "6 parcelas de R$ 100,00".
            compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress))
                .performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(6f) }
            compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "6 parcelas de R$ 100,00"))
                .assertExists()
            compose.onNodeWithText("6× de R$ 100,00").assertExists()
            // Sem fatura guardada do cartão: o vencimento precisa ser confirmado antes de salvar.
            compose.onNodeWithText("Salvar gasto").assertIsNotEnabled()
            compose.onNodeWithText("Informe o vencimento da fatura Nubank").performScrollTo().performClick()
            val today = java.time.LocalDate.now()
            compose.onNodeWithContentDescription(today.format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM", java.util.Locale("pt", "BR"))))
                .performClick()
            compose.onNodeWithText("Salvar gasto").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Ver na agenda").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Ver na agenda").performClick()
            // A tela de Parcelamentos virou Planos › Compromissos (v2, fase 7), que lê o mesmo resumo.
            runBlocking {
                assertEquals(6, repository.faturasCartao.first().size)
                assertEquals(60000L, repository.contas.first().sumOf { it.valorCentavos })
                assertTrue(repository.transacoes.first().all { it.parcelasTotal == 6 && it.faturaId != null })
                fun summary() = runBlocking { InstallmentsSummary.of(repository.transacoes.first(), repository.faturasCartao.first(), repository.contas.first()) }
                assertEquals(6, summary().active.single().openCount)
                val account = repository.contas.first().minByOrNull { it.vencimento }!!
                repository.salvarConta(account.copy(status = "pago"))
                assertEquals(5, summary().active.single().openCount)
            }
        } finally { db.close() }
    }
}
