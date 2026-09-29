package com.finai.app.domain.scenario

import java.time.LocalDate

/**
 * Decide o que o chat faz com uma mensagem, antes de qualquer IA: é pergunta de compra? Falta
 * dado? Dá para simular? Kotlin puro, para ser testável sem Android.
 */
object PurchaseAssistant {

    sealed interface Step {
        /** Não é sobre compra (ou o usuário mudou de assunto): segue o chat normal. */
        data object NotPurchase : Step

        /** Falta um dado que muda a conta: o app pergunta e guarda a intenção até a resposta. */
        data class Ask(val intent: PurchaseIntent, val question: String) : Step

        /** Simulado. [result] é a única fonte de números da resposta. */
        data class Simulated(val intent: PurchaseIntent, val result: ScenarioResult) : Step
    }

    /**
     * [pending] é a intenção guardada da pergunta anterior do app ("em que dia vence a 1ª?").
     * Uma pergunta de compra nova, com valor, substitui a pendente. [last] é a última compra
     * simulada na conversa: "e em 6x?" refaz a conta com a mudança.
     */
    fun handle(text: String, pending: PurchaseIntent?, snapshot: FinanceSnapshot, today: LocalDate, last: PurchaseIntent? = null): Step {
        val fresh = PurchaseIntentParser.parse(text, today)
        val intent = when {
            fresh != null && fresh.amountCents != null -> fresh
            pending != null -> PurchaseIntentParser.answer(pending, text, today) ?: return Step.NotPurchase
            last != null -> PurchaseIntentParser.variation(last, text, today) ?: return Step.NotPurchase
            fresh != null -> fresh
            else -> return Step.NotPurchase
        }
        if (intent.missing().isNotEmpty()) return Step.Ask(intent, ScenarioQuestions.ask(intent, snapshot, today))
        val result = ScenarioSimulator.simulate(snapshot, intent, today)
            ?: return Step.Ask(intent, ScenarioQuestions.ask(intent, snapshot, today))
        return Step.Simulated(intent, result)
    }
}
