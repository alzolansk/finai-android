package com.finai.app.domain.scenario

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Regra 6 (planning-v2, fase 8): menor saldo de cada mês com e sem a compra, por forma de pagamento. */
class PurchaseSimulatorV2Test {
    private val today = LocalDate.of(2026, 11, 8)
    private fun receita(day: LocalDate, cents: Long, recorrente: Boolean, main: Boolean = false) = TransacaoEntity(
        data = day.toEpochMillis(), descricao = if (main) "Salário" else "Entrada", valorCentavos = cents, categoria = "Receita",
        contaOrigem = "Nubank", recorrente = recorrente, tipo = "Receita", origem = "manual", rendaPrincipal = main,
    )

    /** R$ 1.500 em conta hoje (entrada de 01/11) e salário de R$ 1.000 todo dia 10, a partir de dezembro. */
    private val base = listOf(
        receita(LocalDate.of(2026, 11, 1), 150_000, recorrente = false),
        receita(LocalDate.of(2026, 12, 10), 100_000, recorrente = true, main = true),
    )

    private fun sim(value: Long, discount: Double = 0.0, fits: Long? = null, due: LocalDate? = null) =
        PurchaseSimulatorV2.simulate(emptyList(), base, emptyList(), today, value, marginCents = 30_000, firstCardDue = due,
            pixDiscountPercent = discount, fitsToSaveCents = fits)

    @Test fun fiveWaysAreSimulated() {
        assertEquals(listOf("Pix", "1×", "3×", "6×", "10×"), sim(100_000).options.map { it.way.label })
    }

    @Test fun withoutThePurchaseTheLowestIsTodaysBalance() {
        val nov = sim(100_000).option(PurchaseWay(0))!!.months.first { it.month == YearMonth.of(2026, 11) }
        assertEquals(150_000L, nov.withoutCents)
    }

    @Test fun pixTodayIsTightAndOneTimeOnCardIsBest() {
        val s = sim(100_000)
        // Pix hoje: sobra R$ 500, só R$ 200 acima da margem de R$ 300.
        assertEquals(PurchaseVerdictV2.Apertado, s.option(PurchaseWay(0))!!.verdict)
        assertEquals(50_000L, s.option(PurchaseWay(0))!!.worst.withCents)
        // 1× vence em 08/12, antes do salário de 10/12: dezembro também desce a R$ 500.
        // Em 3× a primeira parcela é de R$ 333 e o saldo nunca fica perto da margem.
        assertEquals(PurchaseVerdictV2.Apertado, s.option(PurchaseWay(1))!!.verdict)
        assertEquals(PurchaseWay(3), s.best)
        assertEquals(PurchaseVerdictV2.Cabe, s.option(PurchaseWay(3))!!.verdict)
    }

    @Test fun cardDueAfterTheSalaryMakesOneTimeTheBest() {
        val s = sim(100_000, due = LocalDate.of(2026, 12, 15))
        assertEquals(PurchaseVerdictV2.Cabe, s.option(PurchaseWay(1))!!.verdict)
        assertEquals(PurchaseWay(1), s.best)
        assertEquals(LocalDate.of(2026, 12, 15), s.option(PurchaseWay(3))!!.firstDue)
    }

    @Test fun discountOnlyOnPix() {
        val s = sim(100_000, discount = 10.0)
        assertEquals(90_000L, s.option(PurchaseWay(0))!!.totalCents)
        assertEquals(100_000L, s.option(PurchaseWay(3))!!.totalCents)
    }

    @Test fun installmentsAddUpToTheValue() {
        val o = sim(100_000).option(PurchaseWay(3))!!
        assertEquals(33_334L, o.installmentCents)
        val totalOut = o.months.sumOf { it.withoutCents - it.withCents }
        assertTrue(totalOut > 0)
        assertEquals(100_000L, o.totalCents)
    }

    @Test fun tooExpensiveDoesNotFitAnyWay() {
        val s = sim(2_000_000)
        assertTrue(s.options.all { it.verdict == PurchaseVerdictV2.NaoCabe })
        assertNull(s.best)
        assertTrue(s.sentence(s.options.first()), s.sentence(s.options.first()).startsWith("Faltariam"))
    }

    @Test fun goalDelayIsValueOverWhatFitsToSave() {
        assertEquals(4, sim(100_000, fits = 30_000).goalDelayMonths)
        assertNull(sim(100_000).goalDelayMonths)
    }

    @Test fun factsBlockCarriesEveryNumberTheReplyMayUse() {
        val s = sim(100_000, fits = 30_000)
        val facts = s.factsBlock("Tênis")
        assertTrue(facts, facts.contains("R$ 1.000"))
        assertTrue(facts, facts.contains("Margem de segurança: R$ 300"))
        assertTrue(facts, facts.contains("Melhor forma"))
        assertTrue(ScenarioReplyGuard.accepts("Em 3 vezes de R$ 333 fica melhor.", facts))
        assertTrue(!ScenarioReplyGuard.accepts("Sobra R$ 7.777 no fim.", facts))
    }
}
