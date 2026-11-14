package com.finai.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Migration19To20Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test fun correctsOnlyOpenInvoicesWithTheirOriginalTotal() {
        val name = "migration_19_20_test.db"
        helper.createDatabase(name, 19).use { db ->
            for ((id, status, total) in listOf(Triple(1, "pendente", -50411), Triple(2, "pago", -50411), Triple(3, "pendente", 12000))) {
                db.execSQL("INSERT INTO contas (id,nome,valorCentavos,vencimento,status,tipo,recorrente) VALUES (?, 'Fatura', ?, 1, ?, 'a_pagar', 0)", arrayOf(id, total, status))
                db.execSQL("INSERT INTO faturas_cartao (id,contaId,referencia,vencimento) VALUES (?, ?, 'Cartão', 1)", arrayOf(id, id))
                for ((label, amount) in listOf("Compra" to 56308, "Estorno" to -300, "Pagamento recebido" to -106419)) {
                    db.execSQL("INSERT INTO transacoes (faturaId,data,descricao,valorCentavos,categoria,contaOrigem,recorrente,origem) VALUES (?, 1, ?, ?, 'Outros', 'Cartão', 0, 'importado')", arrayOf(id, label, amount))
                }
            }
        }
        helper.runMigrationsAndValidate(name, 20, true, FinaiDatabase.MIGRATION_19_20).use { db ->
            db.query("SELECT valorCentavos FROM contas ORDER BY id").use { cursor ->
                assertEquals(listOf(56008, -50411, 12000), buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) })
            }
            db.query("SELECT descricao FROM transacoes WHERE faturaId = 1 ORDER BY id").use { cursor ->
                assertEquals(listOf("Compra", "Estorno"), buildList { while (cursor.moveToNext()) add(cursor.getString(0)) })
            }
        }
    }
}
