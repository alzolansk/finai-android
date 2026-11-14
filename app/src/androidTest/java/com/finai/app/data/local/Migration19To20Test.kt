package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.entity.AcaoAssistenteEntity
import com.finai.app.data.local.entity.AcompanhamentoEntity
import com.finai.app.data.local.entity.PlanoEntity
import com.finai.app.data.local.entity.SessaoAssistenteEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 19 → 20: estrutura das respostas do chat, sessões, recibos de ação, acompanhamentos e planos aceitos. */
class Migration19To20Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test fun keepsTheChatAndFinancialDataAndCreatesTheAssistantTables() {
        val name = "migration_19_20_test.db"
        helper.createDatabase(name, 19).apply {
            execSQL("INSERT INTO mensagens_chat (id,papel,texto,timestamp,conversaId) VALUES (1,'usuario','Quanto gastei?',100,7)")
            execSQL("INSERT INTO contas (id,nome,valorCentavos,vencimento,status,tipo,recorrente,pagoEm) VALUES (42,'Fatura',132000,123,'pago','a_pagar',0,456)")
            close()
        }
        helper.runMigrationsAndValidate(name, 20, true, FinaiDatabase.MIGRATION_19_20).apply {
            query("SELECT texto, estrutura FROM mensagens_chat WHERE id=1").use {
                assertTrue(it.moveToFirst())
                assertEquals("Quanto gastei?", it.getString(0))
                assertTrue("mensagem antiga fica sem cartão", it.isNull(1))
            }
            query("SELECT valorCentavos, pagoEm FROM contas WHERE id=42").use {
                assertTrue(it.moveToFirst()); assertEquals(132_000, it.getLong(0)); assertEquals(456, it.getLong(1))
            }
            close()
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(ctx, FinaiDatabase::class.java, name).addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try {
            runBlocking {
                db.sessaoAssistenteDao().upsert(SessaoAssistenteEntity(7, "{}", 1))
                assertEquals("{}", db.sessaoAssistenteDao().get(7)!!.estado)
                val id = db.acaoAssistenteDao().insert(AcaoAssistenteEntity(chave = "7:1:pagar_conta", conversaId = 7, tipo = "pagar_conta", resumo = "r", recibo = "{}", estado = "applied", criadoEm = 1))
                assertEquals(id, db.acaoAssistenteDao().byKey("7:1:pagar_conta")!!.id)
                assertEquals(id, db.acaoAssistenteDao().lastApplied(7)!!.id)
                // A chave de idempotência é única: o mesmo pedido não grava duas linhas.
                val duplicated = runCatching { db.acaoAssistenteDao().insert(AcaoAssistenteEntity(chave = "7:1:pagar_conta", conversaId = 7, tipo = "x", resumo = "", recibo = "{}", estado = "applied", criadoEm = 2)) }
                assertTrue(duplicated.isFailure)
                db.acompanhamentoDao().upsert(AcompanhamentoEntity(tipo = "quitacao", alvo = "Picpay", valorCentavos = 0, descricao = "Avisar", estado = "ativo", criadoEm = 1))
                assertEquals(1, db.acompanhamentoDao().getAll().size)
                db.planoDao().upsert(PlanoEntity(mes = "2026-11", criadoEm = 1, conteudo = "mes=2026-11"))
                assertEquals("2026-11", db.planoDao().getAll().single().mes)
                assertNull(db.sessaoAssistenteDao().get(8))
            }
        } finally { db.close(); ctx.deleteDatabase(name) }
    }
}
