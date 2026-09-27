package com.finai.app.domain.importer

import java.time.LocalDate

/** Extrai apenas campos explicitamente presentes; não fabrica banco, cartão ou datas. */
object InvoiceMetadataParser {
    private val due = Regex("""(?i)(?:data de )?vencimento\s*[:\\-]?\s*(\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?)""")
    private val closing = Regex("""(?i)fechamento\s*[:\\-]?\s*(\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?)""")
    private val card = Regex("""(?i)(?:cart[aã]o|banco)\s*[:\\-]?\s*([^\r\n|]{3,60})""")

    fun parse(lines: List<String>, referenceDate: LocalDate): InvoiceMetadata {
        val text = lines.joinToString("\n")
        return InvoiceMetadata(
            reference = card.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() },
            closingDate = date(closing.find(text)?.groupValues?.getOrNull(1), referenceDate),
            dueDate = date(due.find(text)?.groupValues?.getOrNull(1), referenceDate),
        )
    }

    private fun date(value: String?, referenceDate: LocalDate): LocalDate? = value?.let {
        BrazilianStatementFormats.findDate(it, referenceDate)?.first
    }
}
