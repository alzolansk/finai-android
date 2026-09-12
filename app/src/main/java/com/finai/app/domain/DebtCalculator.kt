package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.ln

private val ptBr: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()

data class DebtPlan(
    val divida: DividaEntity,
    val rank: Int,
    val monthlyInterestCents: Long,
    val progress: Float,
)

data class DebtSummary(
    val ordered: List<DebtPlan>,
    val totalOpenCents: Long,
    val totalMonthlyInterestCents: Long,
    /** null when at least one debt's parcela doesn't cover its own juros — it never pays itself off as configured. */
    val debtFreeDate: LocalDate?,
    val hasUnpayableDebt: Boolean,
)

/**
 * Ordem de ataque por custo do juro + previsão de "livre de dívida" —
 * planning.md §3.4/§6. Pure interest-rate math (loan amortization), no AI:
 * each debt with a fixed parcela is projected independently with the
 * standard amortization formula; the whole-portfolio "livre em" date is the
 * slowest of them (a conservative estimate — it assumes no extra payment is
 * redirected from a debt that finishes early, which the user is always free
 * to do sooner than this date suggests).
 */
object DebtCalculator {
    fun summarize(dividas: List<DividaEntity>, today: LocalDate = LocalDate.now()): DebtSummary {
        // DAO already orders by taxaJurosMensalBasisPoints DESC; re-sort defensively so this
        // function is correct even if called with an unsorted list.
        val ordered = dividas.sortedByDescending { it.taxaJurosMensalBasisPoints }
        val plans = ordered.mapIndexed { index, divida ->
            DebtPlan(
                divida = divida,
                rank = index + 1,
                monthlyInterestCents = divida.valorAbertoCentavos * divida.taxaJurosMensalBasisPoints / 10_000,
                progress = if (divida.valorOriginalCentavos <= 0) 0f
                else (1f - divida.valorAbertoCentavos.toFloat() / divida.valorOriginalCentavos).coerceIn(0f, 1f),
            )
        }

        var hasUnpayable = false
        val monthsToPayoff = ordered.mapNotNull { d ->
            if (d.valorAbertoCentavos <= 0) return@mapNotNull null
            // No parcela defined for an open balance (e.g. a revolving card) means there is no
            // schedule that ever pays it off — that must sink the whole projection, not be skipped.
            if (d.valorParcelaCentavos <= 0) {
                hasUnpayable = true
                return@mapNotNull null
            }
            val rate = d.taxaJurosMensalBasisPoints / 10_000.0
            val balance = d.valorAbertoCentavos.toDouble()
            val payment = d.valorParcelaCentavos.toDouble()
            val months = when {
                rate <= 0.0 -> ceil(balance / payment)
                payment <= balance * rate -> {
                    hasUnpayable = true
                    null
                }
                else -> ceil(ln(payment / (payment - balance * rate)) / ln(1 + rate))
            }
            months
        }

        val debtFreeDate = if (hasUnpayable || monthsToPayoff.isEmpty()) null
        else today.plusMonths(monthsToPayoff.max().toLong())

        return DebtSummary(
            ordered = plans,
            totalOpenCents = dividas.sumOf { it.valorAbertoCentavos },
            totalMonthlyInterestCents = plans.sumOf { it.monthlyInterestCents },
            debtFreeDate = debtFreeDate,
            hasUnpayableDebt = hasUnpayable,
        )
    }

    fun rateLabel(basisPoints: Int): String = String.format(ptBr, "%.1f%% a.m.", basisPoints / 100.0)
}
