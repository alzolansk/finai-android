package com.finai.app.domain.quickentry

import com.finai.app.domain.PaymentMethod
import com.finai.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Fase 5 (planning-v2): frases reais em pt-BR viram rascunho; o que a frase não disse fica em branco. */
class QuickEntryParserTest {
    /** Domingo, 08/11/2026 (o dia do cenário de demonstração). */
    private val today = LocalDate.of(2026, 11, 8)
    private val ctx = QuickEntryContext(
        accounts = listOf("Nubank", "Itaú", "Carteira"),
        cards = listOf("Nubank", "Inter"),
        goals = listOf("Reserva de emergência", "Chile", "Notebook"),
    )
    private fun p(text: String, c: QuickEntryContext = ctx) = QuickEntryParser.parse(text, today, c)

    @Test fun uberYesterday() {
        val d = p("uber 23 ontem")
        assertEquals(TransactionType.Gasto, d.type)
        assertEquals(2_300L, d.cents)
        assertEquals(today.minusDays(1), d.date)
        assertTrue(d.dateExplicit)
        assertEquals("Uber", d.description)
    }

    @Test fun commaDecimalAndKnownAccount() {
        val d = p("gastei 45,90 no ifood no nubank")
        assertEquals(4_590L, d.cents)
        assertEquals("Ifood", d.description)
        assertEquals(TransactionType.Gasto, d.type)
    }

    @Test fun dotDecimal() = assertEquals(1_250L, p("padaria 12.50").cents)

    @Test fun thousandsSeparator() = assertEquals(123_456L, p("paguei 1.234,56 de aluguel").cents)

    @Test fun thousandsWithoutCents() = assertEquals(320_000L, p("aluguel 3.200").cents)

    @Test fun milSuffix() = assertEquals(150_000L, p("recebi 1,5 mil de bônus").cents)

    @Test fun currencySymbol() = assertEquals(8_990L, p("netflix R$ 89,90").cents)

    @Test fun incomeFromFreela() {
        val d = p("recebi 300 do freela")
        assertEquals(TransactionType.Receita, d.type)
        assertEquals(30_000L, d.cents)
        assertEquals("Freela", d.description)
        assertNull(d.category)
        assertNull(d.payment)
    }

    @Test fun salaryIsIncome() = assertEquals(TransactionType.Receita, p("caiu o salário 4200").type)

    @Test fun saveToReserve() {
        val d = p("guardei 200 na reserva")
        assertEquals(TransactionType.Transferencia, d.type)
        assertEquals(20_000L, d.cents)
        assertEquals("Reserva de emergência", d.goalName)
        assertTrue(d.toGoal)
    }

    @Test fun saveToNamedGoal() {
        val d = p("guardei 150 pro chile")
        assertEquals("Chile", d.goalName)
        assertTrue(d.complete)
    }

    @Test fun saveWithoutKnownGoalLeavesDestinationBlank() {
        val d = p("guardei 100 pra viagem")
        assertNull(d.goalName)
        assertEquals(listOf("destino"), d.missing())
    }

    @Test fun installmentsOnCard() {
        val d = p("comprei tênis 600 em 3x no inter")
        assertEquals(3, d.installments)
        assertEquals("Inter", d.card)
        assertEquals(PaymentMethod.Parcelado, d.payment)
        assertEquals(60_000L, d.cents)
    }

    @Test fun installmentsInWords() = assertEquals(10, p("geladeira 3000 em 10 vezes no cartão").installments)

    @Test fun pixIsDebit() {
        val d = p("pix 50 pro joão")
        assertEquals(PaymentMethod.Debito, d.payment)
        assertEquals(5_000L, d.cents)
    }

    @Test fun creditWithoutKnownCardStaysWithoutCard() {
        val d = p("farmácia 80 no crédito")
        assertEquals(PaymentMethod.Credito, d.payment)
        assertNull(d.card)
        assertTrue("cartão" in d.missing())
    }

    @Test fun nameThatIsBothAccountAndCardWithCreditGoesToCard() {
        val d = p("mercado 200 no crédito nubank")
        assertEquals("Nubank", d.card)
        assertNull(d.account)
    }

