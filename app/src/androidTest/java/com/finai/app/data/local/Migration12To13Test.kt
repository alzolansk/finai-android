package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * `MIGRATION_12_13` acrescenta `transacoes.objetivoId` e `transacoes.contaDestino` (destino da
 * transferência e vínculo do aporte). Lançamentos existentes ficam como estavam, com os dois
 * campos nulos: transferências antigas continuam neutras. O `13.json` foi derivado do `12.json`
 * com as duas colunas (o banco passou para a v14 antes de um build na v13), com o hash de
 * identidade calculado pelo mesmo algoritmo do Room.
 */
class Migration12To13Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test fun existingEntriesKeepTheirDataAndGetNoDestination() {
        helper.createDatabase(DB, 12).apply {
            execSQL("INSERT INTO transacoes (data, descricao, valorCentavos, categoria, contaOrigem, recorrente, tipo, origem) VALUES (123, 'Mercado', 4300, 'Alimentação', 'Nubank', 0, 'Gasto', 'manual')")
            execSQL("INSERT INTO transacoes (data, descricao, valorCentavos, categoria, contaOrigem, recorrente, tipo, origem) VALUES (124, 'Para o Inter', 10000, 'Outros', 'Nubank', 1, 'Transferencia', 'manual')")
            execSQL("INSERT INTO objetivos (tipo, nome, valorAlvoCentavos, valorGuardadoCentavos, prazo, prioridade, descricao) VALUES ('Reserva', 'Reserva', 0, 150000, 0, 1, '')")
            close()
        }
        helper.runMigrationsAndValidate(DB, 13, true, FinaiDatabase.MIGRATION_12_13).apply {
            query("SELECT descricao, valorCentavos, tipo, objetivoId, contaDestino FROM transacoes ORDER BY id").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Mercado", c.getString(0)); assertEquals(4300L, c.getLong(1)); assertTrue(c.isNull(3)); assertTrue(c.isNull(4))
                assertTrue(c.moveToNext())
                assertEquals("Transferencia", c.getString(2)); assertTrue(c.isNull(3)); assertTrue(c.isNull(4))
            }
            query("SELECT valorGuardadoCentavos FROM objetivos").use { c -> assertTrue(c.moveToFirst()); assertEquals(150000L, c.getLong(0)) }
            close()
        }
        // E o Room abre o banco migrado com todas as migrações registradas.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, DB).addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try {
            val all = runBlocking { db.transacaoDao().getAll() }
            assertEquals(2, all.size)
            assertTrue(all.all { it.objetivoId == null && it.contaDestino == null })
        } finally { db.close() }
    }

    private companion object { const val DB = "migration_12_13_test.db" }
}
