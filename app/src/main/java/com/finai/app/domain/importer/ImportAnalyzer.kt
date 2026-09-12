package com.finai.app.domain.importer

import com.finai.app.data.ai.ImportAiAssistant
import com.finai.app.data.local.entity.TransacaoEntity

/**
 * O miolo da importação: pega os lançamentos que um parser extraiu, resolve
 * localmente tudo que dá (categoria por regra, duplicata, recorrência) e só
 * então — se houver ambiguidade e se houver IA disponível — pede ajuda ao
 * [ImportAiAssistant] para o que sobrou.
 *
 * Não conhece Android, Uri, OCR nem Room além da entidade que já usa para
 * comparar histórico: quem lida com arquivo é
 * [com.finai.app.data.importer.StatementImporter]. Essa separação é o que
 * permite testar todo o comportamento de decisão na JVM, inclusive os casos
 * "item ambíguo sem IA" e "app sem conexão" (ver ImportAnalyzerTest).
 */
class ImportAnalyzer(private val aiAssistant: ImportAiAssistant?) {

    suspend fun analyze(
        parse: ParseResult,
        existing: List<TransacaoEntity>,
        sourceName: String,
        documentKind: DocumentKind,
        usedOcr: Boolean,
    ): ImportPreview {
        val seen = mutableListOf<ParsedEntry>()
        val items = parse.entries.mapIndexed { index, entry ->
            val category = entry.categoryHint?.let { MerchantClassifier.normalizeCategoryName(it) }
                ?.let { CategorySuggestion(it, CategorySource.RULE, needsReview = false) }
                ?: MerchantClassifier.classify(entry.description)
            val duplicate = DuplicateDetector.check(entry, existing, seen)
            val recurrence = RecurrenceDetector.check(entry, existing, parse.entries)
            seen += entry

            ImportItem(
                id = "item-$index",
                entry = entry,
                categoria = category.categoria,
                categorySource = category.source,
                needsCategoryReview = category.needsReview,
                duplicate = duplicate.verdict,
                duplicateReason = duplicate.reason,
                recurrence = recurrence.verdict,
                recurrenceReason = recurrence.reason,
                selected = duplicate.verdict != DuplicateVerdict.LIKELY,
            )
        }

        val assisted = applyAi(items)

        return ImportPreview(
            sourceName = sourceName,
            documentKind = documentKind,
            items = assisted.items,
            usedOcr = usedOcr,
            totalLines = parse.totalLines,
            ignoredLines = parse.ignoredLines,
            aiUsed = assisted.used,
            aiNote = assisted.note,
        )
    }

    private data class Assisted(val items: List<ImportItem>, val used: Boolean, val note: String?)

    private suspend fun applyAi(items: List<ImportItem>): Assisted {
        val assistant = aiAssistant ?: return Assisted(items, used = false, note = null)

        val ambiguousCategories = items.filter { it.needsCategoryReview }
        val ambiguousRecurrences = items.filter { it.recurrence == RecurrenceVerdict.POSSIBLE }
        val ambiguousDuplicates = items.filter { it.duplicate == DuplicateVerdict.POSSIBLE }
        if (ambiguousCategories.isEmpty() && ambiguousRecurrences.isEmpty() && ambiguousDuplicates.isEmpty()) {
            return Assisted(items, used = false, note = null)
        }

        val result = aiAssistantResult(assistant, ambiguousCategories, ambiguousRecurrences, ambiguousDuplicates)

        val updated = items.map { item ->
            var next = item
            result.categories[item.id]?.let { categoria ->
                next = next.copy(categoria = categoria, categorySource = CategorySource.AI, needsCategoryReview = false)
            }
            result.recurrences[item.id]?.let { isRecurring ->
                next = if (isRecurring) {
                    next.copy(recurrence = RecurrenceVerdict.LIKELY, recurrenceReason = "A IA reconheceu como assinatura recorrente.")
                } else {
                    next.copy(recurrence = RecurrenceVerdict.NONE, recurrenceReason = null)
                }
            }
            result.duplicates[item.id]?.let { isDuplicate ->
                next = if (isDuplicate) {
                    next.copy(
                        duplicate = DuplicateVerdict.LIKELY,
                        duplicateReason = (item.duplicateReason ?: "Lançamento parecido já registrado.") + " A IA confirmou que é a mesma compra.",
                        selected = false,
                    )
                } else {
                    next.copy(duplicate = DuplicateVerdict.NONE, duplicateReason = null, selected = true)
                }
            }
            next
        }

        return Assisted(updated, result.used, result.note)
    }

    private suspend fun aiAssistantResult(
        assistant: ImportAiAssistant,
        categories: List<ImportItem>,
        recurrences: List<ImportItem>,
        duplicates: List<ImportItem>,
    ): ImportAiAssistant.Result = try {
        assistant.assist(categories, recurrences, duplicates)
    } catch (e: Exception) {
        // Nenhuma falha da camada de IA pode derrubar a importação: o resultado
        // local já está pronto e continua válido (planning.md §4).
        ImportAiAssistant.Result(
            used = false,
            note = "Não foi possível consultar a IA agora (${e.message ?: e::class.java.simpleName}). " +
                "A classificação local continua valendo.",
        )
    }
}
