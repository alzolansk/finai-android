package com.finai.app.domain.plans

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.InstallmentsSummary
import com.finai.app.domain.MonthCashFlow
import com.finai.app.domain.TransactionType
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import java.time.YearMonth

data class CommitmentMonth(val month: YearMonth, val loansCents: Long, val cardCents: Long, val fixedCents: Long) {
    val installmentsCents: Long get() = loansCents + cardCents
    val totalCents: Long get() = installmentsCents + fixedCents
}

data class FixedBill(val name: String, val cents: Long, val day: Int)

/**
 * Regra 7: o que já está comprometido, mês a mês, juntando empréstimos ([DebtSchedule]),
 * parcelado no cartão ([InstallmentsSummary], inclusive parcelas estimadas de fatura importada) e
 * contas fixas recorrentes. Nada gravado: é a mesma projeção que a Agenda já usa.
 */
data class CommitmentsSchedule(
    val months: List<CommitmentMonth>,
    val fixedBills: List<FixedBill>,
    val openInvoices: List<ContaEntity>,
) {
    /** Total que ainda falta pagar em parcelas (empréstimos + cartão). */
    val installmentsOpenCents: Long get() = months.sumOf { it.installmentsCents }
    /** Parcelas do mês corrente (ou do primeiro mês com parcela). */
    val monthlyCents: Long get() = months.firstOrNull { it.installmentsCents > 0 }?.installmentsCents ?: 0
    /** Primeiro mês sem nenhuma parcela; nulo quando não há parcelas. */
    val freeFrom: YearMonth? get() = months.lastOrNull { it.installmentsCents > 0 }?.month?.plusMonths(1)
    val fixedMonthlyCents: Long get() = fixedBills.sumOf { it.cents }

    companion object {
        const val MAX_MONTHS = 36

        fun of(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            installments: InstallmentsSummary,
            today: LocalDate,
            /** Contas que são fatura de cartão ([com.finai.app.data.local.entity.FaturaCartaoEntity.contaId]). */
            invoiceContaIds: Set<Long> = emptySet(),
        ): CommitmentsSchedule {
            val current = YearMonth.from(today)
            val active = dividas.filter { it.quitadaEm == null }
            val cardByMonth = installments.openByMonth.associate { it.month to it.cents }
            val flowNow = MonthCashFlow.of(contas, transacoes, dividas, current, today)
            val fixed = (flowNow.payable.filter { it.recorrente }.map { FixedBill(it.nome, it.valorCentavos, it.vencimento.toLocalDate().dayOfMonth) } +
                flowNow.recorrentes.filter { it.tipo == TransactionType.Gasto.name }.map { FixedBill(it.descricao.ifBlank { it.categoria }, it.valorCentavos, it.data.toLocalDate().dayOfMonth) })
                .sortedBy { it.day }
            val fixedTotal = fixed.sumOf { it.cents }
            val all = (0 until MAX_MONTHS).map { current.plusMonths(it.toLong()) }.map { m ->
                CommitmentMonth(m, DebtSchedule.installmentsInMonth(active, m, today).sumOf { it.valorCentavos }, cardByMonth[m] ?: 0, fixedTotal)
            }
            val last = all.indexOfLast { it.installmentsCents > 0 }
            // Mostra até um mês depois da última parcela (o primeiro mês livre), no mínimo 6.
            val shown = all.take((last + 2).coerceIn(6, MAX_MONTHS))
            val open = contas.filter { it.tipo == "a_pagar" && it.status != "pago" && it.id in invoiceContaIds }
                .sortedBy { it.vencimento }
            return CommitmentsSchedule(shown, fixed, open)
        }
    }
}

/** Antecipar parcelas de uma dívida cara: quanto de juros deixa de pagar (aproximação simples, sem IA). */
object DebtPrepayment {
    const val EXPENSIVE_BASIS_POINTS = 300

    data class Opportunity(val debtId: Long, val name: String, val ratePercent: Double, val amountCents: Long, val savedCents: Long, val installmentsCut: Int)

    /**
     * Antecipar [amountCents] abate as **últimas** parcelas pelo valor presente. O ganho é a
     * diferença entre o valor de face das parcelas eliminadas e o que foi pago por elas.
     */
    fun opportunity(divida: DividaEntity, amountCents: Long): Opportunity? {
        val rate = divida.taxaJurosMensalBasisPoints / 10_000.0
        if (divida.quitadaEm != null || divida.taxaJurosMensalBasisPoints < EXPENSIVE_BASIS_POINTS || amountCents <= 0) return null
        if (divida.parcelasRestantes <= 1 || divida.valorParcelaCentavos <= 0) return null
        var left = amountCents.toDouble()
        var cut = 0
        var face = 0L
        var n = divida.parcelasRestantes
        while (n > 1) {
            val present = divida.valorParcelaCentavos / Math.pow(1 + rate, (n - 1).toDouble())
            if (present > left) break
            left -= present; cut++; face += divida.valorParcelaCentavos; n--
        }
        if (cut == 0) return null
        val paid = amountCents - left.toLong()
        return Opportunity(divida.id, divida.nome, divida.taxaJurosMensalBasisPoints / 100.0, paid, face - paid, cut)
    }
}
