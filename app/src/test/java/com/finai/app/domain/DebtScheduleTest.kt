package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DebtScheduleTest {
    private val today = LocalDate.of(2026, 9, 26)

    private fun loan(restantes: Int = 3, total: Int = 12, due: LocalDate? = LocalDate.of(2026, 10, 10), aberto: Long = 150_000) = DividaEntity(
        id = 1, nome = "Empréstimo", valorOriginalCentavos = 600_000, valorAbertoCentavos = aberto,
        taxaJurosMensalBasisPoints = 200, parcelasRestantes = restantes, valorParcelaCentavos = 50_000,
        parcelasTotais = total, proximoVencimento = due?.toEpochMillis(),
    )

    @Test fun projectsEveryRemainingInstallmentUntilTheLast() {
        val d = loan()
        assertNull(DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 9), today))
        val oct = DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 10), today)!!
        assertEquals(10, oct.numero); assertEquals(12, oct.total); assertTrue(oct.isNext)
        assertEquals(LocalDate.of(2026, 10, 10), oct.vencimento)
        val dec = DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 12), today)!!
        assertEquals(12, dec.numero); assertFalse(dec.isNext)
        assertNull(DebtSchedule.installmentInMonth(d, YearMonth.of(2027, 1), today))
        assertEquals(YearMonth.of(2026, 12), DebtSchedule.lastInstallmentMonth(d, today))
    }

    @Test fun day31FallsOnLastDayOfShortMonth() {
        val d = loan(restantes = 6, due = LocalDate.of(2026, 12, 31))
        assertEquals(LocalDate.of(2027, 2, 28), DebtSchedule.installmentInMonth(d, YearMonth.of(2027, 2), today)!!.vencimento)
    }

    /** Picpay/Tablet "vence dia 30" cadastrados em setembro: é todo último dia do mês. */
    @Test fun dueOnLastDayOfMonthFollowsMonthEnd() {
        val d = loan(restantes = 6, due = LocalDate.of(2026, 9, 30))
        fun em(y: Int, m: Int) = DebtSchedule.installmentInMonth(d, YearMonth.of(y, m), today)!!.vencimento
        assertEquals(LocalDate.of(2026, 10, 31), em(2026, 10))
        assertEquals(LocalDate.of(2026, 11, 30), em(2026, 11))
        assertEquals(LocalDate.of(2027, 2, 28), em(2027, 2))
        // Pagar a parcela mantém o fim de mês.
        val paga = DebtSchedule.afterPayment(d, today)
        assertEquals(LocalDate.of(2026, 10, 31), paga.proximoVencimento!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 30), DebtSchedule.afterPayment(paga, today).proximoVencimento!!.toLocalDate())
        // "Livre em" também cai no fim do mês da última parcela.
        assertEquals(LocalDate.of(2027, 2, 28), DebtCalculator.summarize(listOf(d.copy(taxaJurosMensalBasisPoints = 0)), today).debtFreeDate)
    }

    @Test fun overdueInstallmentStaysInItsMonthAndIsFlagged() {
        val d = loan(due = LocalDate.of(2026, 8, 10))
        val aug = DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 8), today)!!
        assertTrue(aug.atrasada); assertTrue(aug.isNext)
        assertTrue(DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 9), today)!!.atrasada)
        assertFalse(DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 10), today)!!.atrasada)
    }

    @Test fun lastInstallmentDoesNotExceedOpenBalance() {
        val d = loan(aberto = 120_000)
        assertEquals(20_000L, DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 12), today)!!.valorCentavos)
    }

    @Test fun legacyDebtWithoutDueDateAnchorsOnCurrentMonth() {
        val d = loan(total = 0, due = null)
        val sep = DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 9), today)!!
        assertNull(sep.vencimento); assertNull(sep.numero)
        assertTrue(DebtSchedule.installmentInMonth(d, YearMonth.of(2026, 11), today) != null)
    }

    @Test fun payingAdvancesTheWholeSchedule() {
        val paid = DebtSchedule.afterPayment(loan(), today)
        assertEquals(2, paid.parcelasRestantes)
        assertEquals(100_000L, paid.valorAbertoCentavos)
        assertEquals(LocalDate.of(2026, 11, 10), paid.proximoVencimento!!.toLocalDate())
        assertNull(DebtSchedule.installmentInMonth(paid, YearMonth.of(2026, 10), today))
        assertEquals(11, DebtSchedule.installmentInMonth(paid, YearMonth.of(2026, 11), today)!!.numero)
        assertNull(DebtSchedule.afterPayment(loan(restantes = 1), today).proximoVencimento)
    }

    @Test fun entryBuildsInstallmentDebtFromContractTerms() {
        val due = LocalDate.of(2026, 10, 10)
        assertTrue(DebtEntry.canSave("Empréstimo", true, 55_000, 24, 5, due, "2,5"))
        assertFalse(DebtEntry.canSave("Empréstimo", true, 55_000, 24, 24, due, ""))
        assertFalse(DebtEntry.canSave("Empréstimo", true, 55_000, 24, 5, null, ""))
        assertFalse(DebtEntry.canSave("Empréstimo", true, 55_000, 24, 5, due, "abc"))
        val d = DebtEntry.build(null, " Empréstimo ", true, 55_000, 24, 5, due, "2,5", null)
        assertEquals("Empréstimo", d.nome)
        assertEquals(19, d.parcelasRestantes); assertEquals(24, d.parcelasTotais)
        assertEquals(55_000L * 19, d.valorAbertoCentavos); assertEquals(55_000L * 24, d.valorOriginalCentavos)
        assertEquals(250, d.taxaJurosMensalBasisPoints)
        assertEquals(due, d.proximoVencimento!!.toLocalDate())
        assertEquals(900_000L, DebtEntry.build(null, "E", true, 55_000, 24, 5, due, "", 900_000).valorAbertoCentavos)
    }

    @Test fun entryBuildsRevolvingDebtWithoutInstallments() {
        val d = DebtEntry.build(null, "Cheque especial", false, 300_000, 0, 0, null, "8", null)
        assertEquals(0, d.parcelasRestantes); assertEquals(0L, d.valorParcelaCentavos); assertNull(d.proximoVencimento)
        assertEquals(300_000L, d.valorAbertoCentavos); assertEquals(800, d.taxaJurosMensalBasisPoints)
        assertTrue(DebtSchedule.installmentsInMonth(listOf(d), YearMonth.of(2026, 9), today).isEmpty())
    }
}
