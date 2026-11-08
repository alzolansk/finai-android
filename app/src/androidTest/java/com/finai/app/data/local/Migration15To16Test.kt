package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `MIGRATION_15_16` (salário dividido por competência) só acrescenta colunas. Receita recorrente
 * de salário que já existe continua "Integral": competência no mês do recebimento, sem grupo nem
 * parte.
 */
class Migration15To16Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test fun existingSalaryStaysIntegral() {
        helper.createDatabase(DB, 15).apply {
            execSQL("INSERT INTO transacoes (data, descricao, valorCentavos, categoria, contaOrigem, recorrente, tipo, origem, excecoes, rendaPrincipal) VALUES (123, 'Salário', 585000, 'Salário', 'Itaú', 1, 'Receita', 'manual', '', 1)")
            close()
        }
        helper.runMigrationsAndValidate(DB, 16, true, FinaiDatabase.MIGRATION_15_16).apply {
            query("SELECT descricao, valorCentavos, competenciaDeslocamento, rendaGrupo, rendaParte, rendaDivisao FROM transacoes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Salário", c.getString(0)); assertEquals(585_000L, c.getLong(1))
                assertEquals(0, c.getInt(2)); assertTrue(c.isNull(3)); assertTrue(c.isNull(4)); assertTrue(c.isNull(5))
            }
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, DB).addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try {
            val rows = runBlocking { db.transacaoDao().getAll() }
            assertEquals(1, rows.size)
            assertEquals(0, rows.single().competenciaDeslocamento)
            assertNull(rows.single().rendaGrupo)
        } finally { db.close() }
    }

    private companion object { const val DB = "migration_15_16_test.db" }
}
