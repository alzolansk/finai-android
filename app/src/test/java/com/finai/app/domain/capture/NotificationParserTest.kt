package com.finai.app.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NotificationParserTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 10, 18)
    private val posted = today.atTime(14, 30).atZone(zone).toInstant().toEpochMilli()

    private fun notif(title: String?, text: String?, big: String? = null, lines: List<String> = emptyList()) =
        CapturedNotification("com.banco", "Banco", "0|com.banco|1|null|10", posted, title, text, big, null, lines)

    private fun parseOne(title: String?, text: String?, known: List<String> = emptyList()): ParseOutcome =
        NotificationParser.parse(notif(title, text), known, zone).single()

    private fun movement(title: String?, text: String?, known: List<String> = emptyList()): ParsedMovement {
        val out = parseOne(title, text, known)
        assertTrue("esperava movimentação, veio $out", out is ParseOutcome.Movement)
        return (out as ParseOutcome.Movement).movement
    }

    private fun ignored(title: String?, text: String?): IgnoreReason {
        val out = parseOne(title, text)
        assertTrue("esperava descarte, veio $out", out is ParseOutcome.Ignored)
        return (out as ParseOutcome.Ignored).reason
    }

    // ── os 13 exemplos da área de testes ─────────────────────────────

    @Test fun sample1_creditPurchase() {
        val m = movement("Compra aprovada", "Compra no crédito de R$ 42,90 aprovada no Mercado.")
        assertEquals(MovementKind.CreditPurchase, m.kind)
        assertEquals(4290L, m.amountCents)
        assertEquals("Mercado", m.counterpart)
        assertEquals(today, m.date)
        assertFalse("sem data escrita, vale o horário da notificação", m.dateExplicit)
        assertEquals("Alimentação", m.category)
    }

    @Test fun sample2_debitPurchase() {
        val m = movement("Compra no débito", "Compra no débito de R$ 18,50 aprovada na Padaria.")
        assertEquals(MovementKind.DebitPurchase, m.kind)
        assertEquals(1850L, m.amountCents)
        assertEquals("Padaria", m.counterpart)
    }

    @Test fun sample3_pixSent() {
        val m = movement("Pix enviado", "Você enviou um Pix de R$ 120,00 para Ana Souza.")
        assertEquals(MovementKind.PixSent, m.kind)
        assertEquals(12000L, m.amountCents)
        assertEquals("Ana Souza", m.counterpart)
        assertNull("pessoa não tem categoria inventada", m.category)
    }

    @Test fun sample4_pixReceived() {
        val m = movement("Pix recebido", "Você recebeu um Pix de R$ 850,00 de Carlos Lima.")
        assertEquals(MovementKind.PixReceived, m.kind)
        assertEquals(85000L, m.amountCents)
        assertEquals("Carlos Lima", m.counterpart)
    }

    @Test fun sample5_installments_totalAndInstallmentAreExplicit() {
        val m = movement("Compra parcelada", "Compra no crédito aprovada na Loja Eletro: total de R$ 300,00 em 3 parcelas de R$ 100,00.")
        assertEquals(MovementKind.InstallmentPurchase, m.kind)
        assertEquals(30000L, m.amountCents)
        assertEquals(10000L, m.installmentCents)
        assertEquals(3, m.installments)
        assertEquals("Loja Eletro", m.counterpart)
    }

    @Test fun sample9_declinedIsIgnored() =
        assertEquals(IgnoreReason.Declined, ignored("Compra recusada", "Compra de R$ 89,90 recusada na Loja X. Saldo insuficiente."))

    @Test fun sample10_limitAdIsIgnored() = assertEquals(
        IgnoreReason.Promotion,
        ignored("Seu limite aumentou", "Aproveite: agora você tem R$ 3.000,00 de limite disponível para compras parceladas em até 12x."),
    )

    @Test fun sample11_noAmountIsIgnored() =
        assertEquals(IgnoreReason.NoAmount, ignored("Compra aprovada", "Sua compra foi aprovada. Toque para ver os detalhes no app."))

    @Test fun sample12_refundNeedsReview() {
        val m = movement("Estorno realizado", "O estorno de R$ 42,90 da compra no Mercado foi creditado no seu cartão.")
        assertEquals(MovementKind.Refund, m.kind)
        assertTrue(m.kind.reviewOnly)
        assertEquals(4290L, m.amountCents)
        assertNull("estorno não ganha categoria de gasto", m.category)
    }

    @Test fun sample13_severalValuesAreAmbiguous() {
        val m = movement("Compras aprovadas", "Compras aprovadas: R$ 25,00 na Padaria e R$ 40,00 na Farmácia.")
        assertTrue(m.ambiguous)
        assertNull(m.amountCents)
        assertEquals(listOf(2500L, 4000L), m.candidatesCents)
    }

    @Test fun everySampleParsesAsItsExpectationSays() {
        CaptureSamples.all.forEach { s ->
            s.stepsFor(s.title, s.text).forEach { (_, content) ->
                val out = parseOne(content.first, content.second)
                val ignoredExpected = s.number in listOf(9, 10, 11)
                assertEquals("exemplo ${s.number}", ignoredExpected, out is ParseOutcome.Ignored)
            }
        }
    }

    // ── formatos de valor ────────────────────────────────────────────

    @Test fun brazilianThousandsAndNoSpace() {
        assertEquals(123456L, movement(null, "Compra no débito aprovada: R$ 1.234,56 em POSTO SHELL").amountCents)
        assertEquals(123456L, movement(null, "Compra no débito aprovada: R$1234,56 em POSTO SHELL").amountCents)
        assertEquals(100000000L, movement(null, "Pix enviado: R$ 1.000.000,00 para Fulano").amountCents)
    }

    @Test fun limitAndBalanceAreNeverTheAmount() {
        val m = movement("Compra aprovada", "Compra de R$ 42,90 APROVADA em MERCADO SAO JOAO. Limite disponível: R$ 1.257,10")
        assertEquals(4290L, m.amountCents)
        assertFalse(m.ambiguous)
        val pix = movement(null, "Pix enviado: R$ 120,00 para Ana. Seu saldo é R$ 500,00")
        assertEquals(12000L, pix.amountCents)
    }

    @Test fun sameValueTwiceIsNotAmbiguous() {
        val m = movement("R$ 42,90 no Mercado", "Compra no crédito de R$ 42,90 aprovada no Mercado.")
        assertEquals(4290L, m.amountCents)
        assertFalse(m.ambiguous)
    }

    @Test fun otherCurrencyAloneHasNoAmount() =
        assertEquals(IgnoreReason.NoAmount, ignored("Compra aprovada", "Compra internacional de US$ 10,00 aprovada em STEAM"))

    @Test fun installmentWithInconsistentTotalIsAmbiguous() {
        val m = movement(null, "Compra parcelada aprovada: total de R$ 300,00 em 3x de R$ 120,00 na Loja")
        assertTrue(m.ambiguous)
        assertNull(m.amountCents)
    }

    @Test fun installmentWithOnlyPerInstallmentValue() {
        val m = movement(null, "Compra aprovada em 4x de R$ 25,00 no crédito na LOJA ABC")
        assertEquals(MovementKind.InstallmentPurchase, m.kind)
        assertEquals(10000L, m.amountCents)
        assertEquals(2500L, m.installmentCents)
        assertEquals(4, m.installments)
    }

    @Test fun installmentWithoutCountKeepsCountUnknown() {
        val m = movement(null, "Compra parcelada aprovada de R$ 600,00 na LOJA ABC")
        assertEquals(MovementKind.InstallmentPurchase, m.kind)
        assertEquals(60000L, m.amountCents)
        assertNull("não inventa número de parcelas", m.installments)
    }

    // ── tipo, forma, data, cartão ────────────────────────────────────

    @Test fun purchaseWithoutMethodDoesNotGuessCreditOrDebit() {
        val m = movement("Compra aprovada", "Compra de R$ 42,90 APROVADA em MERCADO SAO JOAO para o cartão com final 1234.")
        assertEquals(MovementKind.Purchase, m.kind)
        assertEquals("Mercado Sao Joao", m.counterpart)
        assertEquals("1234", m.cardLastDigits)
        assertNull(m.accountHint)
    }

    @Test fun explicitDateWins() {
        val m = movement(null, "Compra aprovada no seu cartão final 1234 de R$ 42,90 em 12/10 às 14:32, em MERCADO")
        assertEquals(LocalDate.of(2026, 10, 12), m.date)
        assertTrue(m.dateExplicit)
        assertEquals("MERCADO".let { "Mercado" }, m.counterpart)
    }

    @Test fun dateWithoutYearInTheFutureIsLastYear() {
        val m = NotificationParser.parseText("Compra no débito de R$ 10,00 em 30/12 na Padaria", LocalDate.of(2027, 1, 2))
        assertEquals(LocalDate.of(2026, 12, 30), (m as ParseOutcome.Movement).movement.date)
    }

    @Test fun knownAccountMentionedByNameIsAHint_appNameIsNot() {
        val m = movement("Nubank", "Compra no crédito de R$ 42,90 aprovada no Mercado com seu cartão Nubank.", known = listOf("Nubank", "Itaú"))
        assertEquals("Nubank", m.accountHint)
        val n = movement("Compra", "Compra no crédito de R$ 42,90 aprovada no Mercado.", known = listOf("Nubank"))
        assertNull(n.accountHint)
    }

    @Test fun invoicePaymentAndTransferNeedReview() {
        assertEquals(MovementKind.InvoicePayment, movement(null, "Recebemos o pagamento da fatura de R$ 1.200,00.").kind)
        assertEquals(MovementKind.Transfer, movement(null, "Transferência enviada: TED de R$ 500,00 para João").kind)
        assertTrue(MovementKind.Transfer.reviewOnly)
    }

    @Test fun cancellationIsRefundNotIncome() =
        assertEquals(MovementKind.Refund, movement(null, "Compra de R$ 59,90 na LOJA X foi cancelada.").kind)

    @Test fun adsAndInfoAreIgnored() {
        assertEquals(IgnoreReason.Promotion, ignored("Oferta", "Empréstimo pré-aprovado de R$ 10.000,00 para você!"))
        assertEquals(IgnoreReason.LimitInfo, ignored("Limite", "Seu limite disponível é R$ 2.000,00"))
        assertEquals(IgnoreReason.NoMovement, ignored("Olá", "Seu cartão chegou! Ative pelo app."))
        assertEquals(IgnoreReason.NoMovement, ignored(null, null))
    }

    // ── campos ausentes, texto expandido, agrupamento ───────────────

    @Test fun bigTextDoesNotDuplicateTheShortText() {
        val n = notif("Compra aprovada", "Compra no débito de R$ 18,50", big = "Compra no débito de R$ 18,50 aprovada na Padaria.")
        val m = (NotificationParser.parse(n, zone = zone).single() as ParseOutcome.Movement).movement
        assertEquals(1850L, m.amountCents)
        assertEquals("Padaria", m.counterpart)
    }

    @Test fun onlyBigTextPresent() {
        val n = notif(null, null, big = "Pix recebido: R$ 850,00 de Carlos Lima")
        val m = (NotificationParser.parse(n, zone = zone).single() as ParseOutcome.Movement).movement
        assertEquals(MovementKind.PixReceived, m.kind)
    }

    @Test fun groupedLinesBecomeOneMovementEach() {
        val n = notif("2 novas compras", "2 novas compras", lines = listOf(
            "Compra no débito de R$ 18,50 aprovada na Padaria.",
            "Compra no débito de R$ 9,90 aprovada na Cafeteria.",
        ))
        val outcomes = NotificationParser.parse(n, zone = zone)
        assertEquals(2, outcomes.size)
        val ms = outcomes.map { it as ParseOutcome.Movement }
        assertEquals(listOf(0, 1), ms.map { it.lineIndex })
        assertEquals(listOf(1850L, 990L), ms.map { it.movement.amountCents })
        assertNotEquals(ms[0].signature, ms[1].signature)
    }

    @Test fun signatureIgnoresCaseSpacesAndAccents() {
        assertEquals(NotificationParser.signatureOf("Compra no Crédito  de R$ 42,90"), NotificationParser.signatureOf("compra no credito de r$ 42,90"))
        assertNotEquals(NotificationParser.signatureOf("R$ 42,90"), NotificationParser.signatureOf("R$ 42,91"))
    }
}
