package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Migration20To21Test {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java,
    )

    @Test fun repairsVersion20WithTheVersion19SchemaWithoutLosingData() {
        val name = "migration_20_21_incomplete.db"
        helper.createDatabase(name, 19).apply {
            execSQL("INSERT INTO mensagens_chat (id,papel,texto,timestamp,conversaId) VALUES (1,'usuario','Quanto gastei?',100,7)")
            execSQL("INSERT INTO contas (id,nome,valorCentavos,vencimento,status,tipo,recorrente,pagoEm) VALUES (42,'Fatura',132000,123,'pago','a_pagar',0,456)")
            execSQL("INSERT INTO avisos (id,tipo,estado,valorRef) VALUES ('aviso-1','limite','dispensado',18000)")
            // Estado observado no celular: o número avançou, mas o schema e o hash não.
            version = 20
            close()
        }
        openAndCheck(name) { sql ->
            sql.query("SELECT texto, estrutura FROM mensagens_chat WHERE id=1").use {
                assertTrue(it.moveToFirst())
                assertEquals("Quanto gastei?", it.getString(0))
                assertTrue(it.isNull(1))
            }
            sql.query("SELECT valorCentavos, pagoEm FROM contas WHERE id=42").use {
                assertTrue(it.moveToFirst())
                assertEquals(132_000L, it.getLong(0))
                assertEquals(456L, it.getLong(1))
            }
            sql.query("SELECT estado FROM avisos WHERE id='aviso-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("dispensado", it.getString(0))
            }
            listOf("sessoes_assistente", "acoes_assistente", "acompanhamentos", "planos").forEach { table ->
                sql.query("SELECT COUNT(*) FROM `$table`").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
            }
        }
    }

    @Test fun preservesACompleteVersion20IncludingStructuredMessagesAndAssistantData() {
        val name = "migration_20_21_complete.db"
        helper.createDatabase(name, 20).apply {
            execSQL("INSERT INTO mensagens_chat (id,papel,texto,timestamp,conversaId,estrutura) VALUES (1,'assistente','Resposta',100,7,'{\"resposta\":true}')")
            execSQL("INSERT INTO sessoes_assistente (conversaId,estado,atualizadoEm) VALUES (7,'pendente',123)")
            execSQL("INSERT INTO acoes_assistente (id,chave,conversaId,tipo,resumo,recibo,estado,criadoEm) VALUES (1,'7:1',7,'pagar_conta','Pago','{}','applied',123)")
            execSQL("INSERT INTO acompanhamentos (id,tipo,alvo,valorCentavos,descricao,estado,criadoEm) VALUES (1,'quitacao','Picpay',1000,'Avisar','ativo',123)")
            execSQL("INSERT INTO planos (id,mes,criadoEm,conteudo) VALUES (1,'2026-11',123,'plano aceito')")
            close()
        }
        openAndCheck(name) { sql ->
            mapOf(
                "SELECT estrutura FROM mensagens_chat WHERE id=1" to "{\"resposta\":true}",
                "SELECT estado FROM sessoes_assistente WHERE conversaId=7" to "pendente",
                "SELECT chave FROM acoes_assistente WHERE id=1" to "7:1",
                "SELECT alvo FROM acompanhamentos WHERE id=1" to "Picpay",
                "SELECT conteudo FROM planos WHERE id=1" to "plano aceito",
            ).forEach { (query, expected) ->
                sql.query(query).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(expected, it.getString(0))
                }
            }
        }
    }

    private fun openAndCheck(
        name: String,
        check: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FinaiDatabase::class.java, name)
            .addMigrations(*FinaiDatabase.MIGRATIONS)
            .build()
        try {
            // Abrir pelo Room reproduz a checagem de integridade que derrubava o app.
            val sql = db.openHelper.writableDatabase
            assertEquals(21, sql.version)
            check(sql)
            sql.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst())
                assertEquals("ok", it.getString(0))
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
