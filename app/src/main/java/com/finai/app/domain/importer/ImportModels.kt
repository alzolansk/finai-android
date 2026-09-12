package com.finai.app.domain.importer

import java.time.LocalDate

/**
 * Modelos da importação de fatura (Fase 4, planning.md §3.6/§9). Tudo aqui é
 * Kotlin puro e determinístico: nenhum tipo deste arquivo depende de Android,
 * de Room ou de IA, para que todo o parsing/classificação possa ser testado na
 * JVM e rodar 100% offline no aparelho (planning.md §4).
 */

/** Tipo de arquivo escolhido pelo usuário — decide qual extrator de texto roda. */
enum class DocumentKind {
    PDF,
    /** .csv, .tsv, .txt tabular ou .xlsx */
    SPREADSHEET,
    IMAGE,
    UNSUPPORTED,
}

/** "03/10" numa fatura de cartão: parcela 3 de 10. */
data class Installment(val current: Int, val total: Int) {
    val label: String get() = "%02d/%02d".format(current, total)
}

/**
 * Um lançamento extraído do documento, já normalizado mas ainda não salvo.
 * [amountCents] segue a convenção do resto do app (ver
 * [com.finai.app.domain.SafeToSpendCalculator]): positivo = gasto, negativo =
 * estorno/crédito.
 */
data class ParsedEntry(
    val date: LocalDate?,
    val description: String,
    val amountCents: Long,
    val installment: Installment? = null,
    val rawText: String = "",
    /** Categoria que já vinha escrita no documento (coluna "categoria" de planilha), quando havia. */
    val categoryHint: String? = null,
) {
    val isRefund: Boolean get() = amountCents < 0
}

/**
 * Saída bruta de um parser (texto ou planilha), antes de classificar categoria
 * e procurar duplicata/recorrência. [ignoredLines] conta linhas que pareciam
 * lançamento mas não renderam data+valor — é o que a tela de revisão mostra
 * como "linhas não reconhecidas".
 */
data class ParseResult(
    val entries: List<ParsedEntry>,
    val totalLines: Int,
    val ignoredLines: Int,
)

/** De onde veio a categoria do lançamento — a UI mostra isso para o usuário saber no que confiar. */
enum class CategorySource {
    /** Regra local de estabelecimento (determinística, sem IA). */
    RULE,

    /** Sugerida pela IA porque a regra local não reconheceu o estabelecimento. */
    AI,

    /** Nem regra nem IA resolveram — caiu no fallback "Outros". */
    FALLBACK,

    /** Escolhida à mão pelo usuário na tela de revisão. */
    USER,
}

data class CategorySuggestion(
    val categoria: String,
    val source: CategorySource,
    /** true quando nem a regra local nem a IA tiveram certeza — a UI pede confirmação. */
    val needsReview: Boolean,
)

/** Quão parecido o lançamento é com algo que já existe no banco ou no próprio lote. */
enum class DuplicateVerdict {
    NONE,

    /** Mesma data, mesmo valor e mesma descrição normalizada — quase certamente repetido. */
    LIKELY,

    /** Valor igual e descrição/data próximas — ambíguo; é aqui que a IA pode ajudar. */
    POSSIBLE,
}

/** Quão parecido o lançamento é com uma assinatura/recorrência mensal. */
enum class RecurrenceVerdict {
    NONE,

    /** Repetição mensal comprovada no histórico, ou estabelecimento de assinatura conhecido. */
    LIKELY,

    /** Sinal fraco (ex.: valor redondo repetido uma única vez) — ambíguo; a IA pode ajudar. */
    POSSIBLE,
}

/** Uma linha da tela de revisão: o lançamento + tudo que o app descobriu sobre ele. */
data class ImportItem(
    val id: String,
    val entry: ParsedEntry,
    val categoria: String,
    val categorySource: CategorySource,
    val needsCategoryReview: Boolean,
    val duplicate: DuplicateVerdict,
    val duplicateReason: String? = null,
    val recurrence: RecurrenceVerdict,
    val recurrenceReason: String? = null,
    /** Marcado para salvar. Duplicata provável entra desmarcada por padrão. */
    val selected: Boolean,
)

/** Resultado completo de uma importação, pronto para a tela de revisão. */
data class ImportPreview(
    val sourceName: String,
    val documentKind: DocumentKind,
    val items: List<ImportItem>,
    /** true quando o texto veio do OCR (imagem ou PDF rasterizado), false quando veio de planilha. */
    val usedOcr: Boolean,
    val totalLines: Int,
    val ignoredLines: Int,
    val aiUsed: Boolean,
    /** Por que a IA não rodou (sem chave, sem rede, cota esgotada) — null quando rodou ou não foi necessária. */
    val aiNote: String? = null,
) {
    val selectedItems: List<ImportItem> get() = items.filter { it.selected }
    val duplicateCount: Int get() = items.count { it.duplicate != DuplicateVerdict.NONE }
    val reviewCount: Int get() = items.count { it.needsCategoryReview }
    val recurringCount: Int get() = items.count { it.recurrence != RecurrenceVerdict.NONE }
}
