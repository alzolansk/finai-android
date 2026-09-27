package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtCalculatorTest {

    private val today = LocalDate.of(2026, 1, 1)

    @Test
    fun `orders by interest rate, not balance`() {
        val small = DividaEntity(nome = "Pequena e cara", valorOriginalCentavos = 10_000, valorAbertoCentavos = 10_000, taxaJurosMensalBasisPoints = 1000, parcelasRestantes = 0, valorParcelaCentavos = 0)
        val big = DividaEntity(nome = "Grande e barata", valorOriginalCentavos = 1_000_000, valorAbertoCentavos = 1_000_000, taxaJurosMensalBasisPoints = 50, parcelasRestantes = 0, valorParcelaCentavos = 0)
        val summary = DebtCalculator.summarize(listOf(big, small), today)
        assertEquals("Pequena e cara", summary.ordered[0].divida.nome)
        assertEquals("Grande e barata", summary.ordered[1].divida.nome)
    }

    @Test
    fun `monthly interest is balance times rate`() {
        val debt = DividaEntity(nome = "D", valorOriginalCentavos = 100_000, valorAbertoCentavos = 100_000, taxaJurosMensalBasisPoints = 500, parcelasRestantes = 0, valorParcelaCentavos = 0)
        val summary = DebtCalculator.summarize(listOf(debt), today)
        // 100_000 cents * 5% = 5_000 cents
        assertEquals(5_000L, summary.totalMonthlyInterestCents)
    }

    @Test
    fun `payment that does not cover interest is flagged unpayable`() {
        val debt = DividaEntity(nome = "Rotativo", valorOriginalCentavos = 100_000, valorAbertoCentavos = 100_000, taxaJurosMensalBasisPoints = 1500, parcelasRestantes = 1, valorParcelaCentavos = 10_000)
        val summary = DebtCalculator.summarize(listOf(debt), today)
        assertTrue(summary.hasUnpayableDebt)
        assertNull(summary.debtFreeDate)
    }

    @Test
    fun `open balance with no parcela defined is flagged unpayable, not silently ignored`() {
        val revolving = DividaEntity(nome = "Rotativo", valorOriginalCentavos = 100_000, valorAbertoCentavos = 100_000, taxaJurosMensalBasisPoints = 1390, parcelasRestantes = 0, valorParcelaCentavos = 0)
        val installment = DividaEntity(nome = "Parcelado", valorOriginalCentavos = 300_000, valorAbertoCentavos = 300_000, taxaJurosMensalBasisPoints = 0, parcelasRestantes = 3, valorParcelaCentavos = 100_000)
        val summary = DebtCalculator.summarize(listOf(revolving, installment), today)
        assertTrue(summary.hasUnpayableDebt)
        assertNull(summary.debtFreeDate)
    }

    @Test
    fun `zero interest debt pays off in balance div payment months`() {
        val debt = DividaEntity(nome = "Parcelado", valorOriginalCentavos = 300_000, valorAbertoCentavos = 300_000, taxaJurosMensalBasisPoints = 0, parcelasRestantes = 3, valorParcelaCentavos = 100_000)
        val summary = DebtCalculator.summarize(listOf(debt), today)
        assertEquals(today.plusMonths(3), summary.debtFreeDate)
    }

    @Test
    fun `progress reflects paid-down fraction`() {
        val debt = DividaEntity(nome = "D", valorOriginalCentavos = 100_000, valorAbertoCentavos = 25_000, taxaJurosMensalBasisPoints = 0, parcelasRestantes = 0, valorParcelaCentavos = 0)
        val plan = DebtCalculator.summarize(listOf(debt), today).ordered.single()
        assertEquals(0.75f, plan.progress)
    }

    @Test
    fun `installment contract ends on the last parcela, not on re-amortized interest`() {
        val today = LocalDate.of(2026, 9, 26)
        // Picpay do relato: saldo 2.401 a 6% a.m., 4 x 481 restantes a partir de set/26.
        // Pela fórmula de amortização daria 7 meses (abr/27); o contrato termina em dez/26.
        val picpay = DividaEntity(
            nome = "Picpay", valorOriginalCentavos = 400_000, valorAbertoCentavos = 240_100,
            taxaJurosMensalBasisPoints = 600, parcelasRestantes = 4, valorParcelaCentavos = 48_100,
            parcelasTotais = 8, proximoVencimento = LocalDate.of(2026, 9, 10).toEpochMillis(),
        )
        val summary = DebtCalculator.summarize(listOf(picpay), today)
        assertEquals(LocalDate.of(2026, 12, 10), summary.debtFreeDate)
    }
}
