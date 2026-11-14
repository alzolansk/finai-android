package com.finai.app.domain.quickentry

import com.finai.app.domain.PaymentMethod
import com.finai.app.domain.TransactionType
import com.finai.app.domain.importer.MerchantClassifier
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Rascunho lido de uma frase ("uber 23 ontem"). Só o que a frase diz: o que faltar fica nulo e o
 * formulário mostra em branco, nunca chutado (planning-v2, fase 5).
 */
data class QuickDraft(
    val type: TransactionType,
    val cents: Long?,
    val description: String,
    val category: String?,
    val date: LocalDate,
    val dateExplicit: Boolean,
    val account: String?,
    val card: String?,
    val payment: PaymentMethod?,
    val installments: Int?,
    /** Transferência para objetivo/reserva: o nome do objetivo conhecido, quando a frase cita um. */
    val goalName: String?,
    /** A frase fala em guardar/reserva: o destino é um objetivo, mesmo sem nome reconhecido. */
    val toGoal: Boolean,
) {
    /** Tem o mínimo para o formulário aceitar salvar sem mais nenhum toque. */
    val complete: Boolean
        get() = cents != null && cents > 0 && when (type) {
            TransactionType.Gasto -> category != null && (payment == null || !payment.isCard)
            TransactionType.Receita -> true
            TransactionType.Transferencia -> goalName != null
        }

    /** O que a frase não disse, em palavras de tela. */
    fun missing(): List<String> = buildList {
        if (cents == null || cents <= 0) add("valor")
        if (type == TransactionType.Gasto && category == null) add("categoria")
        if (type == TransactionType.Transferencia && goalName == null) add("destino")
        if (payment?.isCard == true && card == null) add("cartão")
    }
}

/** O que o parser conhece do usuário: nomes já usados e regras aprendidas com correções. */
data class QuickEntryContext(
    val accounts: List<String> = emptyList(),
    val cards: List<String> = emptyList(),
    val goals: List<String> = emptyList(),
    /** Estabelecimento normalizado → categoria ([MerchantClassifier.normalizeMerchant]). */
    val learned: Map<String, String> = emptyMap(),
)

/**
 * Frase em português → [QuickDraft], sem IA. Serve o campo "Escrever", o "Falar" (o texto do
 * reconhecimento de voz) e o assistente ("lançar pelo chat").
 */
object QuickEntryParser {
    private val INCOME = listOf("recebi", "ganhei", "caiu", "entrou", "receita", "salario", "me pagaram", "recebido")
    private val SAVE = listOf("guardei", "guardar", "reservei", "poupei", "aportei", "aporte", "transferi", "separei")
    private val SPEND = listOf("gastei", "paguei", "comprei", "pago", "gasto", "torrei")
    private val STOP = setOf(
        "no", "na", "nos", "nas", "em", "de", "do", "da", "dos", "das", "com", "pro", "pra", "para", "o", "a", "um", "uma",
        "reais", "real", "conto", "contos", "pila", "pilas", "r$", "rs", "por", "ao", "e", "meu", "minha", "foi", "la", "hoje",
        "ontem", "anteontem", "dia", "pix", "debito", "credito", "cartao", "vezes", "parcelas", "parcelado", "vista",
    )
    private val WEEKDAYS = mapOf(
        "domingo" to DayOfWeek.SUNDAY, "segunda" to DayOfWeek.MONDAY, "terca" to DayOfWeek.TUESDAY, "quarta" to DayOfWeek.WEDNESDAY,
        "quinta" to DayOfWeek.THURSDAY, "sexta" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY,
    )
    private val AMOUNT = Regex("""(?:r\$\s*)?(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s*(mil\b|k\b)?""")
    private val INSTALLMENTS = Regex("""\b(?:em\s+)?(\d{1,2})\s*(?:x\b|vezes\b|parcelas\b)""")
    private val SLASH_DATE = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val DAY = Regex("""\bdia\s+(\d{1,2})\b""")

    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")

