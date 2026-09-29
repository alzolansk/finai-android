package com.finai.app.domain.scenario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Extração local da intenção de compra: o que o usuário disse vira campo; o que não disse fica nulo. */
class PurchaseIntentParserTest {
    private val today = LocalDate.of(2026, 10, 5)

    @Test fun chairInFourInstallmentsWithoutDateOrMethodAsksBoth() {
        val i = PurchaseIntentParser.parse("Posso comprar uma cadeira de R$ 1.000 em 4x?", today)!!
        assertEquals("Cadeira", i.description)
        assertEquals(100_000L, i.amountCents)
        assertEquals(4, i.count)
        assertNull(i.method)
        assertNull(i.firstDate)
        assertEquals(listOf(MissingField.Method, MissingField.FirstDate), i.missing())
    }

    @Test fun installmentValueAfterTheCountIsTheInstallment() {
        val i = PurchaseIntentParser.parse("dá pra pegar um celular em 12x de 150 no cartão?", today)!!
        assertEquals(12, i.count)
        assertEquals(15_000L, i.installmentCents)
        assertEquals(180_000L, i.amountCents)
        assertEquals(PaymentMethod.CreditCard, i.method)
        assertEquals(listOf(MissingField.FirstDate), i.missing())
    }

    @Test fun cashPriceAndInstallmentsInTheSameMessage() {
        val i = PurchaseIntentParser.parse("Vale comprar a TV por R$ 1.000 à vista ou 4x de R$ 300 no cartão, primeira dia 10?", today)!!
        assertEquals(4, i.count)
        assertEquals(30_000L, i.installmentCents)
        assertEquals(100_000L, i.cashPriceCents)
        assertEquals(120_000L, i.amountCents)
        assertEquals(LocalDate.of(2026, 10, 10), i.firstDate)
        assertTrue(i.missing().isEmpty())
    }

    @Test fun pixTodayNeedsNothingElse() {
        val i = PurchaseIntentParser.parse("posso gastar mil reais no pix hoje?", today)!!
        assertEquals(100_000L, i.amountCents)
        assertEquals(PaymentMethod.Immediate, i.method)
        assertEquals(today, i.firstDate)
        assertTrue(i.missing().isEmpty())
    }

    @Test fun thousandsAndDates() {
        assertEquals(150_000L, PurchaseIntentParser.parse("comprar uma bike de 1,5 mil", today)!!.amountCents)
        assertEquals(200_000L, PurchaseIntentParser.parse("comprar notebook de 2k em 10x", today)!!.amountCents)
        val d = PurchaseIntentParser.parse("comprar sofá R$ 2.400 em 6x no boleto, primeira em 15/11", today)!!
        assertEquals(LocalDate.of(2026, 11, 15), d.firstDate)
        assertEquals(PaymentMethod.Boleto, d.method)
        // Dia que já passou neste mês é o do mês que vem.
        assertEquals(LocalDate.of(2026, 11, 3), PurchaseIntentParser.parse("comprar tênis R$ 300 em 2x, primeira dia 3", today)!!.firstDate)
    }

    @Test fun waitForPaydayIsUnderstood() {
        val i = PurchaseIntentParser.parse("posso comprar a cadeira de R$ 1.000 depois do salário?", today)!!
        assertTrue(i.waitForPayday)
        assertFalse(MissingField.FirstDate in i.missing())
    }

    @Test fun discountForCash() {
        val i = PurchaseIntentParser.parse("comprar geladeira R$ 3.000 em 10x no cartão dia 10 ou 10% de desconto à vista", today)!!
        assertEquals(270_000L, i.cashPriceCents)
    }

    @Test fun notPurchaseMessagesAreLeftToTheChat() {
        assertNull(PurchaseIntentParser.parse("oi", today))
        assertNull(PurchaseIntentParser.parse("quanto pago de juros na dívida?", today))
        assertNull(PurchaseIntentParser.parse("quero guardar R$ 500 na meta da viagem", today))
        assertNull(PurchaseIntentParser.parse("como está meu mês?", today))
    }

    @Test fun answerFillsTheMissingDate() {
        val pending = PurchaseIntentParser.parse("Posso comprar uma cadeira de R$ 1.000 em 4x no cartão?", today)!!
        assertEquals(listOf(MissingField.FirstDate), pending.missing())
        val answered = PurchaseIntentParser.answer(pending, "dia 10", today)
        assertNotNull(answered)
        assertEquals(LocalDate.of(2026, 10, 10), answered!!.firstDate)
        assertEquals(100_000L, answered.amountCents)
        assertEquals("Cadeira", answered.description)
        // Só o número do dia também serve como resposta.
        assertEquals(LocalDate.of(2026, 10, 12), PurchaseIntentParser.answer(pending, "12", today)!!.firstDate)
        // Mudou de assunto: nada preenchido, a pergunta pendente cai.
        assertNull(PurchaseIntentParser.answer(pending, "e as minhas metas?", today))
    }

    @Test fun appButtonsProduceWhatTheSimulatorNeeds() {
        // Botão "Perguntar" do simulador "Posso comprar?": completo, Pix/débito hoje.
        val buy = PurchaseIntentParser.parse("Posso fazer uma compra de R$ 500 à vista agora?", today)!!
        assertEquals(1, buy.count)
        assertTrue(buy.missing().isEmpty())
        // Sugestão do chat: pede forma de pagamento e data da 1ª parcela.
        val chip = PurchaseIntentParser.parse("Posso comprar algo de R$ 500 em 2x?", today)!!
        assertNull(chip.description)
        assertEquals(listOf(MissingField.Method, MissingField.FirstDate), chip.missing())
    }

    @Test fun variationsOfTheLastSimulation() {
        val last = PurchaseIntentParser.parse("posso comprar uma cadeira de R$ 1.000 em 4x no cartão, primeira dia 10?", today)!!
        val six = PurchaseIntentParser.variation(last, "e em 6x?", today)!!
        assertEquals(6, six.count)
        assertEquals(100_000L, six.amountCents)
        assertEquals(LocalDate.of(2026, 10, 10), six.firstDate)
        val pix = PurchaseIntentParser.variation(last, "e à vista no pix?", today)!!
        assertEquals(1, pix.count)
        assertEquals(PaymentMethod.Immediate, pix.method)
        assertNull(pix.firstDate)
        assertTrue(pix.missing().isEmpty())
        assertEquals(LocalDate.of(2026, 10, 20), PurchaseIntentParser.variation(last, "e se a primeira for dia 20?", today)!!.firstDate)
        assertNull(PurchaseIntentParser.variation(last, "e minhas metas?", today))
        assertNull(PurchaseIntentParser.variation(last, "e o que vence dia 10?", today))
        // Parcela informada antes não sobrevive a um novo número de parcelas.
        val fixed = PurchaseIntentParser.parse("comprar TV em 4x de R$ 300 no cartão dia 10", today)!!
        val ten = PurchaseIntentParser.variation(fixed, "e em 10x?", today)!!
        assertNull(ten.installmentCents)
        assertEquals(120_000L, ten.amountCents)
    }
}
