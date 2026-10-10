package com.finai.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Migration11To12Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)
    @Test fun preservesExistingPurchases() {
        helper.createDatabase("migration11to12", 11).apply {
            execSQL("INSERT INTO transacoes (data, descricao, valorCentavos, categoria, contaOrigem, recorrente, origem) VALUES (123, 'Ingresso', 10000, 'Lazer', 'Nubank', 0, 'manual')")
            close()
        }
        helper.runMigrationsAndValidate("migration11to12", 12, true, FinaiDatabase.MIGRATION_11_12).apply {
            query("SELECT descricao, valorCentavos, compraId, parcelaNumero, parcelasTotal FROM transacoes").use {
                assertTrue(it.moveToFirst()); assertEquals("Ingresso", it.getString(0)); assertEquals(10000L, it.getLong(1))
                assertTrue(it.isNull(2)); assertTrue(it.isNull(3)); assertTrue(it.isNull(4))
            }
            close()
        }
    }
}
