package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * Sobra projetada mês a mês, do mês corrente em diante — o que uma meta com
 * prazo em 2028 pode de fato usar. A capacidade do mês corrente
 * ([SavingsCapacityCalculator]) sozinha dizia "R$ 114/mês" e condenava metas
 * que cabem com folga nos meses seguintes (parcelas de dívida acabando,
 * salário maior, 13º).
 *
 * Cada mês é o mesmo balanço da Agenda ([MonthCashFlow]): recorrentes pela
 * ocorrência do mês, parcelas de dívida só enquanto existirem, lançamentos
 * futuros já cadastrados (entradas extras incluídas — é dinheiro que chega).
 *
 * Salário: quem lança o salário avulso, mês a mês, não tem salário cadastrado
 * para 2027 — sem estimativa, todo mês futuro ficaria só com as despesas. Então,
 * depois do último salário lançado, todo mês sem salário recebe o valor do
 * último como estimativa ([MonthProjection.estimatedSalaryCents]). Salário
 * recorrente já se projeta sozinho e não precisa disso.
 */
data class MonthProjection(
    val month: YearMonth,
    val balanceCents: Long,
    /** Parte de [balanceCents] que é salário estimado (0 quando o mês tem salário lançado). */
    val estimatedSalaryCents: Long,
)

data class SavingsProjection(val months: List<MonthProjection>) {

    /** Sobra somada do mês corrente até [month], inclusive. Meses negativos descontam. */
    fun cumulativeThrough(month: YearMonth): Long = months.filter { it.month <= month }.sumOf { it.balanceCents }

    val usesEstimatedSalary: Boolean get() = months.any { it.estimatedSalaryCents > 0 }
    val estimatedSalaryCents: Long? get() = months.firstOrNull { it.estimatedSalaryCents > 0 }?.estimatedSalaryCents

    companion object {
        /** Até o prazo da meta mais distante, e no mínimo 12 meses (o resumo da IA fala do próximo ano). */
        fun horizonFor(objetivos: List<com.finai.app.data.local.entity.ObjetivoEntity>, today: LocalDate): YearMonth {
            val minimum = YearMonth.from(today).plusMonths(11)
            val latestGoal = objetivos.maxOfOrNull { YearMonth.from(it.prazo.toLocalDate()) }
            return if (latestGoal != null && latestGoal > minimum) latestGoal else minimum
        }

        fun of(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            until: YearMonth,
            today: LocalDate,
        ): SavingsProjection {
            val start = YearMonth.from(today)
            val salaries = PayCycle.salariesOf(transacoes)
            val lastAvulso = salaries.takeIf { list -> list.none { it.recorrente } }?.maxByOrNull { it.data }
            val lastSalaryMonth = lastAvulso?.let { YearMonth.from(it.data.toLocalDate()) }

            val months = generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= until }.map { month ->
                val flow = MonthCashFlow.of(contas, transacoes, dividas, month, today)
                val hasSalary = (flow.recorrentes + flow.avulsas).any(PayCycle::isSalary)
                val estimated = if (lastAvulso != null && lastSalaryMonth != null && month > lastSalaryMonth && !hasSalary) {
                    lastAvulso.valorCentavos
                } else {
                    0L
                }
                MonthProjection(month, flow.saldoCents + estimated, estimated)
            }.toList()
            return SavingsProjection(months)
        }
    }
}
