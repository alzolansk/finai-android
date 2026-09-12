package com.finai.app.domain.importer

import java.time.LocalDate

/**
 * Transforma o texto corrido de uma fatura (vindo do OCR de uma foto, do
 * rasterizador de PDF ou de um .txt) em lançamentos. Determinístico e sem IA
 * (planning.md §6): a IA só entra depois, e só para o que a regra local não
 * resolveu.
 *
 * O formato real de fatura brasileira que isso precisa aguentar:
 * ```
 * 12/03  SUPERMERCADO EXTRA LOJA
 *        2233 SAO PAULO BR              189,90
 * 14/03  NETFLIX.COM                     55,90
 * 15/03  MAGAZINE LUIZA PARC 03/10      129,90
 * 18/03  ESTORNO COMPRA DUPLICADA        89,90-
 * TOTAL DESTA FATURA                   1.234,56
 * ```
 * — descrição quebrada em várias linhas, parcela embutida na descrição,
 * estorno com sinal no fim e linhas de resumo que não são lançamento.
 */
object StatementTextParser {

    /** Linhas de cabeçalho/rodapé/resumo: nunca viram lançamento. */
    private val NOISE_PREFIXES = listOf(
        "total", "subtotal", "saldo anterior", "saldo em", "saldo disponivel", "limite",
        "vencimento", "data de vencimento", "pagamento minimo", "pagamento mínimo",
        "valor minimo", "valor mínimo", "fatura anterior", "resumo", "demonstrativo",
        "pagina", "página", "linha digitavel", "linha digitável", "codigo de barras",
        "código de barras", "central de atendimento", "ouvidoria", "sac", "cnpj",
        "lancamentos", "lançamentos", "data descricao", "data descrição",
        "proximas faturas", "próximas faturas", "extrato", "titular", "cartao final",
        "cartão final",
    )

    private val NOISE_CONTAINS = listOf("www.", "http", "@", "ouvidoria")

    fun parse(rawLines: List<String>, reference: LocalDate = LocalDate.now()): ParseResult {
        val lines = rawLines
            .map { it.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotBlank() }

        val blocks = groupIntoBlocks(lines, reference)
        val entries = mutableListOf<ParsedEntry>()
        var ignored = 0

        for (block in blocks) {
            val entry = toEntry(block, reference)
            if (entry == null) ignored += block.lineCount else entries += entry
        }

        return ParseResult(entries = entries, totalLines = lines.size, ignoredLines = ignored)
    }

    private data class Block(val text: String, val lineCount: Int)

    /**
     * Um lançamento começa numa linha que tem data logo no início; as linhas
     * seguintes sem data são continuação da descrição (ou trazem só o valor) e
     * entram no mesmo bloco.
     */
    private fun groupIntoBlocks(lines: List<String>, reference: LocalDate): List<Block> {
        val blocks = mutableListOf<Block>()
        var current: StringBuilder? = null
        var currentLines = 0

        fun flush() {
            current?.let { blocks += Block(it.toString().trim(), currentLines) }
            current = null
            currentLines = 0
        }

        for (line in lines) {
            if (isNoise(line)) {
                flush()
                continue
            }
            if (startsWithDate(line, reference)) {
                flush()
                current = StringBuilder(line)
                currentLines = 1
            } else if (current != null) {
                current!!.append(' ').append(line)
                currentLines++
            }
            // Linha sem data antes de qualquer lançamento (cabeçalho solto) é descartada.
        }
        flush()
        return blocks
    }

    private fun startsWithDate(line: String, reference: LocalDate): Boolean {
        val head = line.take(14)
        val found = BrazilianStatementFormats.findDate(head, reference) ?: return false
        return found.second.first <= 2
    }

    private fun isNoise(line: String): Boolean {
        val normalized = foldAccents(line.lowercase())
        if (NOISE_CONTAINS.any { normalized.contains(it) }) return true
        return NOISE_PREFIXES.any { normalized.startsWith(foldAccents(it)) }
    }

    private fun toEntry(block: Block, reference: LocalDate): ParsedEntry? {
        val text = block.text
        val date = BrazilianStatementFormats.findDate(text, reference) ?: return null
        val amounts = BrazilianStatementFormats.findAmounts(text)
            .filter { !it.range.overlaps(date.second) }
        if (amounts.isEmpty()) return null

        val chosen = chooseAmount(text, amounts)
        val description = buildDescription(text, listOf(date.second, chosen.range))
        if (description.isBlank()) return null

        val installment = BrazilianStatementFormats.findInstallment(description)
        val refundByWord = StatementRefundWords.matches(description)
        val cents = if (refundByWord) -Math.abs(chosen.cents) else chosen.cents

        return ParsedEntry(
            date = date.first,
            description = stripInstallmentText(description, installment),
            amountCents = cents,
            installment = installment,
            rawText = text,
        )
    }

    /**
     * Qual dos valores da linha é o cobrado em reais. Compra internacional
     * costuma trazer os dois ("USD 12,00 ... R$ 62,40"), então um valor marcado
     * com R$ ganha do resto; sem marcação, vale o último da linha, que é onde a
     * coluna de valor fica na esmagadora maioria dos layouts.
     */
    private fun chooseAmount(
        text: String,
        amounts: List<BrazilianStatementFormats.AmountMatch>,
    ): BrazilianStatementFormats.AmountMatch {
        val brlMarked = amounts.filter { text.substring(it.range).contains("R$", ignoreCase = true) }
        return brlMarked.lastOrNull() ?: amounts.last()
    }

    private fun buildDescription(text: String, removed: List<IntRange>): String {
        val sb = StringBuilder()
        text.forEachIndexed { index, c ->
            if (removed.none { index in it }) sb.append(c)
        }
        return sb.toString().replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '|', '.', ',')
    }

    private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last
}

/**
 * Tira a marcação de parcela do texto da descrição — ela já está em
 * [ParsedEntry.installment], e é o app que decide como reexibi-la ("(03/10)"),
 * em vez de guardar "MAGAZINE LUIZA PARC 03/10 (03/10)".
 */
internal fun stripInstallmentText(description: String, installment: Installment?): String {
    var text = description
    if (installment != null) {
        text = text
            .replace(
                Regex("""\(?\b0?${installment.current}\s*(?:/|\s+de\s+)\s*0?${installment.total}\b\)?""", RegexOption.IGNORE_CASE),
                "",
            )
            .replace(Regex("""\bPARC(?:ELA)?\.?\s*""", RegexOption.IGNORE_CASE), "")
    }
    return text.replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '|', '.', ',')
}

/** Remove acentos para comparação de texto (nomes de estabelecimento vêm de OCR, com acento inconsistente). */
internal fun foldAccents(text: String): String = text
    .replace(Regex("[áàâãä]"), "a")
    .replace(Regex("[éèêë]"), "e")
    .replace(Regex("[íìîï]"), "i")
    .replace(Regex("[óòôõö]"), "o")
    .replace(Regex("[úùûü]"), "u")
    .replace("ç", "c")
