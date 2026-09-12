package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeToSpendCalculatorTest {

    @Test
    fun `slack splits evenly across remaining days`() {
        val today = LocalDate.of(2026, 3, 21) // 31-day month, 11 days remain incl. today
        val contas = listOf(
            ContaEntity(nome = "Salário", valorCentavos = 500_000, vencimento = today.withDayOfMonth(5).toEpochMillis(), status = "pago", tipo = "a_receber", recorrente = true),
            ContaEntity(nome = "Aluguel", valorCentavos = 150_000, vencimento = today.withDayOfMonth(10).toEpochMillis(), status = "pago", tipo = "a_pagar", recorrente = true),
            ContaEntity(nome = "Internet", valorCentavos = 10_000, vencimento = today.withDayOfMonth(25).toEpochMillis(), status = "pendente", tipo = "a_pagar", recorrente = true),
        )
        val transacoes = listOf(
            TransacaoEntity(data = today.minusDays(1).toEpochMillis(), descricao = "Mercado", valorCentavos = 40_000, categoria = "Alimentação", contaOrigem = "", recorrente = false, origem = "manual"),
        )

        val result = SafeToSpendCalculator.calculate(contas, transacoes, aporteMensalMetasCents = 0, today = today)

        // slack = 500_000 - 40_000 - 10_000 (only unpaid a_pagar counts) - 0 = 450_000
        assertEquals(450_000L, result.slackThisMonthCents)
        assertEquals(11, result.daysRemaining)
        assertEquals(450_000L / 11, result.safeTodayCents)
    }

    @Test
    fun `negative slack floors safe-to-spend at zero`() {
        val today = LocalDate.of(2026, 3, 1)
        val contas = listOf(
            ContaEntity(nome = "Fatura", valorCentavos = 1_000_000, vencimento = today.toEpochMillis(), status = "pendente", tipo = "a_pagar", recorrente = false),
        )
        val result = SafeToSpendCalculator.calculate(contas, emptyList(), aporteMensalMetasCents = 0, today = today)
        assertEquals(0L, result.safeTodayCents)
    }
}
