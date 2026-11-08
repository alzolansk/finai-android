package com.finai.app.domain.capture

import com.finai.app.domain.TransactionEntry
import com.finai.app.domain.importer.CategorySource
import com.finai.app.domain.importer.MerchantClassifier
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Interpreta o texto de uma notificação financeira, localmente e sem IA (o texto nunca sai do
 * aparelho). Regras fixas, cobertas por `NotificationParserTest`; o que elas não reconhecem é
 * descartado ou pede revisão — o parser nunca chuta valor, forma de pagamento, cartão,
 * vencimento ou parcelas.
 *
 * Formatos cobertos (ver os exemplos em [CaptureSamples] e nos testes):
 * - compra aprovada no crédito, no débito ou sem dizer a forma ("Compra aprovada de R$ 42,90 no Mercado");
 * - compra parcelada com número de parcelas explícito ("R$ 300,00 em 3x de R$ 100,00");
 * - Pix enviado e Pix recebido;
 * - estorno/cancelamento, pagamento de fatura e transferência (TED/DOC): só reconhecidos, para revisão;
 * - ignorados: compra recusada, propaganda/oferta de crédito, aviso de limite ou saldo, mensagem sem valor.
 *
 * Valores só em reais ("R$ 1.234,56", "R$1234,56"). Limite, saldo e cashback citados na mensagem
 * nunca são tomados como o valor. Data: a escrita na mensagem ("em 12/10"), senão o dia da notificação.
 */
object NotificationParser {

    fun parse(
        n: CapturedNotification,
        knownAccounts: List<String> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ParseOutcome> {
        val postedDay = Instant.ofEpochMilli(n.postedAt).atZone(zone).toLocalDate()
        // Notificação agrupada (estilo caixa de entrada): cada linha que for uma movimentação vira
        // uma sugestão própria. Uma linha só, ou linhas que não se sustentam sozinhas, valem juntas.
        val lineOutcomes = n.lines.map { it.trim() }.filter { it.isNotEmpty() }
            .mapIndexed { i, line -> i to parseText(line, postedDay, knownAccounts) }
        val movements = lineOutcomes.filter { it.second is ParseOutcome.Movement }
        if (movements.size >= 2) {
            return movements.map { (i, outcome) -> (outcome as ParseOutcome.Movement).copy(lineIndex = i) }
        }
        return listOf(parseText(n.body(), postedDay, knownAccounts))
    }

    /** Uma mensagem só (título + texto já juntados). */
    fun parseText(raw: String, postedDay: LocalDate, knownAccounts: List<String> = emptyList()): ParseOutcome {
        val text = raw.replace(' ', ' ').replace(Regex("""[ \t]+"""), " ").trim()
        val f = fold(text)
        if (f.isBlank()) return ParseOutcome.Ignored(IgnoreReason.NoMovement)

        if (DECLINED.containsMatchIn(f)) return ParseOutcome.Ignored(IgnoreReason.Declined)
        val hasVerb = TX_VERB.containsMatchIn(f)
        if (!hasVerb && PROMO.containsMatchIn(f)) return ParseOutcome.Ignored(IgnoreReason.Promotion)

        val kind = kindOf(f) ?: return ParseOutcome.Ignored(
            if (LIMIT_WORDS.containsMatchIn(f)) IgnoreReason.LimitInfo else IgnoreReason.NoMovement,
        )

        val amounts = amountsIn(text, f)
        val count = if (kind == MovementKind.InstallmentPurchase) installmentCount(f) else null
        val resolved = resolveAmount(kind, amounts, count) ?: return ParseOutcome.Ignored(IgnoreReason.NoAmount)

        val counterpart = counterpartOf(kind, text, f)
        val (date, explicit) = dateOf(f, postedDay)
        val category = if (kind.isIncome || kind.reviewOnly) null else categoryOf(counterpart)
        val movement = ParsedMovement(
            kind = kind,
            amountCents = resolved.total,
            candidatesCents = resolved.candidates,
            counterpart = counterpart,
            date = date,
            dateExplicit = explicit,
            installments = count,
            installmentCents = resolved.perInstallment,
            cardLastDigits = LAST_DIGITS.find(f)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() },
            accountHint = knownAccounts.firstOrNull { name -> mentions(f, name) },
            category = category,
            ambiguous = resolved.ambiguous,
        )
        return ParseOutcome.Movement(movement, signatureOf(text))
    }

