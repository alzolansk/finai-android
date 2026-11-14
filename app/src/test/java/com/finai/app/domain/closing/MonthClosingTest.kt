package com.finai.app.domain.closing

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Fase 10 (planning-v2): o mês que fechou, com quatro passos que a resposta não repete. */
class MonthClosingTest {
    /** Dia 3 de novembro: fecha outubro. */
    private val today = LocalDate.of(2026, 11, 3)
    private val oct = YearMonth.of(2026, 10)
    private fun t(month: YearMonth, day: Int, cents: Long, desc: String, cat: String, tipo: String = "Gasto") =
        TransacaoEntity(data = month.atDay(day).toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = cat,
            contaOrigem = "Nubank", recorrente = false, tipo = tipo, origem = "manual")

    /** Três meses anteriores com luz (R$ 140) e lazer de R$ 200; outubro sem luz e com lazer de R$ 500. */
    private val history = buildList {
        listOf(oct.minusMonths(1), oct.minusMonths(2), oct.minusMonths(3)).forEach { m ->
            add(t(m, 5, 400_000, "Salário", "Receita", "Receita"))
            add(t(m, 10, 14_000, "Enel Luz", "Moradia"))
            add(t(m, 15, 20_000, "Cinema", "Lazer"))
        }
        add(t(oct, 5, 400_000, "Salário", "Receita", "Receita"))
        add(t(oct, 15, 50_000, "Cinema", "Lazer"))
    }

    private fun closing(answers: Map<String, String> = emptyMap(), goal: Pair<Long, String>? = 1L to "Chile", free: Long = 1_000_000) =
        MonthClosing.of(emptyList(), history, emptyList(), emptyMap(), goal, free, answers, today)

    @Test fun closesThePreviousMonth() {
        val c = closing()
        assertEquals(oct, c.month)
        assertEquals(400_000L, c.inCents)
        assertEquals(50_000L, c.outCents)
        assertEquals(350_000L, c.leftCents)
        assertEquals(366_000L, c.averageLeftCents)
    }

    @Test fun biggestDifferenceAgainstTheAverage() {
        val c = closing()
        assertEquals("Lazer", c.diffs.first().categoria)
        assertEquals(30_000L, c.diffs.first().diffCents)
        assertEquals("Lazer", c.pattern?.categoria)
    }

    @Test fun recurringChargeThatDidNotComeIsMissing() {
        val c = closing()
        assertEquals("Enel Luz", c.missing.single().name)
        assertEquals("Lançar R$ 140", c.steps.first { it.id == ClosingStepId.Missing }.action)
    }

    @Test fun surplusIsRoundedAndCappedByTheFree() {
        assertEquals(350_000L, closing().surplusCents)
        assertEquals(12_000L, closing(free = 12_345).surplusCents)
        assertNull(closing(goal = null).steps.first { it.id == ClosingStepId.Surplus }.action)
    }

    @Test fun answeredStepsAreDoneAndNotAskedAgain() {
        val c = closing(mapOf("faltou" to "lancado", "limites" to "pulado"))
        assertEquals(2, c.doneCount)
        assertTrue(c.pending)
        val all = closing(ClosingStepId.values().associate { it.key to "ok" })
        assertFalse(all.pending)
        assertFalse(MonthClosing.showCard(all, today))
    }

    @Test fun cardShowsOnlyInTheFirstWeekUnlessStarted() {
        val c = closing()
        assertTrue(MonthClosing.showCard(c, today))
        val late = LocalDate.of(2026, 11, 20)
        val fresh = MonthClosing.of(emptyList(), history, emptyList(), emptyMap(), null, 0, emptyMap(), late)
        assertFalse(MonthClosing.showCard(fresh, late))
        val started = MonthClosing.of(emptyList(), history, emptyList(), emptyMap(), null, 0, mapOf("faltou" to "ok"), late)
        assertTrue(MonthClosing.showCard(started, late))
    }

    @Test fun emptyMonthHasNothingToClose() {
        assertTrue(MonthClosing.of(emptyList(), emptyList(), emptyList(), emptyMap(), null, 0, emptyMap(), today).empty)
    }

    @Test fun summaryAndFactsHaveTheSameNumbers() {
        val c = closing()
        assertTrue(c.summary(), c.summary().startsWith("Em outubro entraram R$ 4.000 e saíram R$ 500: sobraram R$ 3.500."))
        assertTrue(c.factsBlock().contains("Enel Luz"))
        assertTrue(com.finai.app.domain.scenario.ScenarioReplyGuard.accepts("Sobraram R$ 3.500 e o lazer pesou.", c.factsBlock()))
    }
}