    @Test fun nameThatIsBothAccountAndCardWithPixGoesToAccount() {
        val d = p("mercado 200 pix nubank")
        assertEquals("Nubank", d.account)
        assertNull(d.card)
        assertEquals(PaymentMethod.Debito, d.payment)
    }

    @Test fun unknownAccountStaysBlank() {
        val d = p("lanche 30 no bradesco")
        assertNull(d.account)
        assertNull(d.card)
    }

    @Test fun dayOfMonthInThePast() = assertEquals(LocalDate.of(2026, 11, 5), p("luz 140 dia 5").date)

    @Test fun dayOfMonthAfterTodayIsLastMonth() = assertEquals(LocalDate.of(2026, 10, 20), p("água 90 dia 20").date)

    @Test fun dayNumberIsNotTheAmount() = assertEquals(9_000L, p("água 90 dia 20").cents)

    @Test fun slashDate() {
        val d = p("internet 120 03/11")
        assertEquals(LocalDate.of(2026, 11, 3), d.date)
        assertEquals(12_000L, d.cents)
    }

    @Test fun weekdayIsTheLastOne() = assertEquals(LocalDate.of(2026, 11, 6), p("cinema 60 sexta").date)

    @Test fun weekdayWithFeira() = assertEquals(LocalDate.of(2026, 11, 2), p("almoço 35 na segunda-feira").date)

    @Test fun dayBeforeYesterday() = assertEquals(today.minusDays(2), p("gasolina 200 anteontem").date)

    @Test fun noDateMeansTodayNotExplicit() {
        val d = p("café 8")
        assertEquals(today, d.date)
        assertFalse(d.dateExplicit)
    }

    @Test fun noAmountStaysBlank() {
        val d = p("uber ontem")
        assertNull(d.cents)
        assertTrue("valor" in d.missing())
        assertFalse(d.complete)
    }

    @Test fun learnedRuleWinsOverFixedTable() {
        val learned = ctx.copy(learned = mapOf("ifood" to "Lazer"))
        assertEquals("Lazer", p("ifood 40", learned).category)
    }

    @Test fun knownMerchantGetsCategory() = assertEquals("Transporte", p("uber 23").category)

    @Test fun commandPrefixIsIgnored() {
        val d = p("lança 25 de pão")
        assertEquals(2_500L, d.cents)
        assertEquals("Pão", d.description)
    }

    @Test fun accentsDoNotChangeTheReadingButSpellingIsKept() {
        assertEquals(p("farmacia 80").category, p("farmácia 80").category)
        assertEquals("Farmácia", p("farmácia 80").description)
        assertEquals(p("ALUGUEL 3200").cents, p("aluguel 3200").cents)
    }

    @Test fun completeSpendNeedsCategory() {
        assertTrue(p("uber 23").complete)
        assertFalse(p("xpto 23").complete)
    }

    @Test fun looksLikeEntryOnlyForStatementsWithAmount() {
        assertTrue(QuickEntryParser.looksLikeEntry("gastei 45 no ifood"))
        assertTrue(QuickEntryParser.looksLikeEntry("recebi 300 do freela"))
        assertTrue(QuickEntryParser.looksLikeEntry("anota 30 de pão"))
        assertFalse(QuickEntryParser.looksLikeEntry("quanto gastei com ifood?"))
        assertFalse(QuickEntryParser.looksLikeEntry("posso comprar um tênis de 300?"))
        assertFalse(QuickEntryParser.looksLikeEntry("gastei muito esse mês"))
        assertFalse(QuickEntryParser.looksLikeEntry("oi"))
    }

    @Test fun chatReplySaysWhatIsMissing() {
        val reply = QuickEntryParser.chatReply(p("guardei 100 pra viagem"), today)
        assertTrue(reply, reply.startsWith("Lançamento pronto"))
        assertTrue(reply, reply.contains("Falta destino"))
        assertTrue(QuickEntryParser.chatReply(p("uber 23 ontem"), today).contains("ontem"))
    }
}
