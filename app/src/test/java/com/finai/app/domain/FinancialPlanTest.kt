package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** O plano central: cada real livre tem um destino só, e o livre sai da simulação dia a dia. */
class FinancialPlanTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun tx(date: LocalDate, cents: Long, tipo: TransactionType, recorrente: Boolean = false, desc: String = "Teste") =
        TransacaoEntity(
            data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = "Outros",
            contaOrigem = "Carteira", recorrente = recorrente, origem = "manual", tipo = tipo.name)

    /** Salário recorrente do dia 30: ciclo de 30/09 a 29/10. */
    private fun salario(cents: Long = 300_000) = tx(LocalDate.of(2026, 7, 30), cents, TransactionType.Receita, recorrente = true, desc = "Salário")

    private fun conta(nome: String, cents: Long, vence: LocalDate) = ContaEntity(
        nome = nome, valorCentavos = cents, vencimento = vence.toEpochMillis(), status = "pendente", tipo = "a_pagar", recorrente = false)

    private fun meta(id: Long, nome: String, prioridade: Int, faltam: Long = 1_200_000) = ObjetivoEntity(
        id = id, tipo = "Viagem", nome = nome, valorAlvoCentavos = faltam, valorGuardadoCentavos = 0,
        prazo = LocalDate.of(2027, 10, 28).toEpochMillis(), prioridade = prioridade)

    private fun rotativo(taxaBp: Int) = DividaEntity(
        id = 7, nome = "Rotativo", valorOriginalCentavos = 400_000, valorAbertoCentavos = 400_000,
        taxaJurosMensalBasisPoints = taxaBp, parcelasRestantes = 0, valorParcelaCentavos = 0)

    private val tresMetas = listOf(meta(1, "Viagem", 1), meta(2, "Passagem", 2), meta(3, "Carro", 3))

    @Test fun theSameSurplusGoesToOneDestinationOnly() {
        // Sobram R$ 114 no ciclo; há uma dívida cara e três metas pedindo aporte.
        val plan = FinancialPlan.build(
            contas = listOf(conta("Aluguel e contas", 288_600, LocalDate.of(2026, 10, 12))),
            transacoes = listOf(salario()),
            dividas = listOf(rotativo(1_200)),
            objetivos = tresMetas,
            today = today,
        )
        assertEquals(11_400L, plan.disponivelCents)
        assertEquals(1, plan.allocations.size)
        assertEquals(AllocationTarget.Debt, plan.destino!!.target)
        assertEquals(11_400L, plan.forDebt(7))
        // Nenhuma meta recebe os mesmos R$ 114.
        plan.goals.forEach { assertEquals(0L, it.monthlyContributionFundedCents) }
        plan.goals.forEach { assertTrue(plan.goalNote(it), plan.goalNote(it).contains("\"Rotativo\"")) }
        // E o "pode gastar" não conta com o que já tem destino.
        assertEquals(0L, plan.livreParaGastarCents)
        assertEquals(0L, plan.safe.slackThisMonthCents)
        assertTrue(plan.decisions(emptyList(), emptyList()).first().action.contains("Rotativo"))
        assertTrue(plan.aiBlock().contains("cada real tem um destino só"))
    }

    @Test fun cheapDebtLeavesTheSurplusToGoalsInPriorityOrder() {
        val plan = FinancialPlan.build(
            contas = listOf(conta("Aluguel e contas", 200_000, LocalDate.of(2026, 10, 12))),
            transacoes = listOf(salario()),
            dividas = listOf(rotativo(150)),
            objetivos = tresMetas,
            today = today,
        )
        // R$ 1.000 livres; cada meta pede R$ 1.200 ÷ 12 meses = R$ 1.000 → só a primeira recebe.
        assertEquals(100_000L, plan.disponivelCents)
        assertEquals(0L, plan.forDebt(7))
        assertEquals(listOf(100_000L, 0L, 0L), plan.goals.sortedBy { it.objetivo.prioridade }.map { it.monthlyContributionFundedCents })
        assertEquals(plan.disponivelCents, plan.alocadoCents + plan.livreParaGastarCents)
    }

    @Test fun anObligationBeforeTheNextIncomeHoldsMoneyBack() {
        // Entra R$ 1.900 no dia 10 e sai R$ 600 no dia 12; antes disso, R$ 2.500 no dia 8.
        val plan = FinancialPlan.build(
            contas = listOf(
                conta("Aluguel", 250_000, LocalDate.of(2026, 10, 8)),
                conta("Fatura", 60_000, LocalDate.of(2026, 10, 12)),
            ),
            transacoes = listOf(salario(), tx(LocalDate.of(2026, 10, 10), 190_000, TransactionType.Receita, desc = "Freela")),
            dividas = emptyList(),
            objetivos = emptyList(),
            today = today,
        )
        assertNull(plan.shortfall)
        // Sobra final R$ 1.800, mas no dia 8 só há R$ 500: é isso que está livre agora.
        assertEquals(50_000L, plan.disponivelCents)
        assertEquals(130_000L, plan.reservadoFuturoCents)
        assertEquals(310_000L, plan.comprometidoCents)
    }

    @Test fun aShortfallBlocksEveryAllocation() {
        val plan = FinancialPlan.build(
            contas = listOf(conta("Aluguel", 350_000, LocalDate.of(2026, 10, 8))),
            transacoes = listOf(salario(), tx(LocalDate.of(2026, 10, 10), 190_000, TransactionType.Receita, desc = "Freela")),
            dividas = listOf(rotativo(1_200)),
            objetivos = tresMetas,
            today = today,
        )
        assertNotNull(plan.shortfall)
        assertTrue(plan.allocations.isEmpty())
        assertEquals(0L, plan.livreParaGastarCents)
        assertTrue(plan.decisions(emptyList(), emptyList()).first().action.startsWith("Cobrir a falta"))
        plan.goals.forEach { assertTrue(plan.goalNote(it).contains("falta")) }
    }

    @Test fun allocateNeverSpendsMoreThanItHas() {
        val goals = GoalCalculator.plan(tresMetas, 500_000, today)
        val out = FinancialPlan.allocate(150_000, emptyList(), goals)
        assertEquals(150_000L, out.sumOf { it.cents })
        assertEquals(listOf(1L, 2L), out.map { it.id })
    }
}
