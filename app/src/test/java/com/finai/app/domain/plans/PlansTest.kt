package com.finai.app.domain.plans

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.InstallmentsSummary
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

private val TODAY = LocalDate.of(2026, 11, 8)

private fun goal(id: Long, name: String, target: Long, saved: Long, deadline: YearMonth, priority: Int) =
    ObjetivoEntity(id = id, tipo = "Compra", nome = name, valorAlvoCentavos = target, valorGuardadoCentavos = saved,
        prazo = deadline.atEndOfMonth().toEpochMillis(), prioridade = priority)

/** Regra 5: quando as metas pedem mais do que cabe, a de menor prioridade cede, um conflito por vez. */
class GoalConflictResolverTest {
    private val chile = goal(1, "Chile", 600_000, 0, YearMonth.of(2027, 5), 1)      // 6 meses → R$ 1.000/mês
    private val notebook = goal(2, "Notebook", 300_000, 0, YearMonth.of(2027, 2), 2) // 3 meses → R$ 1.000/mês

    @Test fun asksAreMissingDividedByMonthsLeft() {
        val asks = GoalConflictResolver.asks(listOf(chile, notebook), TODAY)
        assertEquals(listOf(100_000L, 100_000L), asks.map { it.monthlyCents })
    }

    @Test fun noConflictWhenEverythingFits() = assertNull(GoalConflictResolver.resolve(listOf(chile, notebook), 200_000, TODAY))

    @Test fun lowestPriorityGivesWayWithTheFirstDeadlineThatFits() {
        // Cabe R$ 1.500: Chile fica com R$ 1.000, sobram R$ 500 para o Notebook → 6 meses, mai/27.
        val c = GoalConflictResolver.resolve(listOf(chile, notebook), 150_000, TODAY)!!
        assertEquals(2L, c.goalId)
        assertEquals(200_000L, c.asksCents)
        assertEquals(YearMonth.of(2027, 5), c.newDeadline)
        assertEquals(50_000L, c.newMonthlyCents)
        assertEquals("meta-conflito-2", c.decisionId)
    }

    @Test fun whenNothingIsLeftThereIsNoNewDeadline() {
        val c = GoalConflictResolver.resolve(listOf(chile, notebook), 90_000, TODAY)!!
        assertNull(c.newDeadline)
    }

    @Test fun openReserveAsksItsPlannedContributionButNeverGivesWay() {
        val reserve = ObjetivoEntity(id = 3, tipo = "Reserva", nome = "Reserva", valorAlvoCentavos = 0, valorGuardadoCentavos = 50_000,
            prazo = TODAY.toEpochMillis(), prioridade = 9, aporteMensalCentavos = 30_000)
        val asks = GoalConflictResolver.asks(listOf(reserve, chile), TODAY)
        assertEquals(130_000L, asks.sumOf { it.monthlyCents })
        assertEquals(1L, GoalConflictResolver.resolve(listOf(reserve, chile), 100_000, TODAY)!!.goalId)
    }

    @Test fun completedGoalsAskNothing() {
        val done = goal(4, "Fone", 50_000, 50_000, YearMonth.of(2027, 1), 1)
        assertTrue(GoalConflictResolver.asks(listOf(done), TODAY).isEmpty())
    }

    @Test fun seals() {
        val c = GoalConflictResolver.resolve(listOf(chile, notebook), 150_000, TODAY)
        assertEquals(GoalSeal.EmDia, GoalConflictResolver.seal(chile, c, TODAY))
        assertEquals(GoalSeal.NaoCabe, GoalConflictResolver.seal(notebook, c, TODAY))
        assertEquals(GoalSeal.Concluida, GoalConflictResolver.seal(goal(5, "X", 100, 100, YearMonth.of(2027, 1), 3), c, TODAY))
        assertEquals(GoalSeal.NaoCabe, GoalConflictResolver.seal(goal(6, "Y", 100, 0, YearMonth.of(2026, 9), 3), null, TODAY))
    }
}

/** Regra 4: o que cabe guardar é a média da sobra dos próximos 6 meses. */
class SavingsCapacityTest {
    private fun t(day: LocalDate, cents: Long, desc: String, tipo: String, recorrente: Boolean, main: Boolean = false) =
        TransacaoEntity(data = day.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = if (tipo == "Receita") "Receita" else "Moradia",
            contaOrigem = "Nubank", recorrente = recorrente, tipo = tipo, origem = "manual", rendaPrincipal = main)

    @Test fun salaryMinusFixedBills() {
        val list = listOf(
            t(LocalDate.of(2026, 10, 5), 500_000, "Salário", "Receita", recorrente = true, main = true),
            t(LocalDate.of(2026, 10, 10), 200_000, "Aluguel", "Gasto", recorrente = true),
        )
        val cap = SavingsCapacity.of(emptyList(), list, emptyList(), TODAY)
        assertEquals(6, cap.months.size)
        assertEquals(YearMonth.of(2026, 12), cap.months.first().month)
        assertTrue(cap.months.joinToString { "${it.month}=${it.cents}" }, cap.months.all { it.cents == 300_000L })
        assertEquals(300_000L, cap.cabeGuardarCents)
    }

