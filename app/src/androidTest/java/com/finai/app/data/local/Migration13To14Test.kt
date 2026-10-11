package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * `MIGRATION_13_14` (edição na Agenda) só acrescenta colunas: `transacoes.recorrenteAte`,
 * `diaRecorrencia`, `excecoes`, `serieId` e `dividas.ajustesVencimento`. Tudo que existia fica
 * igual, e o vazio/nulo das colunas novas é o comportamento de antes (recorrente sem fim, sem
 * exceção; parcela na data da regra).
 */
class Migration13To14Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test fun existingDataIsKeptAndNewColumnsStartEmpty() {
        helper.createDatabase(DB, 13).apply {
            execSQL("INSERT INTO transacoes (data, descricao, valorCentavos, categoria, contaOrigem, recorrente, tipo, origem) VALUES (123, 'Internet', 12000, 'Serviços', 'Nubank', 1, 'Gasto', 'manual')")
            execSQL("INSERT INTO dividas (nome, valorOriginalCentavos, valorAbertoCentavos, taxaJurosMensalBasisPoints, parcelasRestantes, valorParcelaCentavos, parcelasTotais, proximoVencimento, diaVencimento) VALUES ('Empréstimo', 600000, 300000, 200, 6, 50000, 12, 1777507200000, 30)")
            close()
        }
        helper.runMigrationsAndValidate(DB, 14, true, FinaiDatabase.MIGRATION_13_14).apply {
            query("SELECT descricao, valorCentavos, recorrente, recorrenteAte, diaRecorrencia, excecoes, serieId FROM transacoes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Internet", c.getString(0)); assertEquals(12000L, c.getLong(1)); assertEquals(1, c.getInt(2))
                assertTrue(c.isNull(3)); assertTrue(c.isNull(4)); assertEquals("", c.getString(5)); assertTrue(c.isNull(6))
            }
            query("SELECT parcelasRestantes, diaVencimento, ajustesVencimento FROM dividas").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(6, c.getInt(0)); assertEquals(30, c.getInt(1)); assertEquals("", c.getString(2))
            }
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, DB).addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try {
            val t = runBlocking { db.transacaoDao().getAll() }.single()
            assertEquals("", t.excecoes); assertNull(t.recorrenteAte); assertNull(t.serieId)
            assertEquals("", runBlocking { db.dividaDao().getAll() }.single().ajustesVencimento)
        } finally { db.close() }
    }

    private companion object { const val DB = "migration_13_14_test.db" }
}
