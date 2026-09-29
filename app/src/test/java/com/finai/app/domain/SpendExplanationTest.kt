package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpendExplanationTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun tx(date: LocalDate, cents: Long, tipo: TransactionType, recorrente: Boolean = false, desc: String) =
        TransacaoEntity(
            data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = "Outros",
            contaOrigem = "Carteira", recorrente = recorrente, origem = "manual", tipo = tipo.name)

    private fun conta(nome: String, cents: Long, vence: LocalDate) = ContaEntity(
        nome = nome, valorCentavos = cents, vencimento = vence.toEpochMillis(), status = "pendente", tipo = "a_pagar", recorrente = false)

    private fun explain(transacoes: List<TransacaoEntity>, contas: List<ContaEntity>, metas: Long = 0): SpendExplanation {
        val cycle = PayCycle.of(contas, transacoes, emptyList(), today)!!
        return SpendExplanation.of(SafeToSpendCalculator.fromCycle(cycle, metas), cycle, today)
    }

    /** Os passos somam o valor final: dá para conferir a conta à mão. */
    @Test fun stepsAddUpToWhatIsFree() {
        val e = explain(
            transacoes = listOf(
                tx(LocalDate.of(2026, 7, 30), 300_000, TransactionType.Receita, recorrente = true, desc = "Salário"),
                tx(LocalDate.of(2026, 10, 2), 20_000, TransactionType.Gasto, desc = "Mercado"),
                tx(LocalDate.of(2026, 10, 20), 200_000, TransactionType.Receita, desc = "Freela"),
            ),
            contas = listOf(conta("Fatura", 100_000, LocalDate.of(2026, 10, 12))),
        )
        val parts = e.steps.filterNot { it.total }.sumOf { it.cents }
        assertEquals(e.steps.last().cents, parts)
        assertEquals(180_000L, e.steps.last().cents) // 300k − 20k − 100k no dia 12, antes do freela do dia 20
        assertTrue(e.steps.any { it.label == "Reserva para compromissos" && it.cents == -100_000L })
        assertEquals(listOf("Fatura"), e.upcoming.map { it.label })
        assertEquals(listOf("Freela"), e.incoming.map { it.label })
    }

    @Test fun deducedSalaryIsFlaggedAsMissingInfo() {
        val e = explain(
            transacoes = listOf(tx(LocalDate.of(2026, 7, 30), 300_000, TransactionType.Receita, recorrente = true, desc = "Empresa X")),
            contas = emptyList(),
        )
        assertTrue(e.missing.any { "Empresa X" in it })
    }

    @Test fun withoutCycleItExplainsTheMonthAndAsksForTheSalary() {
        val safe = SafeToSpendCalculator.calculate(emptyList(), listOf(tx(today, 5_000, TransactionType.Gasto, desc = "Café")), 0, today)
        val e = SpendExplanation.of(safe, null, today)
        assertTrue(e.period.contains("fim do mês"))
        assertTrue(e.missing.single().contains("renda principal"))
        // Gasto sem receita: a conta fica negativa e é mostrada assim, não como "R$ 0".
        assertEquals(-5_000L, e.steps.last().cents)
        assertEquals(5_000L, e.shortfall?.cents)
    }
}