    @Test fun variableSpendingAverageIsDiscounted() {
        val list = listOf(
            t(LocalDate.of(2026, 8, 5), 500_000, "Salário", "Receita", recorrente = true, main = true),
            t(LocalDate.of(2026, 8, 20), 60_000, "Mercado", "Gasto", recorrente = false),
            t(LocalDate.of(2026, 9, 20), 60_000, "Mercado", "Gasto", recorrente = false),
            t(LocalDate.of(2026, 10, 20), 60_000, "Mercado", "Gasto", recorrente = false),
        )
        val cap = SavingsCapacity.of(emptyList(), list, emptyList(), TODAY)
        assertEquals(60_000L, cap.variableAverageCents)
        assertEquals(440_000L, cap.cabeGuardarCents)
    }

    @Test fun neverNegative() {
        val list = listOf(t(LocalDate.of(2026, 10, 10), 200_000, "Aluguel", "Gasto", recorrente = true))
        assertEquals(0L, SavingsCapacity.of(emptyList(), list, emptyList(), TODAY).cabeGuardarCents)
    }
}

/** Regra 7: compromissos mês a mês e quando fica livre de parcelas. */
class CommitmentsScheduleTest {
    private fun loan(id: Long, name: String, cents: Long, left: Int) = DividaEntity(
        id = id, nome = name, valorOriginalCentavos = cents * 24, valorAbertoCentavos = cents * left, taxaJurosMensalBasisPoints = 200,
        parcelasRestantes = left, valorParcelaCentavos = cents, parcelasTotais = 24,
        proximoVencimento = LocalDate.of(2026, 11, 15).toEpochMillis(), diaVencimento = 15,
    )
    private val empty = InstallmentsSummary.of(emptyList(), emptyList(), emptyList())

    @Test fun loansEndingAtDifferentMonths() {
        // R$ 1.010 até mar/27, R$ 600 em abr, R$ 410 até jun, livre em jul/27.
        val debts = listOf(loan(1, "Picpay", 41_000, 8), loan(2, "Celular", 19_000, 6), loan(3, "Moto", 41_000, 5))
        val s = CommitmentsSchedule.of(emptyList(), emptyList(), debts, empty, TODAY)
        fun at(y: Int, m: Int) = s.months.first { it.month == YearMonth.of(y, m) }.installmentsCents
        assertEquals(101_000L, at(2026, 11))
        assertEquals(101_000L, at(2027, 3))
        assertEquals(60_000L, at(2027, 4))
        assertEquals(41_000L, at(2027, 6))
        assertEquals(0L, at(2027, 7))
        assertEquals(YearMonth.of(2027, 7), s.freeFrom)
        assertEquals(101_000L, s.monthlyCents)
        assertEquals(41_000L * 8 + 19_000L * 6 + 41_000L * 5, s.installmentsOpenCents)
    }

    @Test fun paidOffDebtDoesNotCount() {
        val s = CommitmentsSchedule.of(emptyList(), emptyList(), listOf(loan(1, "X", 10_000, 3).copy(quitadaEm = 1L)), empty, TODAY)
        assertNull(s.freeFrom)
        assertEquals(6, s.months.size)
    }

    @Test fun fixedBillsComeFromRecurringBills() {
        val rent = ContaEntity(nome = "Aluguel", valorCentavos = 150_000, vencimento = LocalDate.of(2026, 11, 10).toEpochMillis(),
            status = "pendente", tipo = "a_pagar", recorrente = true)
        val gym = TransacaoEntity(data = LocalDate.of(2026, 10, 3).toEpochMillis(), descricao = "Academia", valorCentavos = 11_000,
            categoria = "Saúde", contaOrigem = "Nubank", recorrente = true, origem = "manual")
        val s = CommitmentsSchedule.of(listOf(rent), listOf(gym), emptyList(), empty, TODAY)
        assertEquals(listOf("Academia", "Aluguel"), s.fixedBills.map { it.name })
        assertEquals(161_000L, s.fixedMonthlyCents)
    }

    @Test fun prepaymentOnlyForExpensiveDebt() {
        val cheap = loan(1, "Barato", 50_000, 10)
        assertNull(DebtPrepayment.opportunity(cheap, 100_000))
        val expensive = cheap.copy(taxaJurosMensalBasisPoints = 500)
        val o = DebtPrepayment.opportunity(expensive, 100_000)!!
        assertEquals(2, o.installmentsCut)
        assertTrue(o.savedCents > 0)
        assertTrue(o.amountCents <= 100_000)
    }
}
