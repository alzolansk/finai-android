package com.finai.app.domain.importer

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Primitivas determinísticas de leitura de fatura brasileira: data, valor em
 * reais e parcela. Kotlin puro de propósito (planning.md §6) — nenhuma dessas
 * conversões deve custar cota de IA, e todas precisam funcionar offline.
 *
 * Formatos cobertos (os que aparecem de fato em fatura de cartão, extrato e
 * planilha exportada no Brasil):
 *  - datas: `12/03`, `12/03/2026`, `12/03/26`, `12-03-2026`, `12.03.2026`,
 *    `2026-03-12`, `12 MAR`, `12 mar 2026`, `12 de março`;
 *  - valores: `R$ 1.234,56`, `1.234,56`, `-R$ 12,00`, `12,00-`, `(12,00)`,
 *    `1.234,56 CR`, `1234.56` (planilha exportada com ponto decimal), `1.234`
 *    (ponto como separador de milhar, sem centavos);
 *  - parcelas: `03/10`, `3/10`, `PARC 03/10`, `PARCELA 3 DE 10`, `(03/10)`.
 */
object BrazilianStatementFormats {

    private val MONTHS_PT = mapOf(
        "jan" to 1, "fev" to 2, "mar" to 3, "abr" to 4, "mai" to 5, "jun" to 6,
        "jul" to 7, "ago" to 8, "set" to 9, "out" to 10, "nov" to 11, "dez" to 12,
    )

