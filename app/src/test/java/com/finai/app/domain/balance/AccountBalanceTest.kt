package com.finai.app.domain.balance

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Regra 1 (planning-v2): o saldo em conta soma tudo que foi efetivado, sem recomeçar. */
class AccountBalanceTest {
    private val today = LocalDate.of(2026, 11, 8)
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
    private var id = 1L
    private fun tx(date: LocalDate, cents: Long, tipo: String = "Gasto", recorrente: Boolean = false) = TransacaoEntity(
        id = id++, data = date.toEpochMillis(), descricao = "x", valorCentavos = cents, categoria = "Outros",
        contaOrigem = "Nubank", recorrente = recorrente, tipo = tipo, origem = "manual",
    )
    private fun conta(cents: Long, vence: LocalDate, pago: Boolean, tipo: String = "a_pagar") = ContaEntity(
        id = id++, nome = "c", valorCentavos = cents, vencimento = vence.toEpochMillis(),
        status = if (pago) "pago" else "pendente", tipo = tipo, recorrente = false,
    )

    private fun balance(contas: List<ContaEntity> = emptyList(), t: List<TransacaoEntity>) =
        AccountBalance.todayCents(contas, t, emptyList(), today)

    @Test fun sumsEverythingSettledAcrossMonths() {
        val t = listOf(tx(d(9, 5), 500_000, "Receita"), tx(d(10, 5), 500_000, "Receita"), tx(d(10, 20), 200_000), tx(d(11, 2), 100_000))
        assertEquals(700_000L, balance(t = t))
    }

    @Test fun startsAtTheOldestRecord() {
        val t = listOf(tx(d(9, 5), 1, "Receita"), tx(d(11, 2), 1))
        assertEquals(YearMonth.of(2026, 9), AccountBalance.startMonthFor(t, emptyList(), today))
        assertEquals(YearMonth.of(2026, 11), AccountBalance.startMonthFor(emptyList(), emptyList(), today))
        // Limite de 24 meses para trás.
        val old = listOf(tx(d(1, 5, 2020), 1, "Receita"))
        assertEquals(YearMonth.of(2024, 11), AccountBalance.startMonthFor(old, emptyList(), today))
    }

    @Test fun futureAndUnpaidDoNotCount() {
        val t = listOf(tx(d(11, 1), 300_000, "Receita"), tx(d(11, 20), 100_000), tx(d(11, 30), 900_000, "Receita"))
        val c = listOf(conta(50_000, d(11, 10), pago = false), conta(70_000, d(11, 5), pago = true))
        assertEquals(300_000L - 70_000L, balance(c, t))
    }

    @Test fun recurringSeriesCountsOneOccurrencePerPastMonth() {
        val salario = tx(d(9, 5), 500_000, "Receita", recorrente = true)
        // 05/09, 05/10 e 05/11 já caíram; 05/12 não.
        assertEquals(1_500_000L, balance(t = listOf(salario)))
    }

    @Test fun goalContributionLeavesButTransferBetweenAccountsDoesNot() {
        val aporte = tx(d(11, 3), 40_000, "Transferencia").copy(objetivoId = 1)
        val entre = tx(d(11, 3), 99_000, "Transferencia")
        assertEquals(-40_000L, balance(t = listOf(aporte, entre)))
    }
}
