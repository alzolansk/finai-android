package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CardPurchaseTest {
    private val entry = TransacaoEntity(data = LocalDate.of(2026, 1, 15).toEpochMillis(), descricao = "Ingresso",
        valorCentavos = 10000, categoria = "Lazer", contaOrigem = "", recorrente = false, origem = "manual")

    @Test fun dividesEveryCentAndRetainsPurchaseDateAcrossFebruary() {
        val schedule = CardPurchase.schedule(entry, CardPayment("Nubank", LocalDate.of(2026, 1, 31), 3))
        assertEquals(listOf(3334L, 3333L, 3333L), schedule.map { it.second.valorCentavos })
        assertEquals(listOf(31, 28, 31), schedule.map { it.first.dayOfMonth })
        assertEquals(10000L, schedule.sumOf { it.second.valorCentavos })
        assertTrue(schedule.all { it.second.data == entry.data && !it.second.recorrente })
        assertEquals(1, schedule.map { it.second.compraId }.distinct().size)
        assertEquals(listOf(1, 2, 3), schedule.map { it.second.parcelaNumero })
    }

    @Test(expected = IllegalArgumentException::class) fun requiresCard() {
        CardPurchase.schedule(entry, CardPayment(" ", LocalDate.of(2026, 2, 10)))
    }
    @Test fun importingAnAlreadyPlannedInstallmentFlagsItForReview() {
        val scheduled = CardPurchase.schedule(entry, CardPayment("Nubank", LocalDate.of(2026, 1, 31), 3)).map { it.second }
        val parsed = com.finai.app.domain.importer.ParsedEntry(LocalDate.of(2026, 1, 15), "Ingresso", 3333,
            com.finai.app.domain.importer.Installment(2, 3))
        assertEquals(com.finai.app.domain.importer.DuplicateVerdict.LIKELY,
            com.finai.app.domain.importer.DuplicateDetector.check(parsed, scheduled, emptyList()).verdict)
    }
    @Test(expected = IllegalArgumentException::class) fun refusesDueBeforePurchase() {
        CardPurchase.schedule(entry, CardPayment("Nubank", LocalDate.of(2026, 1, 1)))
    }
    @Test(expected = IllegalArgumentException::class) fun refusesZeroCentInstallments() {
        CardPurchase.schedule(entry.copy(valorCentavos = 2), CardPayment("Nubank", LocalDate.of(2026, 2, 10), 3))
    }

    @Test fun everyInstallmentCountPreservesTotalAndPlacesRemainderFirst() {
        for (count in 2..24) {
            for (total in listOf(count.toLong(), 100L, 10001L, 289900L, 999999999L)) {
                val schedule = CardPurchase.schedule(entry.copy(valorCentavos = total),
                    CardPayment("Nubank", LocalDate.of(2026, 1, 31), count))
                val amounts = schedule.map { it.second.valorCentavos }
                assertEquals("$total cents / $count installments", total, amounts.sum())
                assertEquals(count, amounts.size)
                assertTrue(amounts.all { it > 0 })
                assertTrue(amounts.zipWithNext().all { (first, second) -> first >= second && first - second <= 1 })
                assertTrue(amounts.max() - amounts.min() <= 1)
                assertEquals((total % count).toInt(), amounts.count { it > total / count })
            }
        }
    }
}
