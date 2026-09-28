package com.finai.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fase 7, item 5: `MIGRATION_9_10` marca como renda principal o que já era tratado como
 * salário (pelo nome, ou a maior receita recorrente) e grava o dia combinado das dívidas.
 * Parte do schema exportado da v9 (`app/schemas/.../9.json`), que existe desde a Fase 6.
 */
@RunWith(AndroidJUnit4::class)
class Migration9To10Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinaiDatabase::class.java)

    private fun millis(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun receita(desc: String, cents: Long, recorrente: Int, extra: Int = 0) =
        "INSERT INTO transacoes (data, descricao, valorCentavos, categoria, contaOrigem, recorrente, tipo, origem, extra) " +
            "VALUES (${millis(LocalDate.of(2026, 9, 5))}, '$desc', $cents, 'Outros', '', $recorrente, 'Receita', 'manual', $extra)"

    private fun divida(nome: String, due: LocalDate?) =
        "INSERT INTO dividas (nome, valorOriginalCentavos, valorAbertoCentavos, taxaJurosMensalBasisPoints, parcelasRestantes, " +
            "valorParcelaCentavos, parcelasTotais, proximoVencimento) VALUES ('$nome', 0, 100000, 0, 4, 25000, 12, ${due?.let(::millis) ?: "NULL"})"

    @Test
    fun salaryByNameBecomesMainIncomeAndDebtsKeepTheirDay() {
        helper.createDatabase(DB, 9).apply {
            execSQL(receita("Salário", 500_000, 1))
            execSQL(receita("13º salário", 250_000, 0, extra = 1))
            execSQL(receita("Aluguel recebido", 900_000, 1))
            execSQL(divida("Empréstimo", LocalDate.of(2026, 10, 29)))
            execSQL(divida("Tablet", LocalDate.of(2026, 9, 30)))
            execSQL(divida("Antiga", null))
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 10, true, FinaiDatabase.MIGRATION_9_10)
        db.query("SELECT descricao, rendaPrincipal FROM transacoes ORDER BY id").use { c ->
            val marked = buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
            assertEquals(mapOf("Salário" to 1, "13º salário" to 0, "Aluguel recebido" to 0), marked)
        }
        db.query("SELECT nome, diaVencimento FROM dividas ORDER BY id").use { c ->
            val days = buildMap { while (c.moveToNext()) put(c.getString(0), if (c.isNull(1)) null else c.getInt(1)) }
            // 30/09 é o último dia do mês: vira 31, como a regra de fim de mês já tratava.
            assertEquals(mapOf("Empréstimo" to 29, "Tablet" to 31, "Antiga" to null), days)
        }
    }

    @Test
    fun withoutSalaryNameTheLargestRecurringIncomeIsMarked() {
        helper.createDatabase(DB, 9).apply {
            execSQL(receita("Renda mensal", 400_000, 1))
            execSQL(receita("Freela", 900_000, 0))
            execSQL(receita("Aluguel recebido", 80_000, 1))
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 10, true, FinaiDatabase.MIGRATION_9_10)
        db.query("SELECT descricao FROM transacoes WHERE rendaPrincipal = 1").use { c ->
            val marked = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertEquals(listOf("Renda mensal"), marked)
        }
    }

    private companion object {
        const val DB = "migration_9_10_test.db"
    }
}
