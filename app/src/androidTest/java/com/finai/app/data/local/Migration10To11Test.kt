package com.finai.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `MIGRATION_10_11` acrescenta `objetivos.aporteMensalCentavos` (reserva contínua) sem mexer
 * nos objetivos existentes: todos ficam com 0 (nenhum aporte planejado).
 * O `11.json` é gerado pelo Room no primeiro build depois desta mudança.
 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    @Test
    fun existingGoalsKeepTheirDataAndGetNoPlannedContribution() {
        helper.createDatabase(DB, 10).apply {
            execSQL(
                "INSERT INTO objetivos (tipo, nome, valorAlvoCentavos, valorGuardadoCentavos, prazo, prioridade, descricao) " +
                    "VALUES ('Reserva', 'Reserva geral', 0, 150000, 0, 1, '')",
            )
            execSQL(
                "INSERT INTO objetivos (tipo, nome, valorAlvoCentavos, valorGuardadoCentavos, prazo, prioridade, descricao) " +
                    "VALUES ('Viagem', 'Itália 2028', 2000000, 0, 0, 2, '')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 11, true, FinaiDatabase.MIGRATION_10_11)
        db.query("SELECT nome, valorGuardadoCentavos, aporteMensalCentavos FROM objetivos ORDER BY id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getLong(1), c.getLong(2))) }
            assertEquals(listOf(Triple("Reserva geral", 150_000L, 0L), Triple("Itália 2028", 0L, 0L)), rows)
        }
    }

    private companion object {
        const val DB = "migration_10_11_test.db"
    }
}
