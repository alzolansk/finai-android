package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Salário dividido por competência: divisão em centavos, agrupamento, saldo corrido e regeneração. */
class IncomeSplitTest {
    private val total = 585_000L
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    private fun template() = TransacaoEntity(
        id = 1, data = LocalDate.of(2026, 10, 5).toEpochMillis(), descricao = "Salário", valorCentavos = total,
        categoria = "Salário", contaOrigem = "Itaú", recorrente = true, tipo = "Receita", origem = "manual",
    )

    /** Grava o plano numa "tabela" em memória, dando ids novos, como o repositório faz. */
    private fun apply(table: List<TransacaoEntity>, plan: IncomePlan): List<TransacaoEntity> {
        val gone = plan.delete.map { it.id }.toSet()
        val updates = plan.update.associateBy { it.id }
        var next = (table.maxOfOrNull { it.id } ?: 0) + 1
        return table.filter { it.id !in gone }.map { updates[it.id] ?: it } + plan.insert.map { it.copy(id = next++) }
    }

    private fun split(rule: AdvanceRule) = IncomeSplit(rule)

    // ── divisão em centavos ──────────────────────────────────────────────

    @Test fun percentSplitSumsToTheSalaryInCents() {
        for (pct in IncomeSplit.MIN_PCT..IncomeSplit.MAX_PCT step IncomeSplit.PCT_STEP) {
            for (salary in listOf(585_000L, 585_001L, 100_001L, 333_333L, 100_000L, 777_777L)) {
                val s = split(AdvanceRule.Percent(pct))
                assertEquals("pct=$pct total=$salary", salary, s.advanceCents(salary) + s.balanceCents(salary))
            }
        }
    }

    @Test fun percentRoundsHalfUpToTheCent() {
        // 40% de R$ 5.850,05 = R$ 2.340,02 (234.002 centavos); 10% de 1005 = 100,5 → 101.
        assertEquals(234_002L, split(AdvanceRule.Percent(40)).advanceCents(585_005))
        assertEquals(101L, split(AdvanceRule.Percent(10)).advanceCents(1_005))
    }

    @Test fun fixedSplitSumsToTheSalaryAndNeverExceedsIt() {
        val s = split(AdvanceRule.Fixed(200_000))
        assertEquals(200_000L, s.advanceCents(total))
        assertEquals(total - 200_000L, s.balanceCents(total))
        val big = split(AdvanceRule.Fixed(IncomeSplit.MAX_FIXED_CENTS))
        assertEquals(100_000L, big.advanceCents(100_000))
        assertEquals(0L, big.balanceCents(100_000))
    }

    @Test fun ruleSurvivesTheRoundTrip() {
        assertEquals(AdvanceRule.Percent(35), AdvanceRule.decode(AdvanceRule.Percent(35).encode()))
        assertEquals(AdvanceRule.Fixed(150_050), AdvanceRule.decode(AdvanceRule.Fixed(150_050).encode()))
        assertNull(AdvanceRule.decode("lixo"))
    }

    // ── competência ──────────────────────────────────────────────────────

