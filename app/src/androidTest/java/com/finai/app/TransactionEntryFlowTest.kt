package com.finai.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.TransactionType
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.TransactionEntryScreen
import com.finai.app.ui.screens.agenda.AgendaScreen
import com.finai.app.ui.theme.FinaiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TransactionEntryFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expenseRoomAndAgenda() = flow(TransactionType.Gasto)
    @Test fun incomeRoomAndAgenda() = flow(TransactionType.Receita)
    @Test fun transferRoomAndAgenda() = flow(TransactionType.Transferencia)

    private fun flow(type: TransactionType) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "entry-test-${type.name}.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
        val repository = FinanceRepository(db)
        try {
            compose.setContent {
                val entries by repository.transacoes.collectAsState(emptyList())
                var visible by remember { mutableStateOf(true) }
                FinaiTheme {
                    if (!visible) {
                        val date = entries.single().data.toLocalDate()
                        AgendaScreen(
                            monthIndex = date.monthValue - 1, year = date.year,
                            onPrevMonth = {}, onNextMonth = {}, bills = emptyList(),
                            toPayCents = 0, toPayCount = 0, toGetCents = 0, toGetCount = 0,
                            recurringTransactions = emptyList(),
                            transactions = entries, invoiceItemsByAccountId = emptyMap(),
                            debtsDue = emptyList(), onPayInstallment = {},
                            onToggleContaPaga = { _, _ -> },
                            onDeleteTransaction = {}, onDeleteConta = {}, onEditConta = {},
                        )
                    }
                    TransactionEntryScreen(
                        visible = visible, accounts = listOf("Nubank"),
                        onDismiss = {}, onSave = { repository.salvarTransacao(it) }, onViewEntry = { visible = false },
                    )
                }
            }
            compose.onNodeWithText(type.label).performClick()
            compose.onNodeWithText("Salvar lançamento").assertIsNotEnabled()
            compose.onNodeWithText("4").performClick()
            compose.onNodeWithText("3").performClick()
            compose.onNodeWithText("00").performClick()
            compose.onNodeWithContentDescription("Valor: R$ 43,00").assertExists()
            compose.onNodeWithContentDescription("Apagar").performClick()
            compose.onNodeWithContentDescription("Valor: R$ 4,30").assertExists()
            compose.onNodeWithText("Salvar lançamento").assertIsNotEnabled()
            compose.onNodeWithText("Selecionar categoria").performScrollTo().performClick()
            compose.onNodeWithText("Alimentação").performClick()
            compose.onNodeWithText("Alimentação").performClick()
            compose.onAllNodesWithText("Alimentação").filter(hasClickAction()).onLast().assertIsSelected()
            compose.onNodeWithContentDescription("Fechar").performClick()
            compose.onNodeWithContentDescription("Recorrente").performScrollTo().performClick()
            compose.onNodeWithText("Salvar lançamento").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText(type.confirmation).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Ver lançamento").performClick()
            compose.onNodeWithText("Agenda").assertExists()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Alimentação"))
            compose.onNodeWithText("Alimentação").assertIsDisplayed()
            compose.runOnIdle {
                val saved = runBlocking { repository.transacoes.first() }.single()
                assertEquals(430L, saved.valorCentavos)
                assertEquals(type.name, saved.tipo)
                assertEquals("Nubank", saved.contaOrigem)
                assertTrue(saved.recorrente)
            }
            db.close()
            db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
            val restored = runBlocking { db.transacaoDao().observeAll().first() }.single()
            assertEquals(430L, restored.valorCentavos)
            assertEquals(type.name, restored.tipo)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun backPreservesDraftAndZeroWithCategoryCannotSave() {
        var visible by mutableStateOf(true)
        compose.setContent { FinaiTheme {
            TransactionEntryScreen(
                visible = visible, accounts = listOf("Carteira"),
                onDismiss = { visible = false }, onSave = {}, onViewEntry = {},
            )
        } }
        compose.onNodeWithText("Selecionar categoria").performScrollTo().performClick()
        compose.onNodeWithText("Saúde").performClick()
        compose.onNodeWithText("Salvar lançamento").assertIsNotEnabled()
        compose.onNodeWithText("7").performClick()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.runOnIdle { visible = true }
        compose.onNodeWithContentDescription("Valor: R$ 0,07").assertExists()
        compose.onNodeWithText("Saúde").assertExists()
    }

    @Test fun keypadReturnsWhenTappingAmountAfterOtherFields() {
        compose.setContent { FinaiTheme {
            TransactionEntryScreen(visible = true, accounts = listOf("Carteira"), onDismiss = {}, onSave = {}, onViewEntry = {})
        } }
        compose.onNodeWithText("4").performClick()
        // Descrição focada (o caso do bug): o teclado próprio some...
        compose.onNodeWithContentDescription("Descrição").performClick().performTextInput("Mercado")
        compose.waitUntil(3000) { compose.onAllNodesWithText("7").fetchSemanticsNodes().isEmpty() }
        // ...e tocar no valor precisa trazê-lo de volta, e continuar digitando.
        compose.onNodeWithContentDescription("Valor: R$ 0,04").performScrollTo().performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText("7").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("7").performClick()
        compose.onNodeWithContentDescription("Valor: R$ 0,47").assertExists()
        // Mesmo caminho depois de ir para outro campo (categoria).
        compose.onNodeWithText("Selecionar categoria").performScrollTo().performClick()
        compose.onNodeWithText("Saúde").performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText("7").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Valor: R$ 0,47").performScrollTo().performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText("7").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun migrationPreservesExistingExpense() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "entry-migration-test.db"
        context.deleteDatabase(name)
        val initial = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
        initial.openHelper.writableDatabase
        initial.close()
        val legacy = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0)
        legacy.execSQL("DROP TABLE transacoes")
        legacy.execSQL("CREATE TABLE transacoes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, data INTEGER NOT NULL, descricao TEXT NOT NULL, valorCentavos INTEGER NOT NULL, categoria TEXT NOT NULL, contaOrigem TEXT NOT NULL, recorrente INTEGER NOT NULL, origem TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO transacoes VALUES (23, 0, 'Preservar', 4300, 'Saúde', 'Carteira', 0, 'manual')")
        legacy.version = 3
        legacy.close()
        val migrated = Room.databaseBuilder(context, FinaiDatabase::class.java, name)
            .addMigrations(FinaiDatabase.MIGRATION_3_4, FinaiDatabase.MIGRATION_4_5)
            .build()
        try {
            val entry = runBlocking { migrated.transacaoDao().observeAll().first() }.single()
            assertEquals(23L, entry.id)
            assertEquals(4300L, entry.valorCentavos)
            assertEquals("Preservar", entry.descricao)
            assertEquals("Gasto", entry.tipo)
            assertEquals(null, entry.faturaId)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
