package com.finai.app.domain.importer

import java.time.LocalDate

/**
 * Converte a grade de células de uma planilha (vinda de [CsvReader] ou
 * [XlsxReader]) em lançamentos. Determinístico, sem IA (planning.md §6).
 *
 * Reconhece o cabeçalho quando ele existe (Data / Descrição / Valor, com as
 * variações que bancos e o próprio Excel em pt-BR costumam usar) e, quando não
 * existe, cai numa heurística por linha: primeira célula que é data, última que
 * é valor, maior célula de texto restante como descrição.
 *
 * Convenção de sinal: o app trata gasto como positivo (ver
 * [com.finai.app.domain.SafeToSpendCalculator]). Exportação de banco costuma
 * fazer o contrário, então quando a maioria dos valores da planilha é negativa
 * o parser inverte o sinal do arquivo inteiro — e não linha a linha, para não
 * misturar convenções dentro da mesma importação.
 */
object SpreadsheetStatementParser {

    private val DATE_HEADERS = listOf("data", "date", "dia", "data da compra", "data compra", "competencia")
    private val DESCRIPTION_HEADERS = listOf(
        "descricao", "descrição", "historico", "histórico", "estabelecimento", "lancamento",
        "lançamento", "description", "memo", "detalhe", "titulo", "título", "nome",
    )
    private val AMOUNT_HEADERS = listOf("valor", "amount", "montante", "quantia", "total", "valor (r$)", "valor r$")
    private val DEBIT_HEADERS = listOf("debito", "débito", "saida", "saída", "gasto", "despesa")
    private val CREDIT_HEADERS = listOf("credito", "crédito", "entrada", "receita", "pagamento")
    private val INSTALLMENT_HEADERS = listOf("parcela", "parcelas", "parcelamento")
    private val CATEGORY_HEADERS = listOf("categoria", "category", "classificacao", "classificação")

    private data class Columns(
        val date: Int,
        val description: Int,
        val amount: Int?,
        val debit: Int?,
        val credit: Int?,
        val installment: Int?,
        val category: Int?,
    )

    fun parse(rows: List<List<String>>, reference: LocalDate = LocalDate.now()): ParseResult {
        if (rows.isEmpty()) return ParseResult(emptyList(), 0, 0)

        val headerIndex = rows.indexOfFirst { isHeaderRow(it) }.takeIf { it in 0..14 }
        val columns = headerIndex?.let { mapColumns(rows[it]) }
        val bodyRows = rows.drop((headerIndex ?: -1) + 1)

        val raw = bodyRows.map { row -> toRawEntry(row, columns, reference) }
        val parsed = raw.filterNotNull()
        val flipSign = parsed.count { it.amountCents < 0 } > parsed.count { it.amountCents > 0 }

        val entries = parsed.map { entry ->
            val cents = if (flipSign) -entry.amountCents else entry.amountCents
            val refundByWord = StatementRefundWords.matches(entry.description)
            entry.copy(amountCents = if (refundByWord) -Math.abs(cents) else cents)
        }

        return ParseResult(
            entries = entries,
            totalLines = bodyRows.size,
            ignoredLines = raw.count { it == null },
        )
    }

    private fun isHeaderRow(row: List<String>): Boolean {
        val cells = row.map { normalize(it) }
        val hasDate = cells.any { cell -> DATE_HEADERS.any { cell == it || cell.startsWith(it) } }
        val hasValue = cells.any { cell ->
            (AMOUNT_HEADERS + DEBIT_HEADERS + CREDIT_HEADERS).any { cell == it || cell.startsWith(it) }
        }
        return hasDate && hasValue
    }

    private fun mapColumns(header: List<String>): Columns {
        val cells = header.map { normalize(it) }
        fun find(options: List<String>): Int? =
            cells.indexOfFirst { cell -> options.any { cell == it || cell.startsWith(it) } }.takeIf { it >= 0 }

        return Columns(
            date = find(DATE_HEADERS) ?: 0,
            description = find(DESCRIPTION_HEADERS) ?: 1,
            amount = find(AMOUNT_HEADERS),
            debit = find(DEBIT_HEADERS),
            credit = find(CREDIT_HEADERS),
            installment = find(INSTALLMENT_HEADERS),
            category = find(CATEGORY_HEADERS),
        )
    }

