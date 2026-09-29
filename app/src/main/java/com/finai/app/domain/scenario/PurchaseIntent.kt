package com.finai.app.domain.scenario

import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.roundToLong

/** Como o dinheiro sai. Define a data da saída: Pix sai hoje, cartão sai na fatura. */
enum class PaymentMethod(val label: String) {
    /** Pix, débito, dinheiro, transferência: sai no dia da compra. */
    Immediate("Pix/débito"),
    /** Sai no vencimento da fatura em que cada parcela cair. */
    CreditCard("cartão de crédito"),
    /** Boleto, carnê, crediário: sai no vencimento de cada boleto. */
    Boleto("boleto/carnê"),
    /** Parcelado com as datas informadas, sem dizer se é cartão ou boleto: as datas bastam para a conta. */
    Unspecified("parcelado"),
}

/** O que ainda falta saber para simular sem chutar. */
enum class MissingField { Amount, Method, FirstDate }

/**
 * A intenção de compra extraída da mensagem do usuário, campo a campo. Nulo = o usuário não
 * disse. Nada aqui é suposto: quem decide se dá para simular é [missing], e o que faltar é
 * perguntado ao usuário ([ScenarioQuestions]).
 */
data class PurchaseIntent(
    val description: String? = null,
    /** Valor total da compra (soma das parcelas). */
    val totalCents: Long? = null,
    /** 1 = à vista. Nulo = não disse se parcela. */
    val installments: Int? = null,
    val installmentCents: Long? = null,
    /** Preço à vista quando o usuário disse que é diferente do parcelado ("à vista sai 900"). */
    val cashPriceCents: Long? = null,
    val method: PaymentMethod? = null,
    /** Data da compra à vista, ou vencimento da primeira parcela/fatura. */
    val firstDate: LocalDate? = null,
    /** "Depois do salário": a data é o próximo salário, resolvida na simulação. */
    val waitForPayday: Boolean = false,
) {
    val count: Int? get() = installments
        ?: if (installmentCents != null && totalCents != null && installmentCents > 0) {
            (totalCents.toDouble() / installmentCents).roundToLong().toInt().takeIf { it >= 1 }
        } else null

    val amountCents: Long? get() = when {
        installmentCents != null && count != null -> installmentCents * count!!
        else -> totalCents
    }

    val isInstallment: Boolean get() = (count ?: 1) > 1

    /**
     * O que falta para a simulação ser confiável. À vista sem forma de pagamento dita vale como
     * Pix/débito hoje (a saída mais cedo possível, então nunca esconde aperto); a resposta diz que
     * considerou isso. Parcelado e cartão dependem da data: sem ela, pergunta.
     */
    fun missing(): List<MissingField> = buildList {
        if (amountCents == null || amountCents!! <= 0) add(MissingField.Amount)
        val n = count
        if (n == null && method == null) {
            add(MissingField.Method)
            return@buildList
        }
        val needsDate = when {
            waitForPayday || firstDate != null -> false
            (n ?: 1) > 1 -> true
            method == PaymentMethod.CreditCard || method == PaymentMethod.Boleto -> true
            else -> false
        }
        if (needsDate) {
            if ((n ?: 1) > 1 && method == null) add(MissingField.Method)
            add(MissingField.FirstDate)
        }
    }

    /** Completa esta intenção com o que veio na resposta do usuário; o que ele não repetiu fica. */
    fun mergedWith(other: PurchaseIntent): PurchaseIntent = PurchaseIntent(
        description = other.description ?: description,
        totalCents = other.totalCents ?: totalCents,
        installments = other.installments ?: installments,
        installmentCents = other.installmentCents ?: installmentCents,
        cashPriceCents = other.cashPriceCents ?: cashPriceCents,
        method = other.method ?: method,
        firstDate = other.firstDate ?: firstDate,
        waitForPayday = other.waitForPayday || waitForPayday,
    )

    val isEmpty: Boolean get() = this == PurchaseIntent()
}

/**
 * Extrai uma [PurchaseIntent] de uma mensagem em português, sem IA: regra local não inventa
 * valor, funciona offline e não gasta cota. Reconhece "R$ 1.000", "1000 reais", "mil reais",
 * "1,5 mil", "2k", "4x", "4x de R$ 250", "10 vezes", "à vista", "à vista sai 900",
 * "10% de desconto à vista", Pix/débito/cartão/boleto, "dia 10", "10/10", "hoje", "amanhã",
 * "depois do salário".
 */
