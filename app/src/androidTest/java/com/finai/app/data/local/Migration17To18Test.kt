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
 * `MIGRATION_17_18` acrescenta `contas.pagoEm` (dia em que a conta foi paga). Conta paga antes da
 * migração continua paga, sem o dia (o histórico de pagamentos mostra "dia não registrado").
 */
class Migration17To18Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test fun paidBillKeepsStatusWithoutPaymentDay() {
        helper.createDatabase(DB, 17).apply {
            execSQL("INSERT INTO contas (nome, valorCentavos, vencimento, status, tipo, recorrente) VALUES ('Fatura', 80000, 123, 'pago', 'a_pagar', 0)")
            close()
        }
        helper.runMigrationsAndValidate(DB, 18, true, FinaiDatabase.MIGRATION_17_18).apply {
            query("SELECT status, pagoEm FROM contas").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("pago", c.getString(0)); assertTrue(c.isNull(1))
            }
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, DB).addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try {
            val conta = runBlocking { db.contaDao().getAll() }.single()
            assertEquals("pago", conta.status)
            assertNull(conta.pagoEm)
        } finally { db.close() }
    }

    private companion object { const val DB = "migration_17_18_test.db" }
}
