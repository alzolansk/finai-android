package com.finai.app.data.ai

import com.finai.app.data.model.Categorias
import com.finai.app.domain.AiPromptBuilder
import com.finai.app.domain.importer.ImportItem
import com.finai.app.domain.importer.MerchantClassifier

/**
 * A única porta da importação de fatura para a camada de IA da Fase 3 — usa o
 * mesmo [AiProvider] (na prática o [AiRouter], com fallback entre os cinco
 * provedores, cota diária e cache), nunca uma integração paralela.
 *
 * Três regras que valem para tudo aqui:
 *  1. **O arquivo original nunca sai do aparelho** (planning.md §4). O que vai
 *     no prompt é o resultado do parsing local: estabelecimento já normalizado
 *     por [MerchantClassifier.normalizeMerchant] (sem código de loja, sem
 *     final de cartão, sem sequência longa de dígitos), valor e dia/mês.
 *  2. **Só o ambíguo vai** (planning.md §6). Item que a regra local classificou
 *     com confiança nunca entra em prompt nenhum.
 *  3. **Em lote**: no máximo uma chamada por tipo de pergunta por importação,
 *     com teto de [MAX_ITEMS_PER_CALL] itens, em vez de uma chamada por
 *     lançamento — é o que faz a importação caber no free tier.
 *
 * Se a IA estiver indisponível (sem chave, sem rede, cota esgotada), devolve
 * [Result.note] com o motivo e o fluxo segue inteiro com o resultado local:
 * categoria "Outros" marcada para revisão e os avisos de duplicata/recorrência
 * que a regra local já deu.
 */
class ImportAiAssistant(private val provider: AiProvider) {

    data class Result(
        /** id do [ImportItem] -> categoria sugerida pela IA (já validada contra [Categorias]). */
        val categories: Map<String, String> = emptyMap(),
        /** id do [ImportItem] -> true se a IA disse que é assinatura recorrente. */
        val recurrences: Map<String, Boolean> = emptyMap(),
        /** id do [ImportItem] -> true se a IA disse que é duplicata. */
        val duplicates: Map<String, Boolean> = emptyMap(),
        val used: Boolean = false,
        /** Motivo de a IA não ter rodado, em pt-BR, para a tela de revisão explicar. */
        val note: String? = null,
    )

    suspend fun assist(
        ambiguousCategories: List<ImportItem>,
        ambiguousRecurrences: List<ImportItem>,
        ambiguousDuplicates: List<ImportItem>,
    ): Result {
        if (ambiguousCategories.isEmpty() && ambiguousRecurrences.isEmpty() && ambiguousDuplicates.isEmpty()) {
            return Result(used = false, note = null)
        }

        var used = false
        var note: String? = null

        val categories = mutableMapOf<String, String>()
        val recurrences = mutableMapOf<String, Boolean>()
        val duplicates = mutableMapOf<String, Boolean>()

        ambiguousCategories.take(MAX_ITEMS_PER_CALL).takeIf { it.isNotEmpty() }?.let { batch ->
            when (val response = provider.generate(AiPromptBuilder.importCategories(batch.mapIndexed { i, item -> describe(i + 1, item) }, Categorias.all))) {
                is AiResponse.Success -> {
                    used = true
                    parseLines(response.text).forEach { (index, value) ->
                        val item = batch.getOrNull(index - 1) ?: return@forEach
                        MerchantClassifier.normalizeCategoryName(value)?.let { categories[item.id] = it }
                    }
                }
                is AiResponse.Unavailable -> note = response.reason
            }
        }

        ambiguousRecurrences.take(MAX_ITEMS_PER_CALL).takeIf { it.isNotEmpty() && note == null }?.let { batch ->
            when (val response = provider.generate(AiPromptBuilder.importRecurrences(batch.mapIndexed { i, item -> describe(i + 1, item) }))) {
                is AiResponse.Success -> {
                    used = true
                    parseLines(response.text).forEach { (index, value) ->
                        val item = batch.getOrNull(index - 1) ?: return@forEach
                        recurrences[item.id] = isYes(value)
                    }
                }
                is AiResponse.Unavailable -> note = response.reason
            }
        }

        ambiguousDuplicates.take(MAX_ITEMS_PER_CALL).takeIf { it.isNotEmpty() && note == null }?.let { batch ->
            when (val response = provider.generate(AiPromptBuilder.importDuplicates(batch.mapIndexed { i, item -> describeDuplicate(i + 1, item) }))) {
                is AiResponse.Success -> {
                    used = true
                    parseLines(response.text).forEach { (index, value) ->
                        val item = batch.getOrNull(index - 1) ?: return@forEach
                        duplicates[item.id] = isYes(value)
                    }
                }
                is AiResponse.Unavailable -> note = response.reason
            }
        }

        return Result(categories, recurrences, duplicates, used, note)
    }

    /** "3. mercado sao joao — R$ 189,90 em 12/03" — o máximo que sai do aparelho por lançamento. */
    private fun describe(index: Int, item: ImportItem): String {
        val merchant = MerchantClassifier.normalizeMerchant(item.entry.description).take(MAX_MERCHANT_CHARS)
        val amount = "R$ %.2f".format(Math.abs(item.entry.amountCents) / 100.0).replace('.', ',')
        val date = item.entry.date?.let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) } ?: "sem data"
        return "$index. $merchant — $amount em $date"
    }

    private fun describeDuplicate(index: Int, item: ImportItem): String =
        describe(index, item) + " | já registrado: " + (item.duplicateReason?.take(MAX_REASON_CHARS) ?: "lançamento parecido")

    /** Lê "3 | Alimentação" (ou "3 - Alimentação", "3: sim") tolerando numeração e ruído do modelo. */
    private fun parseLines(text: String): List<Pair<Int, String>> =
        text.lineSequence().mapNotNull { line ->
            val match = LINE_FORMAT.find(line.trim()) ?: return@mapNotNull null
            val index = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val value = match.groupValues[2].trim().trim('*', '"', '.', ' ')
            if (value.isEmpty()) null else index to value
        }.toList()

    private fun isYes(value: String): Boolean {
        val normalized = value.lowercase()
        return normalized.startsWith("sim") || normalized.startsWith("s ") || normalized == "s" || normalized.startsWith("yes")
    }

    companion object {
        /** Teto por chamada: mantém o prompt curto e o consumo de cota previsível. */
        const val MAX_ITEMS_PER_CALL = 25
        private const val MAX_MERCHANT_CHARS = 48
        private const val MAX_REASON_CHARS = 80
        private val LINE_FORMAT = Regex("""^\D{0,3}(\d{1,3})\s*[|\-:.)]\s*(.+)$""")
    }
}
