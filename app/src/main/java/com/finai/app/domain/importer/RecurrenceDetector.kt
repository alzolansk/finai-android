package com.finai.app.domain.importer

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toLocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * Detecção de assinatura/recorrência (planning.md §3.6). Como em
 * [DuplicateDetector], a regra local resolve os casos claros e só o resto
 * sobra para a IA:
 *
 *  - [RecurrenceVerdict.LIKELY]: estabelecimento de assinatura conhecido
 *    ([MerchantClassifier.KNOWN_SUBSCRIPTIONS]) ou a mesma cobrança, com valor
 *    equivalente, em pelo menos dois meses diferentes do histórico/lote;
 *  - [RecurrenceVerdict.POSSIBLE]: sinal fraco — apareceu em exatamente um mês
 *    anterior com o mesmo valor, ou a descrição fala em plano/mensalidade;
 *  - [RecurrenceVerdict.NONE]: o resto, incluindo parcelamento, que se repete
 *    mas tem fim e não é assinatura.
 */
object RecurrenceDetector {

    /** Tolerância de variação de valor entre meses (reajuste, câmbio de assinatura em dólar). */
    private const val AMOUNT_TOLERANCE = 0.12

    private val PLAN_WORDS = listOf("mensalidade", "plano ", "assinatura", "recorrente", "subscription", "premium")

    data class Verdict(val verdict: RecurrenceVerdict, val reason: String?)

    private val NONE = Verdict(RecurrenceVerdict.NONE, null)

    fun check(
        entry: ParsedEntry,
        existing: List<TransacaoEntity>,
        batch: List<ParsedEntry>,
    ): Verdict {
        if (entry.installment != null) return NONE
        if (entry.isRefund) return NONE

        val merchant = MerchantClassifier.normalizeMerchant(entry.description)
        if (merchant.isBlank()) return NONE

        MerchantClassifier.KNOWN_SUBSCRIPTIONS.firstOrNull { merchant.contains(it) }?.let { keyword ->
            return Verdict(RecurrenceVerdict.LIKELY, "\"$keyword\" costuma ser cobrança mensal.")
        }

        val months = monthsWithSameCharge(entry, merchant, existing, batch)
        return when {
            months >= 2 -> Verdict(RecurrenceVerdict.LIKELY, "Mesmo valor cobrado em $months meses diferentes.")
            months == 1 -> Verdict(RecurrenceVerdict.POSSIBLE, "Já apareceu com o mesmo valor em outro mês.")
            PLAN_WORDS.any { merchant.contains(it) } ->
                Verdict(RecurrenceVerdict.POSSIBLE, "A descrição fala em plano/mensalidade.")
            else -> NONE
        }
    }

    private fun monthsWithSameCharge(
        entry: ParsedEntry,
        merchant: String,
        existing: List<TransacaoEntity>,
        batch: List<ParsedEntry>,
    ): Int {
        val entryMonth = entry.date?.let { YearMonth.from(it) }
        val months = mutableSetOf<YearMonth>()

        existing.forEach { transacao ->
            if (!similarAmount(transacao.valorCentavos, entry.amountCents)) return@forEach
            if (DuplicateDetector.similarity(merchant, MerchantClassifier.normalizeMerchant(transacao.descricao)) < 0.8) return@forEach
            val month = YearMonth.from(transacao.data.toLocalDate())
            if (month != entryMonth) months += month
        }

        batch.forEach { other ->
            if (other === entry) return@forEach
            if (!similarAmount(other.amountCents, entry.amountCents)) return@forEach
            if (DuplicateDetector.similarity(merchant, MerchantClassifier.normalizeMerchant(other.description)) < 0.8) return@forEach
            val month = other.date?.let { YearMonth.from(it) } ?: return@forEach
            if (month != entryMonth) months += month
        }

        return months.size
    }

    private fun similarAmount(a: Long, b: Long): Boolean {
        if (a == 0L || b == 0L) return false
        val diff = abs(abs(a) - abs(b)).toDouble()
        return diff / abs(a).toDouble() <= AMOUNT_TOLERANCE
    }
}