object PurchaseIntentParser {

    /** Uma pergunta nova de compra; nulo quando a mensagem não é sobre comprar algo com valor. */
    fun parse(text: String, today: LocalDate): PurchaseIntent? {
        val n = normalize(text)
        if (!looksLikePurchase(n)) return null
        val intent = extract(text, n, today, answerMode = false)
        if (intent.amountCents == null && intent.installments == null) return null
        return intent
    }

    /**
     * A resposta a uma pergunta do app ("em que dia vence a primeira parcela?"). Nulo quando a
     * mensagem não preenche nada do que faltava — aí o usuário mudou de assunto.
     */
    fun answer(pending: PurchaseIntent, text: String, today: LocalDate): PurchaseIntent? {
        val n = normalize(text)
        val missing = pending.missing()
        val got = extract(text, n, today, answerMode = true, expectingAmount = MissingField.Amount in missing,
            expectingDay = MissingField.FirstDate in missing)
        val merged = pending.mergedWith(got)
        val filled = missing.any { it !in merged.missing() } ||
            (got.installments != null && got.installments != pending.installments)
        return merged.takeIf { filled }
    }

    private val variationCue = Regex("""^\s*(e|e se|mas|agora|se|entao|e pagando|e parcelando)\b""")
    private val variationWords = Regex("""\b(\d{1,2}\s*x|vezes|parcela\w*|pix|debito|cartao|credito|boleto|carne|a vista|salario)\b""")

    /**
     * Uma variação da compra que acabou de ser simulada ("e em 6x?", "e se a primeira for dia 20?",
     * "e à vista no pix?"). Nulo quando a mensagem não muda nada da compra — aí é outra conversa.
     */
    fun variation(last: PurchaseIntent, text: String, today: LocalDate): PurchaseIntent? {
        val n = normalize(text)
        if (n.length > 80 || !(variationCue.containsMatchIn(n) || variationWords.containsMatchIn(n))) return null
        val got = extract(text, n, today, answerMode = true)
        if (got.copy(description = null) == PurchaseIntent()) return null
        // Só uma data ("e o que vence dia 10?") não é variação, a menos que fale da compra.
        val onlyDate = got.copy(description = null, firstDate = null) == PurchaseIntent()
        if (onlyDate && !Regex("""\b(primeira|1a|parcela\w*|comec\w*|pagar|pago|compr\w*)\b""").containsMatchIn(n)) return null
        val total = got.totalCents ?: last.amountCents
        // Mudou o número de parcelas ou o valor sem dizer a parcela: a parcela antiga não vale mais.
        val keepInstallment = got.installmentCents == null && got.totalCents == null && (got.installments == null || got.installments == last.count)
        val method = got.method ?: if (got.installments == 1 && got.firstDate == null) null else last.method
        return PurchaseIntent(
            description = last.description,
            totalCents = total,
            installments = got.installments ?: last.count,
            installmentCents = got.installmentCents ?: last.installmentCents.takeIf { keepInstallment },
            cashPriceCents = got.cashPriceCents ?: last.cashPriceCents,
            method = method,
            // Nova forma de pagamento ou parcelas sem data nova: a data antiga pode não valer mais.
            firstDate = got.firstDate ?: last.firstDate.takeIf { got.method == null || got.method == last.method },
            waitForPayday = got.waitForPayday,
        )
    }

    private val purchaseWords = Regex("""\b(comprar|compra|compro|parcel\w*|gastar|gasto|financiar|levar|pegar)\b""")
    private val payWord = Regex("""\bpagar\b""")
    private val notPurchase = Regex("""\b(divida|dividas|amortiz\w*|aporte|guardar|meta|metas|objetivo|emprestimo|fatura)\b""")

    private fun looksLikePurchase(n: String): Boolean {
        if (Regex("""\b(comprar|compra|compro)\b""").containsMatchIn(n)) return true
        if (notPurchase.containsMatchIn(n)) return false
        return purchaseWords.containsMatchIn(n) || payWord.containsMatchIn(n)
    }

    private data class Money(val cents: Long, val range: IntRange)

