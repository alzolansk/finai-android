package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Saldo projetado contínuo e conciliação com o banco (refs. 3a e 3b). */
class ReconciliationTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun tx(id: Long, date: LocalDate, desc: String, cents: Long, tipo: String = "Gasto", recorrente: Boolean = false, categoria: String = "Moradia") = TransacaoEntity(
        id = id, data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = categoria,
        contaOrigem = "Itaú", recorrente = recorrente, tipo = tipo, origem = "manual",
    )

    private fun adjust(id: Long, date: LocalDate, cents: Long) = tx(id, date, ADJUSTMENT_LABEL, cents, ADJUSTMENT_TYPE, categoria = ADJUSTMENT_LABEL)
        .copy(origem = ADJUSTMENT_ORIGIN)

    // ── máscara e diferença ─────────────────────────────────────────────

    @Test fun maskFormatsPtBrWhileTyping() {
        assertEquals("", Reconciliation.mask(""))
        assertEquals("0,05", Reconciliation.mask("5"))
        assertEquals("12,34", Reconciliation.mask("1234"))
        assertEquals("1.234,56", Reconciliation.mask("123456"))
        assertEquals("1.234.567,89", Reconciliation.mask("123456789"))
        assertEquals("-1.234,56", Reconciliation.mask("-123456"))
        assertEquals("-", Reconciliation.mask("-"))
    }

    @Test fun parseReadsTheMaskedValueInCents() {
        assertEquals(283_040L, Reconciliation.parse("2.830,40"))
        assertEquals(-5_000L, Reconciliation.parse("-50,00"))
        assertNull(Reconciliation.parse(""))
        assertNull(Reconciliation.parse("-"))
        assertEquals("2.830,40", Reconciliation.format(283_040))
    }

    @Test fun differenceAndTone() {
        assertEquals(-29_000L, Reconciliation.difference(283_040, 312_040))
        assertNull(Reconciliation.difference(null, 100))
        assertEquals(DiffTone.Neutral, diffTone(0))
        assertEquals(DiffTone.Neutral, diffTone(null))
        assertEquals(DiffTone.BankLess, diffTone(-1))
        assertEquals(DiffTone.BankMore, diffTone(1))
        assertTrue(diffNote(-1).contains("gasto não lançado ou uma tarifa"))
        assertTrue(diffNote(1).contains("entrada não lançada"))
        assertTrue(diffNote(0).startsWith("Tudo certo"))
    }

    // ── aviso da Início ─────────────────────────────────────────────────

    @Test fun noticeAsksAfterThirtyDaysOrNever() {
        assertEquals(ReconciliationNotice.Stale(null), Reconciliation.notice(null, today))
        assertNull(Reconciliation.notice(ReconciliationState(today.minusDays(30), 0, 0), today))
        assertEquals(ReconciliationNotice.Stale(31), Reconciliation.notice(ReconciliationState(today.minusDays(31), 0, 0), today))
        assertEquals(ReconciliationNotice.Stale(34), Reconciliation.notice(ReconciliationState(today.minusDays(34), 0, 0), today))
    }

    @Test fun noticeTurnsGreenRightAfterReconciling() {
        assertEquals(ReconciliationNotice.DoneToday(-29_000), Reconciliation.notice(ReconciliationState(today, 283_040, -29_000), today))
    }

    // ── o ajuste não é gasto nem receita, mas mexe no saldo ──────────────

    @Test fun adjustmentMovesTheBalanceButNotReportsOrBudgets() {
        val adj = adjust(9, today, -29_000)
        assertEquals(1, adj.freeCashSign) // valor já com sinal
        val flow = MonthCashFlow.of(emptyList(), listOf(adj), emptyList(), YearMonth.from(today), today)
        assertEquals(0L, flow.toPayCents)
        assertEquals(0L, flow.toGetCents)
        assertEquals(0L, flow.fechamentoCents)
        val agenda = AgendaBalance.build(listOf(adj), emptyList(), emptyList(), today)
        assertEquals(-29_000L, agenda.todayCents)
    }

    // ── saldo contínuo, meses, menor saldo ───────────────────────────────

    @Test fun balanceCarriesAcrossMonthsAndBarsUseTheMonthEnd() {
        val rows = listOf(
            tx(1, LocalDate.of(2026, 11, 5), "Salário", 585_000, "Receita"),
            tx(2, LocalDate.of(2026, 11, 20), "Aluguel", 180_000),
            tx(3, LocalDate.of(2026, 12, 5), "Salário dez", 585_000, "Receita"),
            tx(4, LocalDate.of(2026, 12, 8), "Aluguel dez", 180_000),
            tx(5, LocalDate.of(2027, 1, 10), "Viagem", 900_000),
        )
        val agenda = AgendaBalance.build(rows, emptyList(), emptyList(), today)
        assertEquals(585_000L, agenda.todayCents)
        assertEquals(4, agenda.monthEnds.size)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2)), agenda.monthEnds.map { it.month })
        // O saldo final de um mês é o inicial do seguinte.
        assertEquals(405_000L, agenda.monthEnds[0].cents)
        assertEquals(agenda.monthEnds[0].cents, agenda.monthStart[YearMonth.of(2026, 12)])
        assertEquals(810_000L, agenda.monthEnds[1].cents)
        assertEquals(-90_000L, agenda.monthEnds[2].cents)
        assertEquals(-90_000L, agenda.monthEnds[3].cents) // mês sem movimento carrega o saldo
        assertEquals(agenda.monthEnds[1].cents, agenda.monthStart[YearMonth.of(2027, 1)])
        // Menor saldo previsto: o do dia da viagem.
        assertEquals(LowestBalance(-90_000L, LocalDate.of(2027, 1, 10)), agenda.lowest)
    }

    @Test fun lowestBalanceCanBeTodayAndTonesFollowThresholds() {
        val agenda = AgendaBalance.build(listOf(tx(1, today.minusDays(2), "Salário", 50_000, "Receita")), emptyList(), emptyList(), today)
        assertEquals(LowestBalance(50_000L, today), agenda.lowest)
        assertEquals(BalanceTone.Negative, balanceTone(-1))
        assertEquals(BalanceTone.Low, balanceTone(99_999))
        assertEquals(BalanceTone.Normal, balanceTone(100_000))
    }

    // ── conciliar recalcula tudo a partir do mês do ajuste ───────────────

    @Test fun adjustmentFromAnEarlierMonthKeepsTheBalanceAnchored() {
        val base = listOf(
            tx(1, LocalDate.of(2026, 10, 5), "Salário out", 500_000, "Receita"),
            tx(2, LocalDate.of(2026, 10, 25), "Mercado", 120_000),
            tx(3, LocalDate.of(2026, 11, 5), "Salário nov", 500_000, "Receita"),
        )
        // Fase 1: o saldo é contínuo e parte do primeiro lançamento, com ou sem ajuste:
        // outubro (500 − 120) + novembro (500) = R$ 8.800.
        val semAjuste = AgendaBalance.build(base, emptyList(), emptyList(), today)
        assertEquals(880_000L, semAjuste.todayCents)
        assertEquals(YearMonth.of(2026, 10), semAjuste.startMonth)
        // O usuário conciliou em 28/10: o banco tinha R$ 4.000 e o FinAI mostrava R$ 3.800 (ajuste +200).
        val adj = adjust(4, LocalDate.of(2026, 10, 28), 20_000)
        val agenda = AgendaBalance.build(base + adj, emptyList(), emptyList(), today)
        assertEquals(YearMonth.of(2026, 10), agenda.startMonth)
        // O ajuste soma ao saldo contínuo (R$ 8.800 + 200 = R$ 9.000), e o mês anterior não aparece no bloco de hoje.
        assertEquals(900_000L, agenda.todayCents)
        assertTrue(agenda.past.all { YearMonth.from(it.date) == YearMonth.of(2026, 11) })
        assertEquals(1, agenda.past.size)
    }

    @Test fun reconcilingMakesTheRegisteredBalanceMatchTheBank() {
        val rows = listOf(tx(1, LocalDate.of(2026, 11, 5), "Salário", 585_000, "Receita"), tx(2, today, "Aluguel", 180_000))
        val before = AgendaBalance.build(rows, emptyList(), emptyList(), today).todayCents
        val bank = 400_000L
        val diff = Reconciliation.difference(bank, before)!!
        assertEquals(-5_000L, diff)
        val after = AgendaBalance.build(rows + adjust(9, today, diff), emptyList(), emptyList(), today)
        assertEquals(bank, after.todayCents)
        // Excluir o ajuste volta ao saldo de antes.
        assertEquals(before, AgendaBalance.build(rows, emptyList(), emptyList(), today).todayCents)
        assertFalse(after.past.isEmpty())
    }
}
