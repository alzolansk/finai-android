package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.entity.FechamentoEntity
import com.finai.app.data.local.entity.MemoriaEntity
import com.finai.app.data.local.entity.RegraCategoriaEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Migration18To19Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)
    @Test fun preservesDataAndCreatesMemoryRulesAndClosing() {
        val name = "migration_18_19_test.db"
        helper.createDatabase(name, 18).apply {
            execSQL("INSERT INTO contas (id,nome,valorCentavos,vencimento,status,tipo,recorrente,pagoEm) VALUES (42,'Fatura',132000,123,'pago','a_pagar',0,456)")
            execSQL("INSERT INTO avisos (id,tipo,estado,valorRef) VALUES ('limite-Lazer-2026-11','limite:0','dispensado',18000)")
            close()
        }
        helper.runMigrationsAndValidate(name, 19, true, FinaiDatabase.MIGRATION_18_19).apply {
            query("SELECT valorCentavos, pagoEm FROM contas WHERE id=42").use {
                assertTrue(it.moveToFirst()); assertEquals(132_000, it.getLong(0)); assertEquals(456, it.getLong(1))
            }
            query("SELECT estado FROM avisos").use { assertTrue(it.moveToFirst()); assertEquals("dispensado", it.getString(0)) }
            close()
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(ctx, FinaiDatabase::class.java, name).addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try { runBlocking {
            db.memoriaDao().insert(MemoriaEntity(texto = "Academia não é corte", tipo = "decisao", criadoEm = 1, origem = "chat"))
            db.regraCategoriaDao().upsert(RegraCategoriaEntity("padaria do ze", "Alimentação"))
            db.fechamentoDao().upsert(FechamentoEntity("2026-10", "limites", "aplicado", 1))
            assertEquals(1, db.memoriaDao().getAll().size)
            assertEquals("Alimentação", db.regraCategoriaDao().getAll().single().categoria)
            assertEquals("aplicado", db.fechamentoDao().getAll().single().resposta)
        } } finally { db.close(); ctx.deleteDatabase(name) }
    }
}