    private val ISO_DATE = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val NUMERIC_DATE = Regex("""\b(\d{1,2})[/.\-](\d{1,2})(?:[/.\-](\d{2,4}))?\b""")
    private val NAMED_DATE = Regex(
        """\b(\d{1,2})\s*(?:\.|\s|de\s+)\s*([A-Za-zÀ-ÿ]{3,9})\.?(?:\s*(?:de\s+)?(\d{2,4}))?\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Trecho de texto que parece um valor monetário, com a posição para poder removê-lo da descrição. */
    data class AmountMatch(val cents: Long, val range: IntRange)

    /**
     * O sufixo de crédito é colado no número (`89,90-`) ou vem como sigla
     * isolada (`89,90 CR`); exigir `\b` depois da sigla evita transformar o "C"
     * de "COMPRA" em marca de crédito, e não aceitar espaço antes do "-" evita
     * ler o hífen separador de "189,90 - SAO PAULO" como sinal.
     */
    private val AMOUNT = Regex(
        """(?<![\d.,])(-)?\s*(R\$|US\$|USD|EUR)?\s*(-)?\s*(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)(-|\s?CR\b|\s?C\b|\s?D\b)?(?![\d.,])""",
        RegexOption.IGNORE_CASE,
    )

    private val INSTALLMENT_EXPLICIT = Regex(
        """\bPARC(?:ELA)?\.?\s*(\d{1,2})\s*(?:/|de|-)\s*(\d{1,2})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val INSTALLMENT_PLAIN = Regex("""\(?\b(\d{1,2})\s*(?:/|\s+de\s+)\s*(\d{1,2})\b\)?""", RegexOption.IGNORE_CASE)

    // ── datas ───────────────────────────────────────────────────────────

    /**
     * Lê a primeira data do texto. [reference] é a data de referência da fatura
     * (normalmente hoje): quando o ano não vem escrito, assume o ano que deixa a
     * data no passado recente — uma fatura de dezembro lida em janeiro é do ano
     * anterior, não do próximo.
     */
    fun parseDate(text: String, reference: LocalDate = LocalDate.now()): LocalDate? =
        findDate(text, reference)?.first

    /** Como [parseDate], mas devolve também o trecho ocupado pela data, para removê-lo da descrição. */
    fun findDate(text: String, reference: LocalDate = LocalDate.now()): Pair<LocalDate, IntRange>? {
        ISO_DATE.find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            buildDate(y.toInt(), mo.toInt(), d.toInt())?.let { return it to m.range }
        }
        NUMERIC_DATE.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearText = m.groupValues[3]
            val date = if (yearText.isEmpty()) {
                inferYear(day, month, reference)
            } else {
                buildDate(normalizeYear(yearText.toInt()), month, day)
            }
            if (date != null) return date to m.range
        }
        NAMED_DATE.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS_PT[m.groupValues[2].lowercase().take(3)]
            val yearText = m.groupValues[3]
            if (month != null) {
                val date = if (yearText.isEmpty()) {
                    inferYear(day, month, reference)
                } else {
                    buildDate(normalizeYear(yearText.toInt()), month, day)
                }
                if (date != null) return date to m.range
            }
        }
        return null
    }

    private fun inferYear(day: Int, month: Int, reference: LocalDate): LocalDate? {
        val sameYear = buildDate(reference.year, month, day) ?: return null
        // Mais de 45 dias no futuro em relação à referência = é do ano passado
        // (fatura de dez/2025 aberta em jan/2026), não do ano que vem.
        return if (sameYear.isAfter(reference.plusDays(45))) buildDate(reference.year - 1, month, day) else sameYear
    }

    private fun normalizeYear(year: Int): Int = when {
        year >= 1000 -> year
        year >= 70 -> 1900 + year
        else -> 2000 + year
    }

    private fun buildDate(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }

    // ── valores ─────────────────────────────────────────────────────────

    /** Converte um token isolado (`"R$ 1.234,56"`, `"12,00-"`, `"(35,90)"`) em centavos, ou null. */
    fun parseAmountCents(token: String): Long? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null
        val core = trimmed.removePrefix("(").removeSuffix(")").trim()
        val parenthesized = trimmed.startsWith("(") && trimmed.endsWith(")")
        val match = AMOUNT.find(core) ?: return null
        // Só aceita quando o token inteiro é o valor; sobra de texto significa
        // que o chamador deveria estar usando [findAmounts].
        if (match.range.first != 0 || match.range.last != core.lastIndex) return null
        val cents = toCents(match.groupValues[4]) ?: return null
        val negative = parenthesized || match.groupValues[1] == "-" || match.groupValues[3] == "-" ||
            isCreditSuffix(match.groupValues[5])
        return if (negative) -cents else cents
    }

    /** Todos os valores monetários do texto, na ordem em que aparecem. */
    fun findAmounts(text: String): List<AmountMatch> = AMOUNT.findAll(text).mapNotNull { m ->
        val digits = m.groupValues[4]
        // Um número solto sem separador decimal nem "R$" costuma ser número de
        // loja/parcela/código, não dinheiro — só vira valor com marca explícita.
        val looksMonetary = digits.contains(',') || digits.contains('.') || m.groupValues[2].isNotEmpty()
        if (!looksMonetary) return@mapNotNull null
        val cents = toCents(digits) ?: return@mapNotNull null
        if (cents == 0L) return@mapNotNull null
        val negative = m.groupValues[1] == "-" || m.groupValues[3] == "-" || isCreditSuffix(m.groupValues[5]) ||
            isParenthesized(text, m.range)
        AmountMatch(if (negative) -cents else cents, m.range)
    }.toList()

    private fun isParenthesized(text: String, range: IntRange): Boolean {
        val before = text.getOrNull(range.first - 1)
        val after = text.getOrNull(range.last + 1)
        return before == '(' && after == ')'
    }

    private fun isCreditSuffix(suffix: String): Boolean {
        val s = suffix.trim()
        return s == "-" || s.equals("CR", true) || s.equals("C", true)
    }

    private fun toCents(digits: String): Long? {
        val hasComma = digits.contains(',')
        val normalized = when {
            hasComma -> digits.replace(".", "").replace(',', '.')
            digits.contains('.') -> {
                val last = digits.substringAfterLast('.')
                // "1.234" = milhar (inteiro); "1234.56" = ponto decimal de planilha exportada.
                if (last.length == 3) digits.replace(".", "") else digits
            }
            else -> digits
        }
        val value = normalized.toDoubleOrNull() ?: return null
        return Math.round(value * 100)
    }

    // ── parcelas ────────────────────────────────────────────────────────

    /**
     * Extrai `3 de 10` de uma descrição. Aceita só totais plausíveis (2..72) e
     * `current <= total`, o que evita confundir com data (`12/03`) ou com código
     * de loja.
     */
    fun findInstallment(text: String): Installment? {
        INSTALLMENT_EXPLICIT.find(text)?.let { m ->
            val current = m.groupValues[1].toInt()
            val total = m.groupValues[2].toInt()
            if (isPlausibleInstallment(current, total)) return Installment(current, total)
        }
        INSTALLMENT_PLAIN.findAll(text).forEach { m ->
            val current = m.groupValues[1].toInt()
            val total = m.groupValues[2].toInt()
            if (isPlausibleInstallment(current, total)) return Installment(current, total)
        }
        return null
    }

    private fun isPlausibleInstallment(current: Int, total: Int) =
        total in 2..72 && current in 1..total
}
