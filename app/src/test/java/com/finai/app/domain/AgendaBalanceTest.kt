package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.balance.FreeToSpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Agenda "A seguir" com o saldo contínuo (planning-v2, fase 4): o "fica R$ X" e o menor saldo
 * seguem as regras do livre, conta atrasada de mês passado aparece hoje, conta paga adiantada
 * fica no lugar para reabrir, e nada zera na virada do mês.
 */
class AgendaBalanceTest {
    private val today = LocalDate.of(2026, 11, 8)

    private fun tx(id: Long, date: LocalDate, desc: String, cents: Long, tipo: String = "Gasto", recorrente: Boolean = false) = TransacaoEntity(
        id = id, data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = if (tipo == "Receita") "Salário" else "Moradia",
        contaOrigem = "Itaú", recorrente = recorrente, tipo = tipo, origem = "manual",
    )

    private fun adjust(cents: Long) = tx(1, today, ADJUSTMENT_LABEL, cents, ADJUSTMENT_TYPE).copy(categoria = ADJUSTMENT_LABEL, origem = ADJUSTMENT_ORIGIN)

    private fun conta(id: Long, nome: String, cents: Long, due: LocalDate, status: String = "pendente", tipo: String = "a_pagar") = ContaEntity(
        id = id, nome = nome, valorCentavos = cents, vencimento = due.toEpochMillis(), status = status, tipo = tipo, recorrente = false,
    )

    private val salary = tx(2, LocalDate.of(2026, 11, 20), "Salário", 300_000, "Receita", recorrente = true).copy(rendaPrincipal = true)

    @Test fun overdueBillFromLastMonthLeavesToday() {
        val contas = listOf(conta(1, "Luz", 20_000, LocalDate.of(2026, 10, 20)), conta(2, "Aluguel", 100_000, LocalDate.of(2026, 11, 25)))
        val b = AgendaBalance.build(listOf(adjust(500_000), salary), contas, emptyList(), today)
        val luz = b.future.first()
        assertEquals("Luz", luz.descricao)
        assertEquals(today, luz.date)
        assertEquals(LocalDate.of(2026, 10, 20), luz.movedFrom)
        assertEquals(480_000L, luz.balanceAfterCents)
        // Mesmo número da Hoje: menor saldo antes da renda e o livre partem da mesma série.
        val free = FreeToSpend.of(contas, listOf(adjust(500_000), salary), emptyList(), today, marginCents = 0)!!
        assertEquals(free.lowest.cents, b.lowestBeforeIncome.cents)
        assertEquals(free.lowest.date, b.lowestBeforeIncome.date)
    }

    @Test fun overdueReceivableShowsButDoesNotCount() {
        val contas = listOf(conta(1, "Reembolso", 30_000, LocalDate.of(2026, 11, 3), tipo = "a_receber"))
        val b = AgendaBalance.build(listOf(adjust(100_000)), contas, emptyList(), today)
        assertEquals(100_000L, b.todayCents)
        assertTrue(b.future.isEmpty())
        assertEquals(listOf("Reembolso"), b.unconfirmedInflows.map { it.descricao })
        val rows = AgendaAhead.rows(b)
        val move = rows.filterIsInstance<AgendaAhead.Row.Move>().single()
        assertEquals(AgendaAhead.MoveKind.Unconfirmed, move.kind)
        assertEquals(LocalDate.of(2026, 11, 3), move.movement.movedFrom)
    }

    @Test fun billPaidAheadStaysInPlaceAndIsAlreadyInTheBalance() {
        val contas = listOf(
            conta(1, "Internet", 12_000, LocalDate.of(2026, 11, 15), status = "pago"),
            conta(2, "Aluguel", 100_000, LocalDate.of(2026, 11, 25)),
        )
        val b = AgendaBalance.build(listOf(adjust(300_000)), contas, emptyList(), today)
        assertEquals(288_000L, b.todayCents)
        assertTrue(b.past.none { it.descricao == "Internet" })
        assertEquals(listOf("Internet"), b.settledAhead.map { it.descricao })
        val moves = AgendaAhead.rows(b).filterIsInstance<AgendaAhead.Row.Move>()
        assertEquals(listOf("Internet", "Aluguel"), moves.map { it.movement.descricao })
        assertEquals(AgendaAhead.MoveKind.SettledAhead, moves[0].kind)
        // Pagar adiantado não muda o saldo depois do vencimento.
        assertEquals(188_000L, moves[1].movement.balanceAfterCents)
    }

    @Test fun billPaidAfterDueGoesToThePastBlock() {
        val contas = listOf(conta(1, "Luz", 20_000, LocalDate.of(2026, 11, 3), status = "pago"))
        val b = AgendaBalance.build(listOf(adjust(100_000)), contas, emptyList(), today)
        assertEquals(listOf("Luz"), b.past.map { it.descricao } - ADJUSTMENT_LABEL)
        assertTrue(b.settledAhead.isEmpty())
    }

    @Test fun monthTurnCarriesTheBalanceAndExtrasAppear() {
        val decimo = tx(5, LocalDate.of(2026, 12, 18), "13º (1ª parcela)", 300_000, "Receita").copy(extra = true)
        val contas = listOf(conta(1, "Aluguel", 140_000, LocalDate.of(2026, 11, 25)))
        val b = AgendaBalance.build(listOf(adjust(348_000), salary, decimo), contas, emptyList(), today)
        val rows = AgendaAhead.rows(b)
        val dec = rows.filterIsInstance<AgendaAhead.Row.MonthStart>().first()
        assertEquals(YearMonth.of(2026, 12), dec.month)
        // 3.480 + 3.000 (salário 20/11) − 1.400 (aluguel): começa dezembro com 5.080, sem zerar.
        assertEquals(508_000L, dec.startCents)
        val extra = rows.filterIsInstance<AgendaAhead.Row.Move>().first { it.movement.descricao.startsWith("13º") }
        assertEquals(AgendaAhead.MoveKind.Counted, extra.kind)
        // Extra não abre período: só a renda principal ganha cabeçalho do dia.
        val incomeDays = rows.filterIsInstance<AgendaAhead.Row.IncomeDay>()
        assertTrue(incomeDays.isNotEmpty())
        assertTrue(incomeDays.none { it.date == LocalDate.of(2026, 12, 18) })
        assertEquals(LocalDate.of(2026, 11, 20), incomeDays.first().date)
    }

    @Test fun lowestIsMarkedOnlyWhenTheBalanceDrops() {
        val b = AgendaBalance.build(listOf(adjust(100_000), salary), emptyList(), emptyList(), today)
        assertFalse(AgendaAhead.rows(b).filterIsInstance<AgendaAhead.Row.Move>().any { it.lowest })
        assertEquals(AgendaAhead.Row.Today(today, 100_000), AgendaAhead.rows(b).first())
    }

    @Test fun monthNumbersCloseTheOldHomeBalance() {
        val contas = listOf(conta(1, "Aluguel", 140_000, LocalDate.of(2026, 11, 25)))
        val flow = MonthCashFlow.of(contas, listOf(adjust(348_000), salary, tx(9, LocalDate.of(2026, 11, 2), "Mercado", 24_000)), emptyList(), YearMonth.of(2026, 11), today)
        assertEquals(300_000L, flow.entraCents)
        assertEquals(164_000L, flow.saiCents)
        assertEquals(flow.fechamentoCents, flow.entraCents - flow.saiCents)
    }
}
