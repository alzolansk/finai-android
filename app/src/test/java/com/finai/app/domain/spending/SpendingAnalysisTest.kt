package com.finai.app.domain.spending

import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Fase 6 (planning-v2): Gastos é consumo, não caixa. */
class SpendingAnalysisTest {
    private val today = LocalDate.of(2026, 11, 8)
    private var nextId = 1L
    private fun gasto(day: LocalDate, cents: Long, cat: String, desc: String = cat, tipo: String = "Gasto", conta: String = "Nubank") =
        TransacaoEntity(id = nextId++, data = day.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = cat,
            contaOrigem = conta, recorrente = false, tipo = tipo, origem = "manual")

    private val nov = YearMonth.of(2026, 11)
    private val oct = YearMonth.of(2026, 10)

    @Test fun onlyConsumptionCounts() {
        val list = listOf(
            gasto(today, 5_000, "Alimentação", "iFood"),
            gasto(today, 300_000, "Salário", tipo = "Receita"),
            gasto(today, 20_000, "Transferência", tipo = "Transferencia"),
            gasto(today, 7_000, "Ajuste", tipo = "Ajuste"),
        )
        val a = SpendingAnalysis.of(list, emptyMap(), SpendingPeriod(2026, 11), today)
        assertEquals(5_000L, a.totalCents)
        assertEquals(listOf("Alimentação"), a.categories.map { it.categoria })
    }

    @Test fun futureEntriesOfTheMonthDoNotCountYet() {
        val list = listOf(gasto(today, 5_000, "Lazer"), gasto(today.plusDays(3), 9_000, "Lazer"))
        assertEquals(5_000L, SpendingAnalysis.of(list, emptyMap(), SpendingPeriod(2026, 11), today).totalCents)
    }

    @Test fun currentMonthComparesWithTheSameDayOfLastMonth() {
        val list = listOf(
            gasto(nov.atDay(3), 10_000, "Lazer"),
            gasto(oct.atDay(5), 4_000, "Lazer"),
            gasto(oct.atDay(20), 50_000, "Lazer"), // depois do dia 8: fora da comparação
        )
        val a = SpendingAnalysis.of(list, emptyMap(), SpendingPeriod(2026, 11), today)
        assertEquals("R$ 60 a mais do que até o dia 8 de outubro.", a.comparison)
    }

    @Test fun closedMonthComparesWithTheWholePreviousMonth() {
        val list = listOf(gasto(oct.atDay(10), 30_000, "Moradia"), gasto(YearMonth.of(2026, 9).atDay(10), 40_000, "Moradia"))
        val a = SpendingAnalysis.of(list, emptyMap(), SpendingPeriod(2026, 10), today)
        assertEquals("R$ 100 a menos do que em setembro.", a.comparison)
    }

    @Test fun trendHasSixMonthsEndingInThePeriod() {
        val a = SpendingAnalysis.of(listOf(gasto(oct.atDay(1), 1_000, "Lazer")), emptyMap(), SpendingPeriod(2026, 11), today)
        assertEquals(6, a.trend.size)
        assertEquals(nov, a.trend.last().first)
        assertEquals(1_000L, a.trend.first { it.first == oct }.second)
    }

    @Test fun limitWithoutSpendingStillShowsInCurrentMonth() {
        val a = SpendingAnalysis.of(emptyList(), mapOf("Saúde" to 20_000L), SpendingPeriod(2026, 11), today)
        assertEquals(listOf("Saúde"), a.categories.map { it.categoria })
        assertEquals(20_000L, a.categories.single().limitCents)
    }

    @Test fun paceWarnsWhenTheLimitWillBreakBeforeMonthEnd() {
        // R$ 400 em 8 dias = R$ 50/dia; faltam R$ 100 até R$ 500: 2 dias.
        val (note, tone) = SpendingAnalysis.paceNote(40_000, 50_000, nov, today)
        assertEquals("Nesse ritmo passa do limite em 2 dias", note)
        assertEquals(SpendTone.Warn, tone)
    }

    @Test fun paceIsCalmWhenTheRhythmFits() {
        val (note, tone) = SpendingAnalysis.paceNote(4_000, 100_000, nov, today)
        assertEquals("Restam R$ 960 até o fim do mês", note)
        assertEquals(SpendTone.Ok, tone)
    }

    @Test fun overTheLimit() {
        val (note, tone) = SpendingAnalysis.paceNote(60_000, 50_000, nov, today)
        assertEquals("Passou R$ 100 do limite", note)
        assertEquals(SpendTone.Over, tone)
    }

