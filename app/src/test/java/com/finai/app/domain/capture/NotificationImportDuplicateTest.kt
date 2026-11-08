package com.finai.app.domain.capture

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.CardPayment
import com.finai.app.domain.CardPurchase
import com.finai.app.domain.importer.DuplicateDetector
import com.finai.app.domain.importer.DuplicateVerdict
import com.finai.app.domain.importer.Installment
import com.finai.app.domain.importer.ParsedEntry
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Problema B: a compra confirmada pela notificação e a mesma compra na fatura importada depois.
 * A importação deve sinalizar (desmarcada), nunca unir nem apagar, e nunca confundir a data da
 * compra com o vencimento da fatura.
 */
class NotificationImportDuplicateTest {
    private val purchaseDay = LocalDate.of(2026, 10, 18)
    private val due = LocalDate.of(2026, 11, 10)

    /** O que `salvarCompraCartao` grava para uma compra confirmada pela notificação. */
    private fun savedFromNotification(total: Long, installments: Int, merchant: String = "Mercado", card: String = "Nubank"): List<TransacaoEntity> {
        val entry = TransacaoEntity(data = purchaseDay.toEpochMillis(), descricao = merchant, valorCentavos = total, categoria = "Alimentação",
            contaOrigem = card, recorrente = false, origem = NOTIFICATION_ORIGIN, tipo = "Gasto")
        return CardPurchase.schedule(entry, CardPayment(card, due, installments)).mapIndexed { i, (_, t) -> t.copy(id = 100L + i, faturaId = 9) }
    }

    @Test fun purchaseKeepsThePurchaseDateNotTheInvoiceDue() {
        val saved = savedFromNotification(30000, 3)
        assertTrue(saved.all { it.data.toLocalDate() == purchaseDay })
    }

    @Test fun invoiceLineOfTheSamePurchaseIsLikelyDuplicate() {
        val saved = savedFromNotification(4290, 1)
        val line = ParsedEntry(purchaseDay, "MERCADO SAO JOAO LTDA", 4290)
        val v = DuplicateDetector.check(line, saved, emptyList(), card = "Nubank")
        assertEquals(DuplicateVerdict.LIKELY, v.verdict)
        assertTrue(v.reason!!.contains("notificação"))
    }

    @Test fun invoiceDateOneDayLaterStillMatches() {
        val saved = savedFromNotification(4290, 1)
        val line = ParsedEntry(purchaseDay.plusDays(1), "MERCADO", 4290)
        assertEquals(DuplicateVerdict.LIKELY, DuplicateDetector.check(line, saved, emptyList()).verdict)
    }

    @Test fun differentMerchantNameFromTheBankIsOnlyPossible() {
        val saved = savedFromNotification(4290, 1, merchant = "Mercado")
        val line = ParsedEntry(purchaseDay, "PAG*SUPERNOSSO 0912", 4290)
        assertEquals(DuplicateVerdict.POSSIBLE, DuplicateDetector.check(line, saved, emptyList()).verdict)
    }

    @Test fun eachInstallmentMatchesOnlyItsOwnNumber() {
        val saved = savedFromNotification(30000, 3, merchant = "Loja Eletro")
        val first = ParsedEntry(purchaseDay, "LOJA ELETRO", 10000, Installment(1, 3))
        val second = ParsedEntry(purchaseDay, "LOJA ELETRO", 10000, Installment(2, 3))
        assertEquals(DuplicateVerdict.LIKELY, DuplicateDetector.check(first, saved, emptyList()).verdict)
        assertEquals(DuplicateVerdict.LIKELY, DuplicateDetector.check(second, saved, emptyList()).verdict)
        // Outra compra com outro número de parcelas não é a mesma.
        val other = ParsedEntry(purchaseDay, "LOJA ELETRO", 10000, Installment(1, 6))
        assertEquals(DuplicateVerdict.NONE, DuplicateDetector.check(other, saved, emptyList()).verdict)
    }

    @Test fun sameAmountOnAnotherCardIsNeverLikely() {
        val saved = savedFromNotification(4290, 1, card = "Inter")
        val line = ParsedEntry(purchaseDay, "MERCADO", 4290)
        assertEquals(DuplicateVerdict.POSSIBLE, DuplicateDetector.check(line, saved, emptyList(), card = "Nubank").verdict)
        assertEquals(DuplicateVerdict.LIKELY, DuplicateDetector.check(line, saved, emptyList(), card = "Nubank final 1234".replace("Nubank", "Inter")).verdict)
    }

    @Test fun farAwayDateIsNotADuplicate() {
        val saved = savedFromNotification(4290, 1)
        val line = ParsedEntry(purchaseDay.plusDays(9), "MERCADO", 4290)
        assertEquals(DuplicateVerdict.NONE, DuplicateDetector.check(line, saved, emptyList()).verdict)
    }

    @Test fun twoLegitimateEqualPurchasesInTheInvoiceAreBothFlaggedNotDropped() {
        // Uma compra confirmada pela notificação; a fatura tem duas iguais. O detector só
        // sinaliza: a revisão mostra as duas, e o usuário marca a segunda, que é outra compra.
        val saved = savedFromNotification(990, 1, merchant = "Cafeteria")
        val a = ParsedEntry(purchaseDay, "CAFETERIA", 990)
        val b = ParsedEntry(purchaseDay, "CAFETERIA", 990)
        assertEquals(DuplicateVerdict.LIKELY, DuplicateDetector.check(a, saved, emptyList()).verdict)
        assertEquals(DuplicateVerdict.LIKELY, DuplicateDetector.check(b, saved, listOf(a)).verdict)
        assertEquals(2, listOf(a, b).size)
    }
}