    private val moneyPatterns = listOf(
        // R$ 1.234,56 / R$1000 / R$ 1,5 mil
        Regex("""r\$\s*(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?(\s*(?:mil|k)\b)?"""),
        // 1,5 mil / 2 mil / 2k
        Regex("""\b(\d+)(?:,(\d{1,2}))?\s*(mil|k)\b"""),
        // 1.200 reais / 1200,00 reais
        Regex("""\b(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?(\s*)(?:reais|real|conto|contos)\b"""),
    )
    private val wordThousand = Regex("""\b(?:de|por|uns|um|uma)?\s*mil\s*(?:reais)?\b""")

    private fun moneyIn(n: String): List<Money> {
        val found = mutableListOf<Money>()
        moneyPatterns.forEach { p ->
            p.findAll(n).forEach { m ->
                if (found.any { it.range.first <= m.range.last && m.range.first <= it.range.last }) return@forEach
                val whole = m.groupValues[1].replace(".", "").toLongOrNull() ?: return@forEach
                val frac = m.groupValues[2].takeIf { it.isNotEmpty() }?.padEnd(2, '0')?.toLong() ?: 0
                val thousand = m.groupValues.getOrNull(3)?.trim()?.let { it == "mil" || it == "k" } == true
                val cents = if (thousand) (whole * 100 + frac) * 1000 else whole * 100 + frac
                if (cents > 0) found += Money(cents, m.range)
            }
        }
        // "mil reais" sem número
        if (found.isEmpty()) {
            wordThousand.findAll(n).firstOrNull { Regex("""\d\s*$""").find(n.substring(0, it.range.first)) == null }
                ?.let { found += Money(100_000, it.range) }
        }
        return found.sortedBy { it.range.first }
    }

    private val installmentsRe = Regex("""\b(\d{1,2})\s*(?:x|vezes|parcelas|prestacoes)\b""")
    private val cashRe = Regex("""\ba vista\b""")
    private val discountRe = Regex("""\b(\d{1,2})\s*%\s*(?:de\s*)?desconto""")
    private val slashDate = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val dayRe = Regex("""\bdia\s+(\d{1,2})\b""")
    private val paydayRe = Regex("""\b(?:depois do|apos o|no proximo|quando cair o|quando entrar o|esperar o|esperar ate o|ate o proximo)\s+(?:proximo\s+)?(?:salario|pagamento)\b|\bquando (?:eu )?receber\b""")

    private fun extract(
        original: String,
        n: String,
        today: LocalDate,
        answerMode: Boolean,
        expectingAmount: Boolean = false,
        expectingDay: Boolean = false,
    ): PurchaseIntent {
        // Datas primeiro, para "10/10" não virar valor nem parcela.
        val slash = slashDate.find(n)
        val date = slash?.let { m -> dateFrom(m, today) }
            ?: dayRe.find(n)?.let { nextDay(it.groupValues[1].toInt(), today) }
            ?: when {
                Regex("""\bhoje\b""").containsMatchIn(n) -> today
                Regex("""\bamanha\b""").containsMatchIn(n) -> today.plusDays(1)
                else -> null
            }
        val masked = slash?.let { n.replaceRange(it.range, " ".repeat(it.value.length)) } ?: n

        val inst = installmentsRe.find(masked)
        val count = inst?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..48 }
        val money = moneyIn(masked).toMutableList()
        // Número solto ("cadeira de 1000 em 4x", ou a resposta "1000"): só quando não é parcela nem dia.
        if (money.isEmpty()) {
            Regex("""(?:\b(?:de|por|custa|custando|sai|valor)\s+|^\s*)(\d{1,3}(?:\.\d{3})+|\d{2,7})(?:,(\d{2}))?\b(?!\s*(?:x|vezes|parcelas|prestacoes|%|/))""")
                .findAll(masked)
                .filterNot { m -> dayRe.findAll(masked).any { it.range.last >= m.range.first && it.range.first <= m.range.last } }
                .filter { !answerMode || expectingAmount || !expectingDay }
                .forEach { m ->
                    val whole = m.groupValues[1].replace(".", "").toLong()
                    val frac = m.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: 0
                    money += Money(whole * 100 + frac, m.groups[1]!!.range)
                }
        }
        // Resposta a "em que dia?" com só um número ("10").
        val answerDay = if (answerMode && expectingDay && date == null && count == null) {
            Regex("""^\s*(?:no\s+)?(\d{1,2})\s*$""").find(masked)?.let { nextDay(it.groupValues[1].toInt(), today) }
        } else null
        if (answerDay != null) money.clear()

