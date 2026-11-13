package com.finai.app

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.repository.InboxRepository
import com.finai.app.domain.inbox.*
import com.finai.app.domain.toEpochMillis
import com.finai.app.ui.screens.inbox.InboxScreen
import com.finai.app.ui.theme.FinaiTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class InboxV2Test {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 8)
    private val item = InboxItem("limite-Lazer-2026-11", "limite", "Lazer perto do limite", "R$ 180 de R$ 200", 18000)

    @Test fun dismissFromScreenSurvivesDatabaseReopenAndRestoreWorks() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "inbox_v2_test.db"
        ctx.deleteDatabase(name)
        var db = Room.databaseBuilder(ctx, FinaiDatabase::class.java, name).build()
        var repository = InboxRepository(db)
        runBlocking { repository.synchronize(listOf(item), emptySet(), today); repository.markSeen(setOf(item.id), today) }
        var shown by mutableStateOf(listOf(item))
        var count by mutableStateOf(0)
        compose.setContent {
            val scope = rememberCoroutineScope()
            FinaiTheme {
                InboxScreen(rememberLazyListState(), shown, count, {}, {}, {}, { value ->
                    scope.launch {
                        repository.dismiss(value, today)
                        shown = InboxRules.visible(listOf(item), db.avisoDao().getAll(), today)
                        count = 1
                    }
                }, { _, _ -> }, {
                    scope.launch {
                        repository.restoreDismissed()
                        shown = InboxRules.visible(listOf(item), db.avisoDao().getAll(), today)
                        count = 0
                    }
                }, {}, today)
            }
        }
        compose.onNodeWithText("Dispensar").performScrollTo().performClick()
        compose.waitUntil(10000) { shown.isEmpty() }
        compose.onNodeWithText("Tudo em dia. Nenhuma decisão pendente.").assertExists()
        db.close()
        db = Room.databaseBuilder(ctx, FinaiDatabase::class.java, name).build()
        repository = InboxRepository(db)
        try {
            runBlocking {
                repository.synchronize(listOf(item.copy(title = "Título atualizado")), emptySet(), today.plusDays(1))
                val states = db.avisoDao().getAll()
                assertEquals("dispensado", states.first { it.id == item.id }.estado)
                assertFalse(InboxRules.isNew(item, states))
                assertTrue(InboxRules.visible(listOf(item), states, today.plusDays(1)).isEmpty())
            }
            compose.onNodeWithText("Lazer perto do limite").assertDoesNotExist()
            compose.onNodeWithText("Desfazer tudo").performScrollTo().performClick()
            compose.waitUntil(10000) { shown.isNotEmpty() }
            compose.onNodeWithText("Lazer perto do limite").assertExists()
        } finally { db.close(); ctx.deleteDatabase(name) }
    }

    @Test fun legacyDismissalMigratesOnceAndSnoozeReturnsOnDate() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, FinaiDatabase::class.java).build()
        val repo = InboxRepository(db)
        val meta = item.copy(id = "meta-conflito", kind = "meta")
        try {
            repo.synchronize(listOf(meta), setOf("conflito|Metas pedem R$ 1.625"), today)
            assertTrue(InboxRules.visible(listOf(meta), db.avisoDao().getAll(), today).isEmpty())
            repo.restoreDismissed()
            repo.synchronize(listOf(meta), setOf("conflito|Metas pedem R$ 1.625"), today)
            assertEquals(1, InboxRules.visible(listOf(meta), db.avisoDao().getAll(), today).size)
            repo.markSeen(setOf(meta.id), today)
            assertFalse(InboxRules.isNew(meta, db.avisoDao().getAll()))
            repo.snooze(meta, today.plusDays(2), today)
            repo.synchronize(listOf(meta), emptySet(), today.plusDays(1))
            assertTrue(InboxRules.visible(listOf(meta), db.avisoDao().getAll(), today.plusDays(1)).isEmpty())
            repo.synchronize(listOf(meta), emptySet(), today.plusDays(2))
            assertEquals("novo", db.avisoDao().getAll().first { it.id == meta.id }.estado)
        } finally { db.close() }
    }

    @Test fun payingResolvesAndExplicitReopeningCreatesANewNotice() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, FinaiDatabase::class.java).build()
        try {
            val finance = com.finai.app.data.repository.FinanceRepository(db)
            val bill = com.finai.app.data.local.entity.ContaEntity(1, "Fatura", 132000, today.toEpochMillis(), "pendente", "a_pagar", false)
            db.contaDao().upsert(bill)
            db.avisoDao().upsert(com.finai.app.data.local.entity.AvisoEntity("conta-1-2026-11", "conta:0", "visto", 132000, vistoEm = today.toString()))
            finance.salvarConta(bill.copy(status = "pago"))
            assertEquals("resolvido", db.avisoDao().getAll().single().estado)
            finance.salvarConta(bill)
            assertEquals("novo", db.avisoDao().getAll().single().estado)
            assertNull(db.avisoDao().getAll().single().vistoEm)
        } finally { db.close() }
    }
}
