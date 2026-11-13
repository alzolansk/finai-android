package com.finai.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.entity.AvisoEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Migration16To17Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)
    @Test fun preservesFinancialDataAndPersistsInboxState() {
        val name = "migration_16_17_test.db"
        helper.createDatabase(name, 16).apply {
            execSQL("INSERT INTO contas (id,nome,valorCentavos,vencimento,status,tipo,recorrente) VALUES (42,'Fatura',132000,123,'pendente','a_pagar',0)")
            execSQL("INSERT INTO transacoes (data,descricao,valorCentavos,categoria,contaOrigem,recorrente,tipo,origem,excecoes,competenciaDeslocamento,rendaPrincipal) VALUES (123,'Compra',4590,'Alimentação','Nubank',0,'Gasto','manual','',0,0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 17, true, FinaiDatabase.MIGRATION_16_17).apply {
            query("SELECT valorCentavos FROM contas WHERE id=42").use { assertTrue(it.moveToFirst()); assertEquals(132_000, it.getLong(0)) }
            query("SELECT descricao,valorCentavos FROM transacoes").use { assertTrue(it.moveToFirst()); assertEquals("Compra", it.getString(0)); assertEquals(4590, it.getLong(1)) }
            close()
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(ctx, FinaiDatabase::class.java, name).addMigrations(*FinaiDatabase.MIGRATIONS).build()
        try { runBlocking {
            db.avisoDao().upsert(AvisoEntity("limite-Lazer-2026-11", "limite:0", "dispensado", 18000, dispensadoEm = "2026-11-08"))
            assertEquals("dispensado", db.avisoDao().getAll().single().estado)
            assertEquals(1, db.contaDao().getAll().size)
        } } finally { db.close(); ctx.deleteDatabase(name) }
    }
}