        // Valor logo depois de "4x de" é a parcela; valor perto de "à vista" é o preço à vista.
        var installmentCents: Long? = null
        var cashPrice: Long? = null
        val cash = cashRe.find(masked)
        if (inst != null) {
            money.firstOrNull { it.range.first > inst.range.last && it.range.first - inst.range.last <= 6 }?.let {
                installmentCents = it.cents
                money.remove(it)
            }
        }
        if (cash != null && count != null && count > 1) {
            money.minByOrNull { minOf(kotlin.math.abs(it.range.first - cash.range.last), kotlin.math.abs(cash.range.first - it.range.last)) }
                ?.takeIf { minOf(kotlin.math.abs(it.range.first - cash.range.last), kotlin.math.abs(cash.range.first - it.range.last)) <= 12 }
                ?.let {
                    cashPrice = it.cents
                    money.remove(it)
                }
        }
        var total = money.firstOrNull()?.cents
        if (total == null && cashPrice != null && installmentCents == null) {
            total = cashPrice
            cashPrice = null
        }
        discountRe.find(masked)?.let { d ->
            val base = cashPrice ?: total ?: installmentCents?.let { c -> count?.let { c * it } }
            if (base != null && cash != null) cashPrice = base * (100 - d.groupValues[1].toInt()) / 100
        }

        val method = when {
            Regex("""\b(pix|debito|dinheiro|especie|transferencia|ted)\b""").containsMatchIn(masked) -> PaymentMethod.Immediate
            Regex("""\b(cartao|credito)\b""").containsMatchIn(masked) -> PaymentMethod.CreditCard
            Regex("""\b(boleto|carne|crediario)\b""").containsMatchIn(masked) -> PaymentMethod.Boleto
            else -> null
        }
        val installments = when {
            count != null -> count
            cash != null -> 1
            else -> null
        }

        return PurchaseIntent(
            description = if (answerMode) null else descriptionOf(original),
            totalCents = total,
            installments = installments,
            installmentCents = installmentCents,
            cashPriceCents = cashPrice?.takeIf { installments != null && installments > 1 },
            method = method,
            firstDate = date ?: answerDay,
            waitForPayday = paydayRe.containsMatchIn(masked),
        )
    }

    private fun dateFrom(m: MatchResult, today: LocalDate): LocalDate? {
        val d = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        if (mo !in 1..12 || d !in 1..31) return null
        val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
        val year = y ?: today.year
        val candidate = runCatching { LocalDate.of(year, mo, 1) }.getOrNull() ?: return null
        val date = candidate.withDayOfMonth(d.coerceAtMost(candidate.lengthOfMonth()))
        return if (y == null && date.isBefore(today)) date.plusYears(1) else date
    }

    /** O próximo dia [day] a partir de hoje (hoje conta); 31 num mês curto cai no último dia. */
    fun nextDay(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        val thisMonth = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
        if (!thisMonth.isBefore(today)) return thisMonth
        val next = today.plusMonths(1)
        return next.withDayOfMonth(day.coerceAtMost(next.lengthOfMonth()))
    }

    private val descRe = Regex(
        """\b(?:comprar|compra de|compra|compro|gastar com|financiar|parcelar|levar|pegar)\s+(?:(?:um|uma|uns|umas|o|a|os|as|esse|essa|este|esta|meu|minha)\s+)?([\p{L}0-9][\p{L}0-9 \-]{1,40}?)(?=\s+(?:de|por|em|no|na|à|a|ou|que|sem|com|pelo|pela|parcelad\p{L}*|agora|hoje|dia)\b|\s*r\$|\s*\d|[?.,!]|$)""",
        RegexOption.IGNORE_CASE,
    )

    private fun descriptionOf(original: String): String? =
        descRe.find(original.lowercase())?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && it !in setOf("algo", "isso", "coisa", "uma coisa") }
            ?.replaceFirstChar { it.uppercase() }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
