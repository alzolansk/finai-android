package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.repository.FinanceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Preserva a correção de faturas da main e as duas variantes publicadas da versão 20. */
class Migration21To22Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test fun correctsOnlyOpenInvoicesWithTheirOriginalTotal() {
        val name = "migration_21_22_invoices.db"
        helper.createDatabase(name, 21).use(::seedInvoices)
        helper.runMigrationsAndValidate(name, 22, true, FinaiDatabase.MIGRATION_21_22).use { db ->
            assertInvoices(db)
            // O reparo já aplicado pela main não pode descontar o pagamento duas vezes.
            FinaiDatabase.repairImportedInvoicePayments(db)
            assertInvoices(db)
        }
    }

    @Test fun upgradesVersion19ThroughTheFullChain() = checkUpgrade(19)

    @Test fun upgradesMainVersion20WithTheOldSchemaAndAlreadyRepairedInvoices() =
        checkUpgrade(20, mainVariant = true)

    @Test fun upgradesAssistantVersion20AndPreservesStructuredMessages() = checkUpgrade(20)

    @Test fun upgradesAssistantVersion21AndPreservesStructuredMessages() = checkUpgrade(21)

    @Test fun restoringOldBackupsRepairsInvoicesAndRestoringANewBackupIsStable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FinaiDatabase::class.java).build()
        try {
            seedInvoices(db.openHelper.writableDatabase)
            val repository = FinanceRepository(db)
            val old = repository.exportar(21, 0)
            for (version in listOf(19, 20, 21)) {
                repository.restaurar(old.copy(schemaVersion = version))
                assertInvoices(db.openHelper.writableDatabase)
            }
            val corrected = repository.exportar(22, 0)
            repository.restaurar(corrected)
            assertEquals(corrected, repository.exportar(22, 0))
        } finally { db.close() }
    }

    private fun checkUpgrade(version: Int, mainVariant: Boolean = false) {
        val name = "migration_to_22_${version}_${mainVariant}.db"
        helper.createDatabase(name, if (mainVariant) 19 else version).use { db ->
            seedInvoices(db)
            if (mainVariant) {
                // A main publicou schema 19 com user_version 20 e o reparo de dados aplicado.
                FinaiDatabase.repairImportedInvoicePayments(db)
                db.version = 20
            }
            db.execSQL("INSERT INTO mensagens_chat (id,papel,texto,timestamp,conversaId) VALUES (1,'usuario','Conversa preservada',100,7)")
            if (version >= 20 && !mainVariant) {
                db.execSQL("UPDATE mensagens_chat SET estrutura='resposta preservada' WHERE id=1")
                db.execSQL("INSERT INTO sessoes_assistente (conversaId,estado,atualizadoEm) VALUES (7,'pendente',100)")
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, name)
            .addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(22, sql.version)
            assertInvoices(sql)
            sql.query("SELECT texto, estrutura FROM mensagens_chat WHERE id=1").use {
                assertTrue(it.moveToFirst())
                assertEquals("Conversa preservada", it.getString(0))
                if (version >= 20 && !mainVariant) assertEquals("resposta preservada", it.getString(1))
                else assertTrue(it.isNull(1))
            }
            if (version >= 20 && !mainVariant) {
                sql.query("SELECT estado FROM sessoes_assistente WHERE conversaId=7").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("pendente", it.getString(0))
                }
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun seedInvoices(db: SupportSQLiteDatabase) {
        for ((id, status, total) in listOf(Triple(1, "pendente", -50411), Triple(2, "pago", -50411), Triple(3, "pendente", 12000))) {
            db.execSQL("INSERT INTO contas (id,nome,valorCentavos,vencimento,status,tipo,recorrente) VALUES (?, 'Fatura', ?, 1, ?, 'a_pagar', 0)", arrayOf(id, total, status))
            db.execSQL("INSERT INTO faturas_cartao (id,contaId,referencia,vencimento) VALUES (?, ?, 'Cartão', 1)", arrayOf(id, id))
            for ((label, amount) in listOf("Compra" to 56308, "Estorno" to -300, "Pagamento recebido" to -106419)) {
                db.execSQL("INSERT INTO transacoes (faturaId,data,descricao,valorCentavos,categoria,contaOrigem,recorrente,origem) VALUES (?, 1, ?, ?, 'Outros', 'Cartão', 0, 'importado')", arrayOf(id, label, amount))
            }
        }
    }

    private fun assertInvoices(db: SupportSQLiteDatabase) {
        db.query("SELECT valorCentavos FROM contas ORDER BY id").use { cursor ->
            assertEquals(listOf(56008, -50411, 12000), buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) })
        }
        db.query("SELECT descricao FROM transacoes WHERE faturaId = 1 ORDER BY id").use { cursor ->
            assertEquals(listOf("Compra", "Estorno"), buildList { while (cursor.moveToNext()) add(cursor.getString(0)) })
        }
    }
}