    private fun toRawEntry(row: List<String>, columns: Columns?, reference: LocalDate): ParsedEntry? =
        if (columns == null) inferEntry(row, reference) else mappedEntry(row, columns, reference)

    private fun mappedEntry(row: List<String>, columns: Columns, reference: LocalDate): ParsedEntry? {
        val date = row.getOrNull(columns.date)?.let { readDate(it, reference) } ?: return null
        val description = row.getOrNull(columns.description).orEmpty().trim()
            .ifBlank { row.filterIndexed { i, cell -> i != columns.date && cell.isNotBlank() && readAmount(cell) == null }.maxByOrNull { it.length }.orEmpty() }
        if (description.isBlank()) return null

        val debit = columns.debit?.let { row.getOrNull(it) }?.let(::readAmount)
        val credit = columns.credit?.let { row.getOrNull(it) }?.let(::readAmount)
        val cents = when {
            columns.debit != null && columns.credit != null ->
                when {
                    debit != null && debit != 0L -> Math.abs(debit)
                    credit != null && credit != 0L -> -Math.abs(credit)
                    else -> return null
                }
            else -> columns.amount?.let { row.getOrNull(it) }?.let(::readAmount) ?: return null
        }

        val installment = columns.installment?.let { row.getOrNull(it) }?.let { BrazilianStatementFormats.findInstallment(it) }
            ?: BrazilianStatementFormats.findInstallment(description)
        val category = columns.category?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotBlank() }

        return ParsedEntry(
            date = date,
            description = stripInstallmentText(description, installment),
            amountCents = cents,
            installment = installment,
            rawText = row.joinToString(" | "),
            categoryHint = category,
        )
    }

    /** Sem cabeçalho: primeira célula que vira data, última que vira valor, maior texto restante como descrição. */
    private fun inferEntry(row: List<String>, reference: LocalDate): ParsedEntry? {
        val dateIndex = row.indexOfFirst { readDate(it, reference) != null }.takeIf { it >= 0 } ?: return null
        val date = readDate(row[dateIndex], reference) ?: return null
        val amountIndex = row.indices.lastOrNull { it != dateIndex && readAmount(row[it]) != null } ?: return null
        val cents = readAmount(row[amountIndex]) ?: return null
        val description = row.filterIndexed { i, cell ->
            i != dateIndex && i != amountIndex && cell.isNotBlank()
        }.maxByOrNull { it.length }?.trim().orEmpty()
        if (description.isBlank()) return null

        val installment = BrazilianStatementFormats.findInstallment(description)
        return ParsedEntry(
            date = date,
            description = stripInstallmentText(description, installment),
            amountCents = cents,
            installment = installment,
            rawText = row.joinToString(" | "),
        )
    }

    private fun readAmount(cell: String): Long? {
        val trimmed = cell.trim()
        if (trimmed.isBlank()) return null
        return BrazilianStatementFormats.parseAmountCents(trimmed)
    }

    /**
     * Data de célula: texto normal, ou o número de série do Excel (dias desde
     * 30/12/1899) que é como o .xlsx guarda datas quando não há formatação de
     * texto. A faixa aceita (20.000..80.000) cobre 1954..2119 e evita confundir
     * um valor solto com data.
     */
    private fun readDate(cell: String, reference: LocalDate): LocalDate? {
        val trimmed = cell.trim()
        if (trimmed.isBlank()) return null
        BrazilianStatementFormats.findDate(trimmed, reference)?.let { return it.first }
        val serial = trimmed.toDoubleOrNull()?.toLong() ?: return null
        if (serial !in 20_000..80_000) return null
        return EXCEL_EPOCH.plusDays(serial)
    }

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    private fun normalize(text: String) = foldAccents(text.lowercase().trim()).replace(Regex("""\s+"""), " ")
}

/** Palavras que marcam estorno/crédito numa descrição — compartilhado pelos dois parsers. */
internal object StatementRefundWords {
    private val WORDS = listOf(
        "estorno", "estornado", "devolucao", "reembolso", "credito recebido",
        "pagamento recebido", "pgto recebido", "pagamento efetuado", "cashback",
    )

    fun matches(description: String): Boolean {
        val normalized = foldAccents(description.lowercase())
        return WORDS.any { normalized.contains(it) }
    }
}