    /** A frase parece um pedido de lançamento (e não uma pergunta)? Usado pelo chat. */
    fun looksLikeEntry(text: String): Boolean {
        val f = fold(text).trim()
        if (f.endsWith("?") || f.startsWith("quanto") || f.startsWith("posso") || f.startsWith("se eu") || f.startsWith("como") || f.startsWith("qual")) return false
        val verb = (INCOME + SAVE + SPEND).any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(f) } || f.startsWith("lanca") || f.startsWith("anota")
        return verb && AMOUNT.containsMatchIn(f)
    }

    fun parse(text: String, today: LocalDate, ctx: QuickEntryContext = QuickEntryContext()): QuickDraft {
        var rest = " " + fold(text).replace(Regex("""^\s*(lanca|lancar|anota|anotar|registra|registrar)\b"""), " ").replace(Regex("""[!?;]"""), " ") + " "
        fun has(words: List<String>) = words.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(rest) }
        fun drop(words: List<String>) { words.forEach { rest = rest.replace(Regex("""\b${Regex.escape(it)}\b"""), " ") } }

        val type = when {
            has(SAVE) -> TransactionType.Transferencia
            has(INCOME) -> TransactionType.Receita
            else -> TransactionType.Gasto
        }
        drop(INCOME + SAVE + SPEND)

        // Data: tirada antes do valor para "dia 10" e "10/11" não virarem dinheiro.
        var date = today
        var explicit = false
        SLASH_DATE.find(rest)?.let { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
            if (d in 1..31 && mo in 1..12) {
                val year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
                val candidate = runCatching { LocalDate.of(year ?: today.year, mo, d) }.getOrNull()
                if (candidate != null) {
                    date = if (year == null && candidate.isAfter(today.plusDays(60))) candidate.minusYears(1) else candidate
                    explicit = true; rest = rest.replace(m.value, " ")
                }
            }
        }
        if (!explicit) DAY.find(rest)?.let { m ->
            val d = m.groupValues[1].toInt()
            if (d in 1..31) {
                val thisMonth = today.withDayOfMonth(d.coerceAtMost(today.lengthOfMonth()))
                date = if (thisMonth.isAfter(today)) today.minusMonths(1).let { it.withDayOfMonth(d.coerceAtMost(it.lengthOfMonth())) } else thisMonth
                explicit = true; rest = rest.replace(m.value, " ")
            }
        }
        if (!explicit) when {
            Regex("""\banteontem\b""").containsMatchIn(rest) -> { date = today.minusDays(2); explicit = true }
            Regex("""\bontem\b""").containsMatchIn(rest) -> { date = today.minusDays(1); explicit = true }
            Regex("""\bhoje\b""").containsMatchIn(rest) -> explicit = true
            else -> WEEKDAYS.entries.firstOrNull { Regex("""\b${it.key}(-feira)?\b""").containsMatchIn(rest) }?.let { (word, dow) ->
                date = today.with(TemporalAdjusters.previousOrSame(dow)); explicit = true
                rest = rest.replace(Regex("""\b(na |no )?(ultima |ultimo |passada |passado )?$word(-feira)?( passada| passado)?\b"""), " ")
            }
        }

        // Parcelas ("3x", "em 10 vezes").
        var installments: Int? = null
        INSTALLMENTS.find(rest)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 2..24 }?.let { installments = it; rest = rest.replace(m.value, " ") }
        }

        // Forma de pagamento dita com todas as letras.
        val saidCredit = Regex("""\b(credito|cartao)\b""").containsMatchIn(rest)
        val saidDebit = Regex("""\b(pix|debito|dinheiro)\b""").containsMatchIn(rest)

        // Nomes conhecidos, do mais longo para o mais curto ("nubank ultravioleta" antes de "nubank").
        fun findName(names: List<String>): String? = names.filter { it.isNotBlank() }.sortedByDescending { it.length }
            .firstOrNull { Regex("""\b${Regex.escape(fold(it))}\b""").containsMatchIn(rest) }
        var goalName: String? = null
        if (type == TransactionType.Transferencia) {
            goalName = findName(ctx.goals)
            if (goalName == null && Regex("""\breserva\b""").containsMatchIn(rest)) {
                goalName = ctx.goals.singleOrNull { fold(it).contains("reserva") }
            }
        }
        val cardName = findName(ctx.cards)
        val accountName = findName(ctx.accounts)
        var card: String? = null
        var account: String? = null
        when {
            type != TransactionType.Gasto -> account = accountName
            cardName != null && (saidCredit || installments != null) -> card = cardName
            saidDebit -> account = accountName
            accountName != null -> account = accountName
            cardName != null -> card = cardName
        }
        listOfNotNull(goalName, card, account, cardName, accountName).forEach { rest = rest.replace(Regex("""\b${Regex.escape(fold(it))}\b"""), " ") }

        val payment = when {
            type != TransactionType.Gasto -> null
            installments != null -> PaymentMethod.Parcelado
            card != null || (saidCredit && !saidDebit) -> PaymentMethod.Credito
            saidDebit || account != null -> PaymentMethod.Debito
            else -> null
        }

        // Valor: o primeiro número que sobrou.
        var cents: Long? = null
        AMOUNT.find(rest)?.let { m ->
            val raw = m.groupValues[1]
            val number = when {
                raw.contains(',') -> raw.replace(".", "").replace(',', '.')
                Regex("""\d{1,3}(\.\d{3})+""").matches(raw) -> raw.replace(".", "")
                else -> raw
            }.toBigDecimalOrNull()
            if (number != null) {
                val scaled = if (m.groupValues[2].isNotBlank()) number.multiply(java.math.BigDecimal(1000)) else number
                cents = scaled.multiply(java.math.BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).toLong().takeIf { it in 1..99_999_999_9L }
                rest = rest.replaceFirst(m.value, " ")
            }
        }

        val words = rest.split(Regex("""\s+""")).filter { it.isNotBlank() && it !in STOP && !it.all { c -> c.isDigit() } }
        val descriptionRaw = words.joinToString(" ").trim().take(40)
        // A descrição volta com a grafia da frase ("pão", não "pao"): cada palavra dobrada é
        // trocada pela original que dobra igual.
        val original = text.split(Regex("""\s+""")).map { it.trim(',', '.', '!', '?', ';', ':') }.filter { it.isNotBlank() }
            .associateBy { fold(it) }
        val description = words.joinToString(" ") { w -> original[w]?.lowercase() ?: w }.trim().take(40).replaceFirstChar { it.uppercase() }

        val category = if (type != TransactionType.Gasto || descriptionRaw.isBlank()) null else {
            val key = MerchantClassifier.normalizeMerchant(descriptionRaw)
            ctx.learned[key] ?: ctx.learned.entries.firstOrNull { key.contains(it.key) }?.value
                ?: MerchantClassifier.classify(descriptionRaw).takeIf { !it.needsReview }?.categoria
        }

        return QuickDraft(
            type = type, cents = cents, description = description, category = category, date = date, dateExplicit = explicit,
            account = account, card = card, payment = payment, installments = installments, goalName = goalName,
            toGoal = type == TransactionType.Transferencia,
        )
    }

    /** O texto do chat para um rascunho lido da frase (fase 9): o que entendeu e o que falta. */
    fun chatReply(d: QuickDraft, today: LocalDate = LocalDate.now()): String = buildString {
        val value = d.cents?.let { com.finai.app.util.formatBrl(it / 100.0) } ?: "sem valor"
        val day = when (d.date) {
            today -> "hoje"
            today.minusDays(1) -> "ontem"
            else -> "%02d/%02d".format(d.date.dayOfMonth, d.date.monthValue)
        }
        val parts = listOfNotNull(
            "${d.type.label} de $value",
            d.description.takeIf { it.isNotBlank() },
            d.category?.takeIf { d.type == TransactionType.Gasto },
            d.goalName?.let { "para $it" },
            d.card?.let { c -> if ((d.installments ?: 1) > 1) "$c em ${d.installments}×" else c } ?: d.account,
            day,
        )
        append("Lançamento pronto: ").append(parts.joinToString(" · ")).append('.')
        val missing = d.missing()
        if (missing.isEmpty() && d.complete) append(" Confira e toque em Salvar.")
        else append(" Falta ${missing.ifEmpty { listOf("conferir a forma de pagamento") }.joinToString()}: toque em Editar para completar.")
    }
}
