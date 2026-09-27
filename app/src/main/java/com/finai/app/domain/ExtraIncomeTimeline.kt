package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate

data class ExtraIncome(val date: LocalDate, val cents: Long, val label: String)

/**
 * "Linha do tempo do ano" da Início: entradas fora da renda normal. Junta as duas
 * origens possíveis — conta a receber avulsa (cadastro antigo/assistente inicial) e
 * receita marcada como [TransacaoEntity.extra] no lançamento, que é como uma entrada
 * extra é registrada hoje. Receita comum (salário lançado avulso) não entra.
 */
object ExtraIncomeTimeline {
    fun forYear(contas: List<ContaEntity>, transacoes: List<TransacaoEntity>, year: Int): List<ExtraIncome> {
        val fromContas = contas
            .filter { it.tipo == "a_receber" && !it.recorrente }
            .map { ExtraIncome(it.vencimento.toLocalDate(), it.valorCentavos, it.nome) }
        val fromTransacoes = transacoes
            .filter { it.tipo == TransactionType.Receita.name && it.extra && !it.recorrente && it.faturaId == null }
            .map { ExtraIncome(it.data.toLocalDate(), it.valorCentavos, it.descricao.ifBlank { it.categoria }) }
        return (fromContas + fromTransacoes).filter { it.date.year == year }.sortedBy { it.date }
    }
}
