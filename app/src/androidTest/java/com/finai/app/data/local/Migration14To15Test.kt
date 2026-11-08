package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `MIGRATION_14_15` (lançamento por notificação) só cria `sugestoes_lancamento` e
 * `testes_notificacao`. Nada existente muda; as tabelas novas começam vazias e o Room valida o
 * schema contra o `15.json`.
 */
class Migration14To15Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test fun existingDataIsKeptAndNewTablesStartEmpty() {
        helper.createDatabase(DB, 14).apply {
            execSQL("INSERT INTO transacoes (data, descricao, valorCentavos, categoria, contaOrigem, recorrente, tipo, origem, excecoes) VALUES (123, 'Mercado', 4290, 'Alimentação', 'Nubank', 0, 'Gasto', 'manual', '')")
            close()
        }
        helper.runMigrationsAndValidate(DB, 15, true, FinaiDatabase.MIGRATION_14_15).apply {
            query("SELECT descricao, valorCentavos FROM transacoes").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("Mercado", c.getString(0)); assertEquals(4290L, c.getLong(1))
            }
            query("SELECT COUNT(*) FROM sugestoes_lancamento").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
            query("SELECT COUNT(*) FROM testes_notificacao").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, DB).addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try {
            assertEquals(1, runBlocking { db.transacaoDao().getAll() }.size)
            assertTrue(runBlocking { db.sugestaoLancamentoDao().getReal() }.isEmpty())
        } finally { db.close() }
    }

    private companion object { const val DB = "migration_14_15_test.db" }
}
