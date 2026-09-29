package com.finai.app.domain.scenario

import com.finai.app.domain.toLocalDate
import com.finai.app.util.formatBrl0
import java.time.LocalDate

/**
 * A pergunta que o assistente faz quando falta um dado que muda a conta. Sem IA: a pergunta
 * é sempre a mesma para o mesmo buraco, e não gasta cota.
 */
object ScenarioQuestions {

    fun ask(intent: PurchaseIntent, snapshot: FinanceSnapshot, today: LocalDate): String {
        val missing = intent.missing()
        val what = buildList {
            intent.description?.let { add(it.lowercase()) }
            intent.amountCents?.let { add(brl(it)) }
            intent.count?.takeIf { it > 1 }?.let { add("em ${it}x") }
        }.joinToString(" ")
        return buildString {
            append(if (what.isBlank()) "Para simular essa compra" else "Para simular $what")
            append(" sem chutar, preciso saber ")
            val parts = buildList {
                if (MissingField.Amount in missing) add("o valor total (ou o valor de cada parcela)")
                if (MissingField.Method in missing) {
                    add(if ((intent.count ?: 1) > 1) "se o parcelamento é no cartão ou no boleto/carnê" else "se vai pagar à vista (Pix/débito) ou no cartão, e em quantas vezes")
                }
                if (MissingField.FirstDate in missing) {
                    add(
                        when {
                            intent.method == PaymentMethod.CreditCard -> "em que dia vence a fatura em que a primeira parcela vai cair"
                            (intent.count ?: 1) > 1 -> "em que dia vence a primeira parcela"
                            else -> "em que dia o pagamento sai"
                        },
                    )
                }
            }
            append(joinPt(parts))
            append(".")
            if (MissingField.FirstDate in missing && intent.method != PaymentMethod.Immediate) {
                val next = snapshot.openInvoices().map { it to it.vencimento.toLocalDate() }
                    .filter { !it.second.isBefore(today) }.sortedBy { it.second }.take(2)
                if (next.isNotEmpty()) {
                    append(" Faturas em aberto que eu conheço: ")
                    append(next.joinToString("; ") { (c, d) -> "${c.nome}, vence ${dm(d)}" })
                    append(".")
                }
                append(" Pode responder assim: \"dia 10\" ou \"10/11\".")
            }
            append(" A data muda em qual ciclo cada parcela cai, por isso não vou supor.")
        }
    }

    private fun joinPt(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " e " + parts.last()
    }

    private fun brl(cents: Long) = formatBrl0(cents / 100.0)
    private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
}

/**
 * Confere a resposta da IA contra a simulação: todo valor em reais e toda data dd/MM citados
 * têm de estar no [ScenarioResult.factsBlock]. Um número fora dele é cálculo da IA — a resposta
 * é descartada e o app usa o texto determinístico ([ScenarioResult.reply]).
 */
object ScenarioReplyGuard {

    private val money = Regex("""R\$\s*-?\s*(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{2}))?""")
    private val date = Regex("""\b(\d{1,2})/(\d{1,2})\b""")

    fun amountsIn(text: String): Set<Long> = money.findAll(text).map { m ->
        // Em reais inteiros: o app escreve sem centavos; "R$ 1.000,00" vale como R$ 1.000.
        m.groupValues[1].replace(".", "").toLong()
    }.toSet()

    fun datesIn(text: String): Set<Pair<Int, Int>> = date.findAll(text).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toSet()

    /** Nulo quando a resposta só cita o que a simulação calculou; senão, o que ela inventou. */
    fun inventedIn(reply: String, facts: String): List<String> {
        val allowedMoney = amountsIn(facts)
        val allowedDates = datesIn(facts)
        return amountsIn(reply).filter { it !in allowedMoney }.map { "R$ $it" } +
            datesIn(reply).filter { it !in allowedDates }.map { "%02d/%02d".format(it.first, it.second) }
    }

    fun accepts(reply: String, facts: String): Boolean = reply.isNotBlank() && inventedIn(reply, facts).isEmpty()
}
