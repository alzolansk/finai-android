package com.finai.app.domain.importer

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * Detecção de lançamento repetido (planning.md §3.6: "Separa duplicados para
 * você confirmar"). Regra local primeiro: mesma data + mesmo valor + mesmo
 * estabelecimento é duplicata com certeza suficiente para já entrar
 * desmarcada na revisão. O caso ambíguo — mesmo valor, data próxima,
 * descrição parecida mas não igual — vira [DuplicateVerdict.POSSIBLE], e é só
 * esse punhado que pode ir para a IA (planning.md §6).
 *
 * Compara contra dois conjuntos: o que já está no Room (importação anterior ou
 * lançamento manual) e o próprio lote sendo importado (fatura que lista a
 * mesma compra duas vezes).
 */
object DuplicateDetector {

    /** Janela em dias para considerar duas cobranças como possivelmente a mesma. */
    private const val NEAR_DAYS = 3L

    data class Verdict(val verdict: DuplicateVerdict, val reason: String?)

    private val NONE = Verdict(DuplicateVerdict.NONE, null)

    fun check(
        entry: ParsedEntry,
        existing: List<TransacaoEntity>,
        earlierInBatch: List<ParsedEntry>,
    ): Verdict {
        val date = entry.date ?: return NONE
        val merchant = MerchantClassifier.normalizeMerchant(entry.description)

        earlierInBatch.forEach { other ->
            val otherDate = other.date ?: return@forEach
            if (other.amountCents == entry.amountCents && otherDate == date &&
                sameMerchant(merchant, MerchantClassifier.normalizeMerchant(other.description))
            ) {
                return Verdict(DuplicateVerdict.LIKELY, "Aparece duas vezes neste mesmo arquivo.")
            }
        }

        var possible: Verdict? = null
        for (transacao in existing) {
            if (abs(transacao.valorCentavos) != abs(entry.amountCents)) continue
            val existingDate = transacao.data.toLocalDate()
            val days = abs(existingDate.toEpochDay() - date.toEpochDay())
            val sim = similarity(merchant, MerchantClassifier.normalizeMerchant(transacao.descricao))

            if (days == 0L && sameMerchant(merchant, MerchantClassifier.normalizeMerchant(transacao.descricao))) {
                return Verdict(
                    DuplicateVerdict.LIKELY,
                    "Já existe \"${transacao.descricao}\" em ${formatShort(existingDate)} com o mesmo valor.",
                )
            }
            if (days <= NEAR_DAYS && sim >= 0.55 && possible == null) {
                possible = Verdict(
                    DuplicateVerdict.POSSIBLE,
                    "Parecido com \"${transacao.descricao}\" de ${formatShort(existingDate)}, mesmo valor.",
                )
            }
        }
        return possible ?: NONE
    }

    private fun formatShort(date: LocalDate) = "%02d/%02d".format(date.dayOfMonth, date.monthValue)

    /**
     * Mesmo estabelecimento na prática: ou os nomes são quase iguais, ou um é
     * prefixo/sufixo do outro — "supermercado extra" e "supermercado extra
     * loja" são a mesma loja, e a distância de edição sozinha não diz isso.
     */
    private fun sameMerchant(a: String, b: String): Boolean {
        if (similarity(a, b) >= 0.85) return true
        return a.length >= 6 && b.length >= 6 && (a.contains(b) || b.contains(a))
    }

    /** Similaridade 0..1 por distância de edição normalizada — tolera o ruído de OCR e de sufixo de loja. */
    internal fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val distance = levenshtein(a, b)
        return 1.0 - distance.toDouble() / max(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }
}