    @Test fun generatedEntriesHaveCompetenceAndReceiptDate() {
        val rows = IncomePlanner.generate(template(), total, split(AdvanceRule.Percent(40)), ym(2026, 11), 5, "g")
        val adv = rows.first { it.incomePart == IncomePart.Advance }
        val bal = rows.first { it.incomePart == IncomePart.Balance }
        assertEquals(LocalDate.of(2026, 11, 20), adv.data.toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 5), bal.data.toLocalDate())
        assertEquals(ym(2026, 11), adv.competenceOf(ym(2026, 11)))
        assertEquals(ym(2026, 11), bal.competenceOf(ym(2026, 12)))
        assertEquals(total, adv.valorCentavos + bal.valorCentavos)
        assertTrue(adv.rendaPrincipal && !bal.rendaPrincipal)
    }

    @Test fun fullSalaryCountsOnceInItsCompetence() {
        val rows = apply(emptyList(), IncomePlan(insert = IncomePlanner.generate(template(), total, split(AdvanceRule.Percent(40)), ym(2026, 11), 5, "g")))
        assertEquals(total, IncomeCompetence.incomeCents(rows, ym(2026, 11)))
        assertEquals(total, IncomeCompetence.incomeCents(rows, ym(2026, 12)))
        // Pela data de recebimento, o saldo de novembro cai em dezembro.
        val cashNov = rows.transactionsInMonth(LocalDate.of(2026, 11, 1)).sumOf { it.valorCentavos }
        val cashDec = rows.transactionsInMonth(LocalDate.of(2026, 12, 1)).sumOf { it.valorCentavos }
        assertEquals(234_000L, cashNov)
        assertEquals(351_000L + 234_000L, cashDec) // saldo de nov + adiantamento de dez
    }

    @Test fun integralIncomeGroupsByReceiptMonthLikeBefore() {
        val rows = listOf(template())
        assertEquals(total, IncomeCompetence.incomeCents(rows, ym(2026, 11)))
        assertEquals(total, rows.transactionsInMonth(LocalDate.of(2026, 11, 1)).sumOf { it.valorCentavos })
    }

    @Test fun monthlyCloseUsesCompetenceNotReceiptDate() {
        val rows = apply(emptyList(), IncomePlan(insert = IncomePlanner.generate(template(), total, split(AdvanceRule.Percent(40)), ym(2026, 11), 5, "g")))
        val nov = MonthCashFlow.of(emptyList(), rows, emptyList(), ym(2026, 11), LocalDate.of(2026, 11, 12))
        assertEquals(234_000L, nov.toGetCents) // caixa: só o adiantamento caiu em novembro
        assertEquals(total, nov.fechamentoCents) // fechamento: o salário inteiro, uma vez
    }

    // ── saldo corrido atravessando a virada de mês ───────────────────────

    @Test fun runningBalanceCrossesTheMonthBoundary() {
        val rows = apply(emptyList(), IncomePlan(insert = IncomePlanner.generate(template(), total, split(AdvanceRule.Percent(40)), ym(2026, 11), 5, "g")))
        val expense = TransacaoEntity(
            id = 50, data = LocalDate.of(2026, 11, 25).toEpochMillis(), descricao = "Aluguel", valorCentavos = 180_000,
            categoria = "Moradia", contaOrigem = "Itaú", recorrente = false, origem = "manual",
        )
        val agenda = AgendaBalance.build(
            transacoes = rows + expense, contas = emptyList(), dividas = emptyList(),
            today = LocalDate.of(2026, 11, 12), months = 2,
        )
        // Hoje: nada saiu nem entrou ainda no app (a série começa em 20/11), saldo parte de zero.
        assertEquals(0L, agenda.todayCents)
        val nov = agenda.future.filter { YearMonth.from(it.date) == ym(2026, 11) }
        assertEquals(listOf(234_000L, 234_000L - 180_000L), nov.map { it.balanceAfterCents })
        // Dezembro começa com o saldo de fim de novembro, sem recomeçar do zero.
        assertEquals(234_000L - 180_000L, agenda.monthStart[ym(2026, 12)])
        val dec = agenda.future.filter { YearMonth.from(it.date) == ym(2026, 12) }
        assertEquals(234_000L - 180_000L + 351_000L, dec.first { it.cents == 351_000L }.balanceAfterCents)
        assertEquals(dec.last().balanceAfterCents, agenda.endOfNextMonthCents)
    }

    // ── regeneração ──────────────────────────────────────────────────────

    @Test fun regeneratingKeepsPaidEntriesAndReplacesTheFuture() {
        val today = LocalDate.of(2026, 11, 25) // adiantamento de nov (20/11) já pago; saldo (05/12) pendente
        val initial = apply(emptyList(), IncomePlan(insert = IncomePlanner.generate(template(), total, split(AdvanceRule.Percent(40)), ym(2026, 11), 5, "g")))
        val adv0 = initial.first { it.incomePart == IncomePart.Advance }
        val bal0 = initial.first { it.incomePart == IncomePart.Balance }

        val plan = IncomePlanner.regenerate(initial, total, split(AdvanceRule.Percent(50)), today)
        val after = apply(initial, plan)

        // A competência em curso (nov): o adiantamento pago não muda, o saldo pendente é salário − pago.
        val novReceipts = IncomeCompetence.receipts(after, ym(2026, 11))
        assertEquals(total, novReceipts.sumOf { it.valorCentavos })
        assertEquals(234_000L, novReceipts.first { it.data.toLocalDate() == LocalDate.of(2026, 11, 20) }.valorCentavos)
        assertEquals(351_000L, novReceipts.first { it.data.toLocalDate() == LocalDate.of(2026, 12, 5) }.valorCentavos)
        // A divisão nova (50%) vale da competência seguinte.
        val dec = IncomeCompetence.receipts(after, ym(2026, 12))
        assertEquals(total, dec.sumOf { it.valorCentavos })
        assertEquals(292_500L, dec.first { it.incomePart == IncomePart.Advance }.valorCentavos)
        assertEquals(292_500L, dec.first { it.incomePart == IncomePart.Balance }.valorCentavos)
        // O registro do adiantamento antigo termina em novembro; nada anterior a hoje foi apagado.
        assertEquals("2026-11", after.first { it.id == adv0.id }.recorrenteAte)
        assertTrue(after.any { it.id == bal0.id })
    }

    @Test fun regeneratingDoesNotTouchAnythingAlreadyPaid() {
        val today = LocalDate.of(2026, 12, 20) // novembro inteiro pago (20/11 e 05/12) e o adiantamento de dez também
        val initial = apply(emptyList(), IncomePlan(insert = IncomePlanner.generate(template(), total, split(AdvanceRule.Percent(40)), ym(2026, 11), 5, "g")))
        val plan = IncomePlanner.regenerate(initial, total, split(AdvanceRule.Fixed(300_000)), today)
        val after = apply(initial, plan)
        // Novembro ficou igual (40/60), dezembro com o adiantamento já pago intacto e o saldo pendente ajustado.
        val novBefore = IncomeCompetence.receipts(initial, ym(2026, 11)).sortedBy { it.data }.map { it.data to it.valorCentavos }
        assertEquals(novBefore, IncomeCompetence.receipts(after, ym(2026, 11)).sortedBy { it.data }.map { it.data to it.valorCentavos })
        val dec = IncomeCompetence.receipts(after, ym(2026, 12))
        assertEquals(total, dec.sumOf { it.valorCentavos })
        assertEquals(234_000L, dec.first { it.data.toLocalDate() == LocalDate.of(2026, 12, 20) }.valorCentavos)
        // Janeiro em diante: valor fixo de R$ 3.000 + saldo.
        val jan = IncomeCompetence.receipts(after, ym(2027, 1))
        assertEquals(300_000L, jan.first { it.incomePart == IncomePart.Advance }.valorCentavos)
        assertEquals(total - 300_000L, jan.first { it.incomePart == IncomePart.Balance }.valorCentavos)
    }

    @Test fun existingIntegralSalaryMovesToTwoPartsFromTheNextCompetence() {
        val today = LocalDate.of(2026, 11, 12) // salário integral de 05/11 já recebido
        val plan = IncomePlanner.regenerate(listOf(template()), total, split(AdvanceRule.Percent(40)), today)
        val after = apply(listOf(template()), plan)
        // Novembro continua um salário inteiro, recebido em 05/11.
        val nov = IncomeCompetence.receipts(after, ym(2026, 11))
        assertEquals(listOf(total), nov.map { it.valorCentavos })
        // Dezembro já vem em duas partes (20/12 e 05/01), somando uma vez.
        val dec = IncomeCompetence.receipts(after, ym(2026, 12))
        assertEquals(2, dec.size)
        assertEquals(total, dec.sumOf { it.valorCentavos })
    }

    @Test fun backToIntegralKeepsPaidAndRestartsIntegral() {
        val today = LocalDate.of(2026, 11, 25)
        val initial = apply(emptyList(), IncomePlan(insert = IncomePlanner.generate(template(), total, split(AdvanceRule.Percent(40)), ym(2026, 11), 5, "g")))
        val after = apply(initial, IncomePlanner.regenerate(initial, total, null, today))
        assertEquals(total, IncomeCompetence.incomeCents(after, ym(2026, 11)))
        val dec = IncomeCompetence.receipts(after, ym(2026, 12))
        assertEquals(listOf(total), dec.map { it.valorCentavos })
        assertNull(dec.single().rendaParte)
    }
}