    /** Identificador do conteúdo: o mesmo texto (sem diferença de caixa/espaço/acento) dá a mesma assinatura. */
    fun signatureOf(text: String): String {
        val normalized = fold(text).replace(Regex("""\s+"""), " ").trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    // ── tipo ─────────────────────────────────────────────────────────

    private val DECLINED = Regex("""recusad|negad|nao (foi )?(aprovad|autorizad|concluid|realizad|efetuad)|bloquead|nao pud|falhou|sem sucesso|saldo insuficiente""")
    private val TX_VERB = Regex("""(?<!pre-)(?<!pre )(?<!pre)aprovad|realizad|efetuad|enviad|recebid|confirmad|voce (comprou|pagou|enviou|recebeu|fez)|debitad|creditad|estornad|registrad|concluid|\bcompra (no|na|de|com|em)\b|\bcompra:|\bpix (enviado|recebido)""")
    private val PROMO = Regex("""oferta|aproveite|pre[- ]?aprovad|emprestimo|consignado|promocao|desconto|convite|conheca|simule|contrate|baixe|clique|ganhe|aumentou|aumentamos|novo limite|disponivel para|sem juros|ate \d{1,2} ?x|saiba mais|cashback de ate|parabens|exclusiv""")
    private val LIMIT_WORDS = Regex("""limite|saldo""")

    private val REFUND = Regex("""estorn|reembols|devolvid|devolucao|cancelad|cancelament""")
    private val INVOICE_PAYMENT = Regex("""pagamento (da|de|do) (sua )?fatura|fatura (foi )?paga|pagamento (da fatura )?recebido|recebemos (o )?(seu )?pagamento""")
    private val PIX_RECEIVED = Regex("""pix recebido|recebeu (um )?pix|voce recebeu|recebimento (de |via )?pix|pix de .{0,60}(foi )?recebido""")
    private val PIX_SENT = Regex("""pix enviado|enviou (um )?pix|pix (realizado|efetuado|feito|concluido|confirmado)|fez um pix|pagamento (via|por|com) pix|transferencia pix (enviada|realizada)|\bpix para\b""")
    private val TRANSFER = Regex("""transferencia (enviada|realizada|efetuada|recebida|agendada)|\bted\b|\bdoc\b""")
    private val PURCHASE = Regex("""\bcompras? (no|na|de|com|em|aprovadas?|realizadas?|efetuadas?|parceladas?|confirmadas?|feitas?)\b|\bcompras?:|voce comprou|pagamento (aprovado|realizado|efetuado|confirmado)|debito automatico|\bcompra\b""")
    private val INSTALLMENT = Regex("""\b\d{1,2} ?x\b|\d{1,2} parcelas|em \d{1,2} vezes|parcelad""")
    private val CREDIT = Regex("""credito""")
    private val DEBIT = Regex("""debito|conta corrente""")

    private fun kindOf(f: String): MovementKind? = when {
        REFUND.containsMatchIn(f) -> MovementKind.Refund
        INVOICE_PAYMENT.containsMatchIn(f) -> MovementKind.InvoicePayment
        PIX_RECEIVED.containsMatchIn(f) -> MovementKind.PixReceived
        PIX_SENT.containsMatchIn(f) -> MovementKind.PixSent
        TRANSFER.containsMatchIn(f) -> MovementKind.Transfer
        PURCHASE.containsMatchIn(f) -> when {
            INSTALLMENT.containsMatchIn(f) -> MovementKind.InstallmentPurchase
            CREDIT.containsMatchIn(f) && !DEBIT.containsMatchIn(f) -> MovementKind.CreditPurchase
            DEBIT.containsMatchIn(f) && !CREDIT.containsMatchIn(f) -> MovementKind.DebitPurchase
            else -> MovementKind.Purchase
        }
        else -> null
    }

    private val COUNT = listOf(
        Regex("""\b(\d{1,2}) ?x\b"""),
        Regex("""\b(\d{1,2}) parcelas"""),
        Regex("""em (\d{1,2}) vezes"""),
    )

    private fun installmentCount(f: String): Int? = COUNT.firstNotNullOfOrNull { r ->
        r.find(f)?.groupValues?.get(1)?.toIntOrNull()
    }?.takeIf { it in 2..120 }

    // ── valores ──────────────────────────────────────────────────────

    private enum class AmountRole { Plain, Total, Installment, Excluded }
    private data class Amount(val cents: Long, val role: AmountRole)

    private val MONEY = Regex("""(?<![A-Za-z])R\$ ?-?((?:\d{1,3}(?:\.\d{3})+)|\d+),(\d{2})(?!\d)""")
    private val EXCLUDED_BEFORE = Regex("""(limite|saldo|disponivel|cashback|fatura atual|total da fatura|anuidade|tarifa de)[^.!?\n]{0,20}$""")
    private val EXCLUDED_AFTER = Regex("""^ ?(de limite|de saldo|disponive|de cashback|em limite)""")
    private val INSTALLMENT_BEFORE = Regex("""(\d{1,2} ?x( de)?|parcelas? de|vezes de|cada parcela( de)?:?|por mes:?) ?$""")
    private val INSTALLMENT_AFTER = Regex("""^ ?(por mes|/mes|cada|mensais|por parcela)""")
    private val TOTAL_BEFORE = Regex("""(total( de)?:?|valor total( de)?:?|no total de) ?$""")
    private val TOTAL_AFTER = Regex("""^ ?(no total|ao todo|total)""")

    private fun amountsIn(text: String, f: String): List<Amount> = MONEY.findAll(text).map { m ->
        val cents = m.groupValues[1].replace(".", "").toLong() * 100 + m.groupValues[2].toLong()
        val before = f.substring(0, m.range.first).takeLast(40)
        val after = f.substring(m.range.last + 1).take(20)
        // Valor em outra moeda logo antes ("US$ 10,00 (R$ 52,30)") não conta: o regex só pega "R$".
        val role = when {
            EXCLUDED_BEFORE.containsMatchIn(before) || EXCLUDED_AFTER.containsMatchIn(after) -> AmountRole.Excluded
            INSTALLMENT_BEFORE.containsMatchIn(before) || INSTALLMENT_AFTER.containsMatchIn(after) -> AmountRole.Installment
            TOTAL_BEFORE.containsMatchIn(before) || TOTAL_AFTER.containsMatchIn(after) -> AmountRole.Total
            else -> AmountRole.Plain
        }
        Amount(cents, role)
    }.filter { it.cents > 0 }.toList()

    private data class Resolved(val total: Long?, val perInstallment: Long?, val candidates: List<Long>, val ambiguous: Boolean)

    private fun resolveAmount(kind: MovementKind, amounts: List<Amount>, count: Int?): Resolved? {
        val usable = amounts.filter { it.role != AmountRole.Excluded }
        if (usable.isEmpty()) return null
        val totals = usable.filter { it.role == AmountRole.Total }.map { it.cents }.distinct()
        val perOnes = usable.filter { it.role == AmountRole.Installment }.map { it.cents }.distinct()
        val plain = usable.filter { it.role == AmountRole.Plain }.map { it.cents }.distinct()
        val all = usable.map { it.cents }.distinct()
        fun ambiguous() = Resolved(null, null, all, ambiguous = true)
        fun consistent(total: Long, per: Long, n: Int) = kotlin.math.abs(total - per * n) <= n

        if (kind != MovementKind.InstallmentPurchase) {
            return if (all.size == 1) Resolved(all.single(), null, emptyList(), false) else ambiguous()
        }
        // Parcelada: total e valor da parcela precisam fechar com o número de parcelas.
        val totalCandidates = (totals + plain).distinct()
        if (count == null) {
            return if (totalCandidates.size == 1 && perOnes.isEmpty()) Resolved(totalCandidates.single(), null, emptyList(), false)
            else ambiguous()
        }
        val per = perOnes.singleOrNull()
        return when {
            perOnes.size > 1 -> ambiguous()
            per != null && totalCandidates.isEmpty() -> Resolved(per * count, per, emptyList(), false)
            per != null && totalCandidates.size == 1 ->
                if (consistent(totalCandidates.single(), per, count)) Resolved(totalCandidates.single(), per, emptyList(), false) else ambiguous()
            per == null && totalCandidates.size == 1 -> Resolved(totalCandidates.single(), null, emptyList(), false)
            per == null && totalCandidates.size == 2 -> {
                val (small, big) = totalCandidates.sorted()
                if (consistent(big, small, count)) Resolved(big, small, emptyList(), false) else ambiguous()
            }
            else -> ambiguous()
        }
    }

    // ── quem / onde ──────────────────────────────────────────────────

    private val STOP_AFTER_PREP = Regex("""^(o |a |os |as )?(credito|debito|cartao|conta|seu |sua |valor|r\$|\d|dia |final|limite|app|aplicativo|fatura|pix|voce|loja virtual$)""")
    private val END = Regex("""\s+(para o|para a|para seu|para sua|no cartao|no seu|na sua|na conta|com o cartao|com cartao|com seu|cartao|as \d|em \d|final \d|no credito|no debito|no valor|foi|esta|aprovad|realizad|efetuad|confirmad|recebid|enviad|via|pelo|pela|dia \d)\b|\s+(e )?r\$|\s+-\s|[.;:,!?\n(]""")

    private fun counterpartOf(kind: MovementKind, text: String, f: String): String {
        val preps = when (kind) {
            MovementKind.PixSent, MovementKind.Transfer -> listOf(" para ", " p/ ")
            MovementKind.PixReceived -> listOf(" de ", " do ", " da ")
            else -> listOf(" em ", " no ", " na ", " com ")
        }
        var best: Pair<Int, String>? = null
        for (prep in preps) {
            var from = 0
            while (true) {
                val at = f.indexOf(prep, from).takeIf { it >= 0 } ?: break
                from = at + 1
                val start = at + prep.length
                val tail = f.substring(start)
                if (STOP_AFTER_PREP.containsMatchIn(tail)) continue
                val end = END.find(tail)?.range?.first ?: tail.length
                val candidate = text.substring(start, start + end.coerceAtMost(48)).trim().trimEnd('-', ':').trim()
                if (candidate.length < 2 || candidate.none { it.isLetter() }) continue
                if (best == null || at < best.first) best = at to candidate
                break
            }
        }
        return best?.second?.let(::tidy) ?: ""
    }

    /** "MERCADO SAO JOAO" → "Mercado Sao Joao"; nomes já em caixa mista ficam como vieram. */
    private fun tidy(s: String): String {
        val clean = s.replace(Regex("""\s+"""), " ").trim()
        val letters = clean.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return clean
        return clean.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    private val LAST_DIGITS = Regex("""final (\d{4})\b|\*{2,} ?(\d{4})\b""")

    private fun mentions(f: String, name: String): Boolean {
        val key = fold(name).trim()
        if (key.length < 3) return false
        return Regex("""(?<![a-z0-9])${Regex.escape(key)}(?![a-z0-9])""").containsMatchIn(f)
    }

    private fun categoryOf(counterpart: String): String? {
        if (counterpart.isBlank()) return null
        val suggestion = MerchantClassifier.classify(counterpart)
        if (suggestion.source != CategorySource.RULE) return null
        val name = if (suggestion.categoria == "Assinaturas") "Serviços" else suggestion.categoria
        return name.takeIf { it in TransactionEntry.categories }
    }

    // ── data ─────────────────────────────────────────────────────────

    private val DATE = Regex("""(?:\bem|\bdia|\bdata:?|\bno dia|,)\s+(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?![\d/])""")

    private fun dateOf(f: String, postedDay: LocalDate): Pair<LocalDate, Boolean> {
        if (Regex("""\bontem\b""").containsMatchIn(f)) return postedDay.minusDays(1) to true
        val m = DATE.find(f) ?: return postedDay to false
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
        val parsed = runCatching { LocalDate.of(year ?: postedDay.year, month, day) }.getOrNull() ?: return postedDay to false
        // Sem ano, uma data "no futuro" é do ano passado (notificação de 02/01 falando de 30/12).
        val date = if (year == null && parsed.isAfter(postedDay.plusDays(1))) parsed.minusYears(1) else parsed
        return date to true
    }

    // ── texto ────────────────────────────────────────────────────────

    /** Minúsculas sem acento, com o mesmo comprimento do original (as posições batem). */
    internal fun fold(s: String): String = buildString(s.length) {
        for (c in s) {
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
            append(base.lowercaseChar())
        }
    }
}
