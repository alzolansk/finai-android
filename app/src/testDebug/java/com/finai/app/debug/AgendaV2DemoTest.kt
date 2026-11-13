package com.finai.app.debug

import com.finai.app.domain.AgendaAhead
import com.finai.app.domain.AgendaBalance
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.balance.FreeToSpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Os números do quadro `v2/Agenda` (aba "A seguir") com o cenário de demonstração: em conta R$ 3.480,
 * fica 2.160 → 2.050 → 1.930 → menor saldo 1.520 em 18/11 (o mesmo da Hoje), adiantamento fica
 * 3.920, aluguel 2.520 e dezembro começa com R$ 2.520.
 */
class AgendaV2DemoTest {
    private val s = DemoScenarioV2
    private val balance = AgendaBalance.build(s.transacoes, s.contas, s.dividas, s.today)
    private val rows = AgendaAhead.rows(balance)

    @Test fun aheadMatchesThePrototype() {
        assertEquals(AgendaAhead.Row.Today(s.today, 348_000), rows.first())
        val moves = rows.filterIsInstance<AgendaAhead.Row.Move>()
        val novembro = moves.takeWhile { it.movement.date.monthValue == 11 }
        assertEquals(
            listOf(216_000L, 205_000L, 193_000L, 152_000L, 392_000L, 252_000L),
            novembro.map { it.movement.balanceAfterCents },
        )
        assertEquals(listOf(10, 12, 15, 18, 20, 25), novembro.map { it.movement.date.dayOfMonth })
        val lowest = moves.single { it.lowest }
        assertTrue(lowest.movement.descricao.contains("Picpay"))
        assertEquals(LocalDate.of(2026, 11, 18), lowest.movement.date)

        val free = FreeToSpend.of(s.contas, s.transacoes, s.dividas, s.today)!!
        assertEquals(free.lowest.cents, balance.lowestBeforeIncome.cents)

        val incomeDay = rows.filterIsInstance<AgendaAhead.Row.IncomeDay>().first()
        assertEquals(LocalDate.of(2026, 11, 20), incomeDay.date)
        val dez = rows.filterIsInstance<AgendaAhead.Row.MonthStart>().first()
        assertEquals(YearMonth.of(2026, 12), dez.month)
        assertEquals(252_000L, dez.startCents)
        val saldo = moves.first { it.movement.date == LocalDate.of(2026, 12, 5) }
        assertEquals(612_000L, saldo.movement.balanceAfterCents)
    }

    @Test fun monthTabNumbers() {
        val flow = MonthCashFlow.of(s.contas, s.transacoes, s.dividas, YearMonth.of(2026, 11), s.today)
        // Entra pela competência: adiantamento de novembro + saldo que cai em 05/12.
        assertEquals(600_000L, flow.entraCents)
        assertEquals(336_000L, flow.saiCents)
        assertEquals(264_000L, flow.fechamentoCents)
        assertTrue(flow.competenceShifted)
    }
}
