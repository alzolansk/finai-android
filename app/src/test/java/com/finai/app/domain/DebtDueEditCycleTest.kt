package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Relato de 11/10/2026: a parcela do Picpay vencia dia 30 e foi editada para 31/10, o dia do
 * salário. A Agenda mensal mostrava a parcela em 31/10, mas a sequencial não a mostrava em ciclo
 * nenhum: o ciclo atual termina em 30/10 e a projeção do ciclo seguinte (que começa em 31/10)
 * dava a parcela como paga porque o vencimento guardado ainda era 30/10.
 */
class DebtDueEditCycleTest {
    private val today = LocalDate.of(2026, 10, 11)
    private fun d(day: Int, month: Int = 10) = LocalDate.of(2026, month, day)

    // Série de fim de mês: 30/09 → 31/10 → 30/11.
    private val salario = TransacaoEntity(
        id = 1, data = d(30, 9).toEpochMillis(), descricao = "Salario", valorCentavos = 410_000, categoria = "Salário",
        contaOrigem = "Carteira", recorrente = true, origem = "manual", tipo = TransactionType.Receita.name, rendaPrincipal = true)

    private val picpay = DividaEntity(
        id = 20, nome = "Empréstimo Picpay", valorOriginalCentavos = 48_100 * 12, valorAbertoCentavos = 48_100 * 4,
        taxaJurosMensalBasisPoints = 0, parcelasRestantes = 4, valorParcelaCentavos = 48_100,
        parcelasTotais = 12, proximoVencimento = d(30).toEpochMillis(), diaVencimento = 30)

    private fun picpayIn(segment: TimelineSegment) = segment.movements.filter { it.descricao == "Empréstimo Picpay" }

    private fun assertMovedToNextCycle(divida: DividaEntity) {
        assertEquals(d(31), DebtSchedule.installmentInMonth(divida, YearMonth.of(2026, 10), today)!!.vencimento)
        val tl = CashFlowTimeline.build(emptyList(), listOf(salario), listOf(divida), today)
        val current = tl.current!!
        assertEquals(d(31), current.nextPayday)
        assertFalse(picpayIn(current).any())
        val next = tl.segments.first { it.kind == SegmentKind.Future }
        assertEquals(d(31), next.start)
        assertEquals(d(29, 11), next.nextPayday.minusDays(1))
        assertEquals(listOf(d(31)), picpayIn(next).map { it.date })
    }

    @Test fun installmentEditedToPaydayGoesToTheNextCycle() =
        assertMovedToNextCycle(DebtSchedule.withInstallmentDate(picpay, YearMonth.of(2026, 10), d(31), today))

    @Test fun dueDayChangedFromThisInstallmentOnGoesToTheNextCycle() =
        assertMovedToNextCycle(DebtSchedule.withDueDayFrom(picpay, YearMonth.of(2026, 10), 31, today))

    @Test fun withoutEditTheInstallmentStaysInTheCurrentCycle() {
        val tl = CashFlowTimeline.build(emptyList(), listOf(salario), listOf(picpay), today)
        assertEquals(listOf(d(30)), picpayIn(tl.current!!).map { it.date })
        // A de novembro (30/11) cai no dia do salário seguinte: é do ciclo depois do próximo.
        assertFalse(picpayIn(tl.segments.first { it.kind == SegmentKind.Future }).any())
    }
}
