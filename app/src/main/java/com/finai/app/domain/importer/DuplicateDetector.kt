package com.finai.app.domain.importer

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.capture.NOTIFICATION_ORIGIN
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
 *
 * Compra confirmada a partir de uma notificação ([NOTIFICATION_ORIGIN]) pesa mais: o banco
 * costuma escrever o estabelecimento de um jeito na notificação e de outro na fatura, e a data
 * pode andar um dia. Mesmo valor perto da data já é "possível"; com o mesmo estabelecimento, é
 * "provável" e entra desmarcada — é o que impede somar a mesma compra duas vezes na fatura.
 * Tudo isso só sinaliza: nada é unido nem apagado, e duas compras iguais continuam podendo
 * entrar se o usuário marcar. A data comparada é sempre a da compra (a parcela grava a data da
 * compra, não o vencimento da fatura). [card] é o cartão da fatura sendo importada, quando
 * conhecido: a mesma compra em outro cartão nunca é "provável".
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
        card: String? = null,
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
            if (transacao.parcelaNumero != null && entry.installment != null &&
                transacao.parcelaNumero != entry.installment.current) continue
            if (transacao.parcelasTotal != null && entry.installment != null &&
                transacao.parcelasTotal != entry.installment.total) continue
            if (abs(transacao.valorCentavos) != abs(entry.amountCents)) continue
            val existingDate = transacao.data.toLocalDate()
            val days = abs(existingDate.toEpochDay() - date.toEpochDay())
            val existingMerchant = MerchantClassifier.normalizeMerchant(
                if (transacao.compraId != null) transacao.descricao.substringBeforeLast(" · parcela ") else transacao.descricao)
            val sim = similarity(merchant, existingMerchant)
            val fromNotification = transacao.origem == NOTIFICATION_ORIGIN
            val otherCard = card != null && (transacao.faturaId != null || transacao.compraId != null) &&
                transacao.contaOrigem.isNotBlank() && !sameCard(card, transacao.contaOrigem)

            if (days == 0L && sameMerchant(merchant, existingMerchant) && !otherCard) {
                return Verdict(
                    DuplicateVerdict.LIKELY,
                    if (fromNotification) "Já lançado pela notificação de ${formatShort(existingDate)} (\"${transacao.descricao}\"). Marque só se for outra compra."
                    else "Já existe \"${transacao.descricao}\" em ${formatShort(existingDate)} com o mesmo valor.",
                )
            }
            if (fromNotification && days <= NEAR_DAYS) {
                if (days <= 1L && sameMerchant(merchant, existingMerchant) && !otherCard) {
                    return Verdict(
                        DuplicateVerdict.LIKELY,
                        "Já lançado pela notificação de ${formatShort(existingDate)} (\"${transacao.descricao}\"). Marque só se for outra compra.",
                    )
                }
                if (possible == null) possible = Verdict(
                    DuplicateVerdict.POSSIBLE,
                    if (otherCard) "Mesmo valor de \"${transacao.descricao}\" (${formatShort(existingDate)}), lançado pela notificação no cartão ${transacao.contaOrigem}. Confira."
                    else "Mesmo valor de \"${transacao.descricao}\", lançado pela notificação em ${formatShort(existingDate)}. Confira se é a mesma compra.",
                )
                continue
            }
            if (otherCard && days == 0L && sameMerchant(merchant, existingMerchant) && possible == null) {
                possible = Verdict(DuplicateVerdict.POSSIBLE, "Mesmo valor e data de \"${transacao.descricao}\", mas no cartão ${transacao.contaOrigem}.")
                continue
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

    /** "Nubank" e "Nubank final 1234" são o mesmo cartão; "Nubank" e "Inter", não. */
    private fun sameCard(a: String, b: String): Boolean {
        val x = MerchantClassifier.normalizeMerchant(a)
        val y = MerchantClassifier.normalizeMerchant(b)
        if (x.isBlank() || y.isBlank()) return true
        return x == y || x.startsWith(y) || y.startsWith(x)
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
