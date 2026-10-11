package com.finai.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.repository.FinanceRepository
import com.finai.app.domain.EntryReceipt
import com.finai.app.domain.TransactionType
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import com.finai.app.ui.components.TransactionEntryScreen
import com.finai.app.ui.screens.agenda.AgendaScreen
import com.finai.app.ui.theme.FinaiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TransactionEntryFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expenseRoomAndAgenda() = flow(TransactionType.Gasto)
    @Test fun incomeRoomAndAgenda() = flow(TransactionType.Receita)
    @Test fun transferRoomAndAgenda() = flow(TransactionType.Transferencia)

    /** A faixa de categorias é horizontal e preguiçosa: rola até o item antes de tocar. */
    private fun pickCategory(name: String) {
        compose.onNode(hasContentDescription("Categoria") and hasScrollAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
    }

    private fun noSave(): suspend (com.finai.app.data.local.entity.TransacaoEntity) -> EntryReceipt = { error("não deveria salvar") }

    private fun flow(type: TransactionType) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "entry-test-${type.name}.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
        val repository = FinanceRepository(db)
        val saveLabel = "Salvar ${type.label.lowercase(java.util.Locale("pt", "BR"))}"
        try {
            compose.setContent {
                val entries by repository.transacoes.collectAsState(emptyList())
                var visible by remember { mutableStateOf(true) }
                FinaiTheme {
                    if (!visible) {
                        val date = entries.single().data.toLocalDate()
                        AgendaScreen(
                            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
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
                        visible = visible, accounts = listOf("Nubank", "Inter"),
                        onDismiss = {}, onSave = { repository.salvarLancamento(it) }, onViewEntry = { visible = false },
                    )
                }
            }
            compose.onNodeWithText(type.label).performClick()
            compose.onNodeWithText(saveLabel).assertIsNotEnabled()
            compose.onNodeWithText("4").performClick()
            compose.onNodeWithText("3").performClick()
            compose.onNodeWithText("00").performClick()
            compose.onNodeWithContentDescription("Valor: Quarenta e três reais").assertExists()
            compose.onNodeWithContentDescription("Apagar dígito").performClick()
            compose.onNodeWithContentDescription("Valor: Quatro reais e trinta centavos").assertExists()
            when (type) {
                TransactionType.Gasto -> {
                    // Valor > 0 sozinho não basta: falta a categoria.
                    compose.onNodeWithText(saveLabel).assertIsNotEnabled()
                    pickCategory("Alimentação"); compose.onNodeWithText("Alimentação").assertIsSelected()
                    compose.onNodeWithContentDescription("Repetição: Todo mês").performScrollTo().performClick()
                    compose.onAllNodesWithText("Todo mês").onLast().performClick()
                }
                TransactionType.Receita -> compose.onNodeWithText("Recorrente").performScrollTo().performClick().assertIsSelected()
                TransactionType.Transferencia -> {
                    // Entre contas: o destino é obrigatório e diferente da origem.
                    compose.onNodeWithText("Outra conta").performClick()
                    compose.onNodeWithText(saveLabel).assertIsNotEnabled()
                    compose.onNodeWithText("Escolher conta").performScrollTo().performClick()
                    compose.onNodeWithText("Inter").performClick()
                }
            }
            compose.onNodeWithText(saveLabel).assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Ver na agenda").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Desfazer").assertIsDisplayed()
            compose.onNodeWithText("Ver na agenda").performClick()
            compose.onNodeWithText("Agenda").assertExists()
            compose.runOnIdle {
                val saved = runBlocking { repository.transacoes.first() }.single()
                assertEquals(430L, saved.valorCentavos)
                assertEquals(type.name, saved.tipo)
                when (type) {
                    TransactionType.Gasto -> { assertEquals("Nubank", saved.contaOrigem); assertTrue(saved.recorrente); assertEquals("Alimentação", saved.categoria) }
                    TransactionType.Receita -> { assertEquals("", saved.contaOrigem); assertTrue(saved.recorrente); assertFalse(saved.rendaPrincipal || saved.extra) }
                    TransactionType.Transferencia -> { assertEquals("Nubank", saved.contaOrigem); assertEquals("Inter", saved.contaDestino); assertFalse(saved.recorrente); assertNull(saved.objetivoId) }
                }
            }
            db.close()
            db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
            val restored = runBlocking { db.transacaoDao().observeAll().first() }.single()
            assertEquals(430L, restored.valorCentavos)
            assertEquals(type.name, restored.tipo)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    /** Checklist: gasto comum em 3 toques (valor, categoria, salvar) e "Lançar outro" mantém o tipo. */
    @Test fun commonExpenseInThreeTapsAndLaunchAnotherKeepsType() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FinaiDatabase::class.java).build()
        val repository = FinanceRepository(db)
        try {
            compose.setContent { FinaiTheme {
                TransactionEntryScreen(visible = true, accounts = listOf("Nubank"), onDismiss = {},
                    onSave = { repository.salvarLancamento(it) }, onViewEntry = {})
            } }
            compose.onNodeWithText("9").performClick()
            pickCategory("Alimentação")
            compose.onNodeWithText("Salvar gasto").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Lançar outro").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Lançar outro").performClick()
            compose.onNodeWithText("Salvar gasto").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithContentDescription("Valor: Zero reais").assertExists()
            compose.onNodeWithText("7").assertIsDisplayed()
            runBlocking { assertEquals(9L, repository.transacoes.first().single().valorCentavos) }
        } finally { db.close() }
    }

    @Test fun backPreservesDraftAndZeroWithCategoryCannotSave() {
        var visible by mutableStateOf(true)
        compose.setContent { FinaiTheme {
            TransactionEntryScreen(
                visible = visible, accounts = listOf("Carteira"),
                onDismiss = { visible = false }, onSave = noSave(), onViewEntry = {},
            )
        } }
        pickCategory("Saúde")
        compose.onNodeWithText("Salvar gasto").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Valor: Zero reais").performScrollTo().performClick()
        compose.onNodeWithText("7").performClick()
        compose.onNodeWithContentDescription("Fechar lançamento").performClick()
        compose.runOnIdle { visible = true }
        compose.onNodeWithContentDescription("Valor: Sete centavos").assertExists()
        compose.onNodeWithText("Saúde").assertIsSelected()
    }

    @Test fun keypadReturnsWhenTappingAmountAfterOtherFields() {
        compose.setContent { FinaiTheme {
            TransactionEntryScreen(visible = true, accounts = listOf("Carteira"), onDismiss = {}, onSave = noSave(), onViewEntry = {})
        } }
        compose.onNodeWithText("4").performClick()
        // Categoria recolhe o teclado; tocar no valor o traz de volta.
        pickCategory("Saúde")
        compose.waitUntil(3000) { compose.onAllNodesWithText("7").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Toque para editar o valor").assertExists()
        compose.onNodeWithContentDescription("Valor: Quatro centavos").performScrollTo().performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText("7").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("7").performClick()
        compose.onNodeWithContentDescription("Valor: Quarenta e sete centavos").assertExists()
        // Descrição abre num painel; ao voltar, o teclado volta no toque do valor.
        compose.onNodeWithContentDescription("Descrição: Descrição").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasContentDescription("Descrição")).performTextInput("Mercado")
        compose.onNodeWithText("Pronto").performClick()
        compose.onNodeWithContentDescription("Descrição: Mercado").assertExists()
        compose.onNodeWithContentDescription("Valor: Quarenta e sete centavos").performScrollTo().performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText("7").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Uma reserva: vem escolhida. Guardar grava transferência + aporte juntos; Desfazer reverte os dois. */
    @Test fun singleReserveIsPreselectedAndUndoRevertsTransferAndContribution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FinaiDatabase::class.java).build()
        val repository = FinanceRepository(db)
        val goalId = runBlocking {
            repository.salvarObjetivo(ObjetivoEntity(tipo = "Reserva", nome = "Reserva de emergência", valorAlvoCentavos = 0,
                valorGuardadoCentavos = 1_000_00, prazo = LocalDate.now().plusYears(1).toEpochMillis(), prioridade = 1))
        }
        try {
            compose.setContent {
                val goals by repository.objetivos.collectAsState(emptyList())
                FinaiTheme {
                    TransactionEntryScreen(visible = true, accounts = listOf("Itaú"), onDismiss = {},
                        onSave = noSave(), onViewEntry = {}, objetivos = goals, dailySpendingCents = 50_00,
                        onSaveContribution = { repository.registrarAporte(it) }, onUndo = { repository.desfazer(it) })
                }
            }
            compose.onNodeWithText("Transferência").performClick()
            compose.onNodeWithText("Vai direto para").assertExists()
            compose.onNodeWithText("5").performClick()
            compose.onNodeWithText("00").performClick()
            compose.onNodeWithText("00").performClick()
            // Reserva contínua: saldo e cobertura, sem percentual.
            compose.onNodeWithText("R$ 1.000,00 → R$ 1.500,00").assertExists()
            compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
            compose.onNodeWithText("Guardar na reserva").assertIsEnabled().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Desfazer").fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                val t = repository.transacoes.first().single()
                assertEquals(goalId, t.objetivoId); assertEquals("Transferencia", t.tipo); assertEquals(500_00L, t.valorCentavos)
                assertEquals(1_500_00L, repository.objetivos.first().single().valorGuardadoCentavos)
            }
            compose.onNodeWithText("Desfazer").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Guardar na reserva").fetchSemanticsNodes().isNotEmpty() }
            // Volta ao formulário preenchido.
            compose.onNodeWithContentDescription("Valor: Quinhentos reais").assertExists()
            runBlocking {
                assertTrue(repository.transacoes.first().isEmpty())
                assertEquals(1_000_00L, repository.objetivos.first().single().valorGuardadoCentavos)
            }
        } finally { db.close() }
    }

    @Test fun twoReservesBlockSaveUntilChosenAndNoneOffersCreation() {
        var goals by mutableStateOf(emptyList<ObjetivoEntity>())
        var createAsked = false
        compose.setContent { FinaiTheme {
            TransactionEntryScreen(visible = true, accounts = listOf("Itaú"), onDismiss = {}, onSave = noSave(), onViewEntry = {},
                objetivos = goals, onSaveContribution = { error("não deveria salvar") }, onCreateGoal = { createAsked = it })
        } }
        compose.onNodeWithText("Transferência").performClick()
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("Criar reserva").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(createAsked) }
        val prazo = LocalDate.now().plusYears(1).toEpochMillis()
        compose.runOnIdle {
            goals = listOf(
                ObjetivoEntity(id = 1, tipo = "Reserva", nome = "Emergência", valorAlvoCentavos = 20_000_00, valorGuardadoCentavos = 12_400_00, prazo = prazo, prioridade = 1),
                ObjetivoEntity(id = 2, tipo = "Reserva", nome = "Chile", valorAlvoCentavos = 10_000_00, valorGuardadoCentavos = 3_800_00, prazo = prazo, prioridade = 2),
            )
        }
        compose.onNodeWithText("Qual reserva?").assertExists()
        compose.onNodeWithText("Guardar na reserva").assertIsNotEnabled()
        compose.onNodeWithText("Emergência").performClick()
        compose.onNodeWithText("Guardar na reserva").assertIsEnabled()
    }

    @Test fun incomeKindsAreExclusive() {
        compose.setContent { FinaiTheme {
            TransactionEntryScreen(visible = true, accounts = listOf("Carteira"), onDismiss = {}, onSave = noSave(), onViewEntry = {})
        } }
        compose.onNodeWithText("Receita").performClick()
        compose.onNodeWithText("Avulsa").assertIsSelected()
        compose.onNodeWithText("Salário").performClick().assertIsSelected()
        compose.onNodeWithText("Avulsa").assertIsNotSelected()
        compose.onNodeWithText("Extra").performClick().assertIsSelected()
        compose.onNodeWithText("Salário").assertIsNotSelected()
        // "Todo mês" só existe no gasto no débito.
        compose.onAllNodesWithContentDescription("Repetição: Todo mês").assertCountEquals(0)
    }

    @Test fun migrationPreservesExistingExpense() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "entry-migration-test.db"
        context.deleteDatabase(name)
        val helper = androidx.room.testing.MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)
        val legacy = helper.createDatabase(name, 4)
        legacy.execSQL("DROP TABLE transacoes")
        legacy.execSQL("CREATE TABLE transacoes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, data INTEGER NOT NULL, descricao TEXT NOT NULL, valorCentavos INTEGER NOT NULL, categoria TEXT NOT NULL, contaOrigem TEXT NOT NULL, recorrente INTEGER NOT NULL, origem TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO transacoes VALUES (23, 0, 'Preservar', 4300, 'Saúde', 'Carteira', 0, 'manual')")
        legacy.version = 3
        legacy.close()
        val migrated = Room.databaseBuilder(context, FinaiDatabase::class.java, name)
            .addMigrations(*FinaiDatabase.MIGRATIONS)
            .build()
        try {
            val entry = runBlocking { migrated.transacaoDao().observeAll().first() }.single()
            assertEquals(23L, entry.id)
            assertEquals(4300L, entry.valorCentavos)
            assertEquals("Preservar", entry.descricao)
            assertEquals("Gasto", entry.tipo)
            assertEquals(null, entry.faturaId)
            assertEquals(null, entry.objetivoId)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun repeatedSaveActionBeforeRecompositionPersistsOnlyOnce() {
        val completion = kotlinx.coroutines.CompletableDeferred<Unit>()
        var saves = 0
        compose.setContent { FinaiTheme {
            TransactionEntryScreen(visible = true, accounts = listOf("Carteira"), onDismiss = {},
                onSave = { saves++; completion.await(); EntryReceipt(TransactionType.Gasto, listOf(1L), LocalDate.now()) }, onViewEntry = {})
        } }
        compose.onNodeWithText("5").performClick()
        pickCategory("Saúde")
        val action = compose.onNodeWithText("Salvar gasto").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        compose.runOnIdle { action(); action() }
        compose.runOnIdle { assertEquals(1, saves); completion.complete(Unit) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Gasto salvo!").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { action(); assertEquals(1, saves) }
    }
}
