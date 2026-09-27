package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SavingsCapacityCalculatorTest {

    private val today = LocalDate.of(2026, 9, 27)

    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun tx(desc: String, cents: Long, tipo: TransactionType, date: LocalDate, recorrente: Boolean = false, extra: Boolean = false) =
        TransacaoEntity(
            data = millis(date), valorCentavos = cents, descricao = desc, categoria = "Outros",
            contaOrigem = "Carteira", origem = "manual", recorrente = recorrente, tipo = tipo.name, extra = extra,
        )

    @Test
    fun `capacidade e o balanco do mes da Agenda sem entradas extras`() {
        val transacoes = listOf(
            tx("Salario", 255_000, TransactionType.Receita, LocalDate.of(2026, 9, 30)),
            tx("Contas de casa", 100_000, TransactionType.Gasto, LocalDate.of(2026, 8, 30), recorrente = true),
            tx("Internet", 8_500, TransactionType.Gasto, LocalDate.of(2026, 9, 30), recorrente = true),
            tx("Bônus", 190_000, TransactionType.Receita, LocalDate.of(2026, 9, 10), extra = true),
        )
        val flow = MonthCashFlow.of(emptyList(), transacoes, emptyList(), java.time.YearMonth.of(2026, 9), today)
        val capacity = SavingsCapacityCalculator.monthlyCapacityCents(emptyList(), transacoes, emptyList(), today)

        assertEquals(255_000L - 100_000L - 8_500L, capacity)
        // Mesma base do "Balanço do mês" da Início, só sem o extra.
        assertEquals(flow.saldoCents - 190_000L, capacity)
    }
}
