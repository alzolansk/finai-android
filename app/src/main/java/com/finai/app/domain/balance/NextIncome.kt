package com.finai.app.domain.balance

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.IncomePart
import com.finai.app.domain.PayCycle
import com.finai.app.domain.TransactionType
import com.finai.app.domain.incomePart
import com.finai.app.domain.isRecurringSeries
import com.finai.app.domain.occurrenceFor
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import java.time.YearMonth

/** A próxima renda que fecha o período do "livre": o dinheiro precisa durar até a véspera dela. */
data class NextIncome(
    val date: LocalDate,
    val cents: Long,
    val description: String,
    /** Parte do salário dividido (adiantamento ou saldo); nulo em salário integral ou outra renda. */
    val part: IncomePart?,
    /** Nenhuma renda lançada depois de hoje: a data é a do último salário mais um mês. */
    val estimated: Boolean,
) {
    /** "salário" ou "adiantamento": a palavra do rótulo "LIVRE ATÉ O …". */
    val noun: String get() = if (part == IncomePart.Advance) "adiantamento" else "salário"
}

/**
 * Regra 3 do planning-v2: a primeira renda **recorrente** depois de hoje — o adiantamento ou o saldo
 * de um salário dividido ([com.finai.app.domain.IncomeSplit]), ou o salário integral. Um salário
 * avulso (renda principal) lançado com data futura também vale. Receita extra (13º, bônus) e
 * entrada avulsa comum não fecham o período: entram no saldo, mas não dizem quando a renda
 * "normal" volta.
 *
 * Sem nenhuma dessas, cai para a estimativa do [PayCycle] (último salário + 1 mês). Sem salário
 * algum, nulo: o app não sabe quando o dinheiro entra e o livre vai até o fim do mês.
 */
object NextIncomeFinder {
    fun find(
        contas: List<ContaEntity>,
        transacoes: List<TransacaoEntity>,
        dividas: List<DividaEntity>,
        today: LocalDate,
    ): NextIncome? {
        val current = YearMonth.from(today)
        val candidates = transacoes
            .filter { it.tipo == TransactionType.Receita.name && !it.extra && it.faturaId == null && it.valorCentavos > 0 }
            .flatMap { t ->
                when {
                    t.isRecurringSeries -> (0L..2L).mapNotNull { t.occurrenceFor(current.plusMonths(it)) }
                    t.rendaPrincipal || PayCycle.isSalary(t) -> listOf(t)
                    else -> emptyList()
                }
            }
            .filter { it.data.toLocalDate().isAfter(today) }
        val next = candidates.minWithOrNull(compareBy<TransacaoEntity> { it.data }.thenByDescending { it.valorCentavos })
        if (next != null) {
            return NextIncome(
                date = next.data.toLocalDate(), cents = next.valorCentavos,
                description = next.descricao.ifBlank { next.categoria }, part = next.incomePart, estimated = false,
            )
        }
        val cycle = runCatching { PayCycle.of(contas, transacoes, dividas, today) }.getOrNull() ?: return null
        return NextIncome(cycle.proximo, cycle.salarioCents, cycle.salarioNome, part = null, estimated = cycle.proximoEstimado)
    }
}