    @Test fun merchantsAreGroupedByNormalizedName() {
        val list = listOf(gasto(today, 2_000, "Alimentação", "IFOOD 99887766"), gasto(today.minusDays(1), 3_000, "Alimentação", "iFood"), gasto(today, 1_000, "Transporte", "Uber"))
        val a = SpendingAnalysis.of(list, emptyMap(), SpendingPeriod(2026, 11), today)
        assertEquals(5_000L, a.merchants.first().cents)
        assertEquals(2, a.merchants.first().count)
    }

    @Test fun yearPeriodAveragesTheMonthsSoFar() {
        val list = (1..11).map { m -> gasto(YearMonth.of(2026, m).atDay(1), 11_000, "Lazer") }
        val a = SpendingAnalysis.of(list, emptyMap(), SpendingPeriod(2026), today)
        assertEquals(121_000L, a.totalCents)
        assertEquals("Média de R$ 110 por mês em 2026.", a.comparison)
        assertTrue(a.categories.all { it.limitCents == null })
    }

    @Test fun searchIgnoresAccentsAndCase() {
        val list = listOf(gasto(today, 1_000, "Saúde", "Farmácia São João"), gasto(today, 2_000, "Lazer", "Cinema"))
        assertEquals(1, SpendingAnalysis.search(list, "farmacia").size)
        assertEquals(1, SpendingAnalysis.search(list, "SAUDE").size)
        assertEquals(2, SpendingAnalysis.search(list, "nubank").size)
        assertTrue(SpendingAnalysis.search(list, "f").isEmpty())
        assertTrue(SpendingAnalysis.search(list, "cinema", SpendingPeriod(2025)).isEmpty())
    }

    @Test fun subscriptionsCountConsecutiveMonths() {
        val sub = AssinaturaEntity(nome = "Netflix", valorCentavos = 5_590, ultimoUso = null, status = "ativa")
        val list = listOf(oct, YearMonth.of(2026, 9), YearMonth.of(2026, 8)).map { gasto(it.atDay(12), 5_590, "Assinaturas", "Netflix.com") }
        val line = SubscriptionLines.of(listOf(sub), list, today).single()
        assertEquals(3, line.monthsCharged)
        assertEquals("Cobrada há 3 meses seguidos", line.note)
    }

    @Test fun cancelledSubscriptionSaysSo() {
        val sub = AssinaturaEntity(nome = "Spotify", valorCentavos = 2_190, ultimoUso = null, status = "cancelada")
        assertEquals("Cancelada", SubscriptionLines.of(listOf(sub), emptyList(), today).single().note)
    }
}

/** Regra 10: média de três meses fechados, −5% onde estourou, arredondado a R$ 10. */
class LimitSuggestionTest {
    private val today = LocalDate.of(2026, 11, 8)
    private fun g(month: YearMonth, cents: Long, cat: String) =
        TransacaoEntity(data = month.atDay(10).toEpochMillis(), descricao = cat, valorCentavos = cents, categoria = cat,
            contaOrigem = "Nubank", recorrente = false, origem = "manual")
    private val months = (1L..3L).map { YearMonth.from(today).minusMonths(it) }

    @Test fun averageRoundedToTenReais() {
        val list = months.zip(listOf(30_000L, 32_000L, 34_400L)).map { (m, c) -> g(m, c, "Alimentação") }
        val p = LimitSuggestion.suggest(list, emptyMap(), today).single()
        assertEquals(32_133L, p.averageCents)
        assertEquals(32_000L, p.suggestedCents)
        assertFalse(p.overBefore)
    }

    @Test fun fivePercentLessWhereItBrokeTheLimit() {
        val list = months.map { g(it, 50_000, "Lazer") }
        val p = LimitSuggestion.suggest(list, mapOf("Lazer" to 40_000L), today).single()
        assertTrue(p.overBefore)
        assertEquals(48_000L, p.suggestedCents) // 47.500 → R$ 480
    }

    @Test fun currentMonthDoesNotEnterTheAverage() {
        val list = listOf(g(YearMonth.from(today), 90_000, "Lazer"))
        assertTrue(LimitSuggestion.suggest(list, emptyMap(), today).isEmpty())
    }

    @Test fun sameAsCurrentLimitIsNotProposed() {
        val list = months.map { g(it, 20_000, "Transporte") }
        assertTrue(LimitSuggestion.suggest(list, mapOf("Transporte" to 20_000L), today).isEmpty())
    }

    @Test fun monthWithoutDataDoesNotPullTheAverageDown() {
        val list = listOf(g(months[0], 30_000, "Saúde"), g(months[1], 30_000, "Saúde"))
        assertEquals(30_000L, LimitSuggestion.suggest(list, emptyMap(), today).single().suggestedCents)
    }
}
