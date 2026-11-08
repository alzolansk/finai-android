package com.finai.app.domain.capture

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.EntryOptions
import com.finai.app.domain.PaymentMethod
import com.finai.app.domain.TransactionType
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CaptureDedupeTest {
    private val day = LocalDate.of(2026, 10, 18)
    private val credit = NotificationParser.parseText("Compra no crédito de R$ 42,90 aprovada no Mercado.", day) as ParseOutcome.Movement
    private val now = 1_000_000_000L

    private fun known(id: Long, key: String, sig: String, state: SuggestionState = SuggestionState.Pending, at: Long = now - 1000, amount: Long? = 4290) =
        CaptureDedupe.Known(id, "com.banco", key, sig, state, at, MovementKind.CreditPurchase, amount, "Mercado")

    @Test fun repostWithSameKeyAndContentIsARepeat() {
        val d = CaptureDedupe.decide("com.banco", "k1", credit.signature, credit.movement, now, listOf(known(7, "k1", credit.signature)))
        assertEquals(CaptureDedupe.Decision.Repeat(7), d)
    }

    @Test fun updateOfTheSameNotificationUpdatesThePendingSuggestion() {
        val updated = NotificationParser.parseText("Compra no crédito de R$ 42,90 aprovada no Mercado. ${CaptureSamples.UPDATE_SUFFIX}", day) as ParseOutcome.Movement
        assertEquals(4290L, updated.movement.amountCents)
        val d = CaptureDedupe.decide("com.banco", "k1", updated.signature, updated.movement, now, listOf(known(7, "k1", credit.signature)))
        assertEquals(CaptureDedupe.Decision.UpdatePending(7), d)
    }

    @Test fun updateNeverReopensAnIgnoredOrConfirmedSuggestion() {
        val updated = NotificationParser.parseText("Compra no crédito de R$ 42,90 aprovada no Mercado. ${CaptureSamples.UPDATE_SUFFIX}", day) as ParseOutcome.Movement
        listOf(SuggestionState.Ignored, SuggestionState.Confirmed).forEach { state ->
            val d = CaptureDedupe.decide("com.banco", "k1", updated.signature, updated.movement, now, listOf(known(7, "k1", credit.signature, state)))
            assertEquals(CaptureDedupe.Decision.KeepResolved(7), d)
        }
    }

    @Test fun appThatReusesTheIdForAnotherPurchaseCreatesANewSuggestion() {
        val other = NotificationParser.parseText("Compra no crédito de R$ 15,00 aprovada na Padaria.", day) as ParseOutcome.Movement
        val d = CaptureDedupe.decide("com.banco", "k1", other.signature, other.movement, now, listOf(known(7, "k1", credit.signature)))
        assertEquals(CaptureDedupe.Decision.Create(possibleRepeat = false), d)
    }

    @Test fun twoLegitimatePurchasesOfTheSameValueBothBecomeSuggestions() {
        // Mesma mensagem, outra notificação (outra chave), logo depois: sugestão nova, avisando.
        val d = CaptureDedupe.decide("com.banco", "k2", credit.signature, credit.movement, now, listOf(known(7, "k1", credit.signature)))
        assertEquals(CaptureDedupe.Decision.Create(possibleRepeat = true), d)
        // Horas depois, é só outra compra igual.
        val later = CaptureDedupe.decide("com.banco", "k3", credit.signature, credit.movement, now + 3 * 3600_000L, listOf(known(7, "k1", credit.signature)))
        assertEquals(CaptureDedupe.Decision.Create(possibleRepeat = false), later)
    }

    @Test fun otherAppWithSameTextIsNotARepeat() {
        val d = CaptureDedupe.decide("com.outro", "x1", credit.signature, credit.movement, now, listOf(known(7, "k1", credit.signature)))
        assertEquals(CaptureDedupe.Decision.Create(possibleRepeat = false), d)
    }

    // ── regras de confirmação ────────────────────────────────────────

    private fun draft(m: ParsedMovement) = SuggestionRules.draftOf(m)

    @Test fun creditNeedsCardAndConfirmedDue_neverSavedAsDebit() {
        val d = draft(credit.movement)
        assertEquals(PaymentMethod.Credito, d.payment)
        assertEquals(listOf(MissingField.Account), SuggestionRules.missing(d, null))
        val withCard = d.copy(card = "Nubank")
        // Sem fatura conhecida não há vencimento: precisa informar.
        assertEquals(listOf(MissingField.DueDate), SuggestionRules.missing(withCard, EntryOptions.CardDue(null, false)))
        // Vencimento só sugerido: precisa confirmar.
        val suggested = EntryOptions.CardDue(LocalDate.of(2026, 11, 10), false)
        assertEquals(listOf(MissingField.DueDate), SuggestionRules.missing(withCard, suggested))
        assertTrue(SuggestionRules.missing(withCard.copy(dueConfirmed = true), suggested).isEmpty())
        val payment = SuggestionRules.cardPaymentOf(withCard.copy(dueConfirmed = true), suggested)
        assertEquals("Nubank", payment.card)
        assertEquals(1, payment.installments)
        assertEquals(TransactionType.Gasto.name, SuggestionRules.entryOf(withCard).tipo)
        assertEquals(NOTIFICATION_ORIGIN, SuggestionRules.entryOf(withCard).origem)
    }

    @Test fun confidentInvoiceDueNeedsNoConfirmation() {
        val faturas = listOf(FaturaCartaoEntity(1, 10, "Nubank", LocalDate.of(2026, 11, 3).toEpochMillis(), LocalDate.of(2026, 11, 10).toEpochMillis()))
        val contas = listOf(ContaEntity(10, "Fatura Nubank", 0, LocalDate.of(2026, 11, 10).toEpochMillis(), "pendente", "a_pagar", false))
        val due = EntryOptions.resolveCardDue("Nubank", day, faturas, contas)
        assertTrue(due.confident)
        assertTrue(SuggestionRules.missing(draft(credit.movement).copy(card = "Nubank"), due).isEmpty())
    }

    @Test fun installmentsUseTheExplicitCount() {
        val m = (NotificationParser.parseText("Compra no crédito aprovada na Loja Eletro: total de R$ 300,00 em 3 parcelas de R$ 100,00.", day) as ParseOutcome.Movement).movement
        val d = draft(m).copy(card = "Nubank", dueConfirmed = true, category = "Compras")
        val due = EntryOptions.CardDue(LocalDate.of(2026, 11, 10), false)
        assertTrue(SuggestionRules.missing(d, due).isEmpty())
        assertEquals(3, SuggestionRules.cardPaymentOf(d, due).installments)
        assertEquals(30000L, SuggestionRules.entryOf(d).valorCentavos)
    }

    @Test fun purchaseWithoutMethodAsksForIt() {
        val m = (NotificationParser.parseText("Compra de R$ 42,90 APROVADA em MERCADO", day) as ParseOutcome.Movement).movement
        assertTrue(MissingField.PaymentMethod in SuggestionRules.missing(draft(m), null))
    }

    @Test fun ambiguousAndReviewOnlyCannotBeSaved() {
        val amb = (NotificationParser.parseText("Compras aprovadas: R$ 25,00 na Padaria e R$ 40,00 na Farmácia.", day) as ParseOutcome.Movement).movement
        assertTrue(MissingField.Amount in SuggestionRules.missing(draft(amb), null))
        val refund = (NotificationParser.parseText("Estorno de R$ 42,90 da compra no Mercado", day) as ParseOutcome.Movement).movement
        assertEquals(listOf(MissingField.Review), SuggestionRules.missing(draft(refund), null))
    }

    @Test fun pixReceivedIsAvulsaIncomeFromThePayer() {
        val m = (NotificationParser.parseText("Você recebeu um Pix de R$ 850,00 de Carlos Lima.", day) as ParseOutcome.Movement).movement
        val d = draft(m)
        assertTrue(SuggestionRules.missing(d, null).isEmpty())
        val e = SuggestionRules.entryOf(d)
        assertEquals(TransactionType.Receita.name, e.tipo)
        assertEquals("Carlos Lima", e.contaOrigem)
        assertEquals("Avulsa", e.categoria)
        assertFalse(e.recorrente)
    }

    @Test fun matcherFindsSameAmountNearDate_butLetsTheUserDecide() {
        val saved = TransacaoEntity(id = 1, data = day.minusDays(1).toEpochMillis(), descricao = "Mercado", valorCentavos = 4290,
            categoria = "Alimentação", contaOrigem = "Nubank", recorrente = false, origem = "manual")
        val far = saved.copy(id = 2, data = day.minusDays(9).toEpochMillis())
        val other = saved.copy(id = 3, valorCentavos = 4291)
        val found = SuggestionMatcher.similar(4290, null, day, "Mercado", TransactionType.Gasto, listOf(saved, far, other))
        assertEquals(listOf(1L), found.map { it.id })
    }
}
