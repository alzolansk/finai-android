package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.repository.FinanceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PaymentHistoryTest {
    private fun millis(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun conta(id: Long, nome: String, cents: Long, due: LocalDate, paid: LocalDate?, tipo: String = "a_pagar") = ContaEntity(
        id = id, nome = nome, valorCentavos = cents, vencimento = millis(due), status = "pago", tipo = tipo, recorrente = false,
        pagoEm = paid?.let(::millis),
    )

    private fun parcela(id: Long, nome: String, n: Int, day: LocalDate) = TransacaoEntity(
        id = id, data = millis(day), descricao = "$nome · parcela $n de 12", valorCentavos = 41_000,
        categoria = DebtSchedule.PAYMENT_CATEGORY, contaOrigem = "", recorrente = false, origem = DebtSchedule.PAYMENT_ORIGIN,
    )

    @Test fun listsInvoicesBillsAndInstallmentsByPaymentDay() {
        val contas = listOf(
            conta(1, "Fatura Nubank", 80_000, LocalDate.of(2026, 12, 5), LocalDate.of(2026, 11, 14)),
            conta(2, "Luz", 20_000, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 6)),
            conta(3, "Antiga", 10_000, LocalDate.of(2026, 10, 10), null),
            ContaEntity(4, "Aberta", 5_000, millis(LocalDate.of(2026, 11, 20)), "pendente", "a_pagar", false),
        )
        val tx = listOf(parcela(10, "Picpay", 3, LocalDate.of(2026, 11, 10)), parcela(11, "Picpay", 2, LocalDate.of(2026, 10, 10)))
        val records = PaymentHistory.build(contas, tx, invoiceContaIds = setOf(1))

        assertEquals(listOf("Fatura Nubank", "Picpay", "Luz", "Picpay", "Antiga"), records.map { it.title })
        assertEquals(PaymentRecord.Kind.Invoice, records[0].kind)
        assertEquals("paga 14/11 · vence 05/12 (21 dias antes)", records[0].timing())
        assertEquals("paga 06/11 · vence 03/11 (3 dias depois)", records[2].timing())
        assertEquals("parcela 3 de 12", records[1].detail)
        assertNull(records[4].paidOn)

        val months = PaymentHistory.byMonth(records)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), null), months.map { it.month })
        // Só o último pagamento de cada dívida pode ser desfeito.
        assertEquals(setOf(10L), PaymentHistory.undoableInstallments(records))
    }

    @Test fun paymentDayIsRecordedOnceAndClearedOnReopen() {
        val open = ContaEntity(1, "Fatura", 80_000, millis(LocalDate.of(2026, 12, 5)), "pendente", "a_pagar", false)
        val paid = FinanceRepository.withPaymentDate(open.copy(status = "pago"), open, 1_000L)
        assertEquals(1_000L, paid.pagoEm)
        // Editar a conta já paga (nome, valor) mantém o dia.
        val edited = FinanceRepository.withPaymentDate(paid.copy(nome = "Fatura Nubank", pagoEm = null), paid, 9_000L)
        assertEquals(1_000L, edited.pagoEm)
        val reopened = FinanceRepository.withPaymentDate(paid.copy(status = "pendente"), paid, 9_000L)
        assertNull(reopened.pagoEm)
    }

    @Test fun legacyPaidBillWithoutDayCountsTodayWhenDueIsAhead() {
        val today = LocalDate.of(2026, 11, 14)
        assertEquals(today, conta(1, "F", 1, LocalDate.of(2026, 12, 5), null).paidOn(today))
        assertEquals(LocalDate.of(2026, 11, 3), conta(1, "F", 1, LocalDate.of(2026, 11, 3), null).paidOn(today))
    }
}
