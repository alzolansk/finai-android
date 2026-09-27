package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * O que entra e o que sai num mês — a mesma conta para os totais da Agenda
 * ("Contas a pagar"/"Recebimentos") e para o "Saldo atual" da Início. Antes eram
 * duas contas diferentes: o saldo não descontava parcela de dívida e contava os itens
 * da fatura pela data da compra, enquanto a Agenda cobrava a fatura no vencimento.
 *
 * - Sai: contas a pagar que vencem no mês (a fatura do cartão entra aqui, pelo
 *   vencimento), gastos lançados no mês (recorrentes pela ocorrência do mês) e as
 *   parcelas de dívida ainda em aberto que vencem no mês.
 * - Entra: contas a receber do mês e receitas lançadas no mês.
 * - Itens de fatura (`faturaId`) ficam de fora — já estão dentro do valor da fatura.
 *   Transferência é neutra.
 *
 * Parcela paga some da projeção ([DebtSchedule.afterPayment]) e passa a contar pelo
 * gasto que o pagamento grava, então pagar não mexe no saldo — só confirma a saída.
 */
data class MonthCashFlow(
    val payable: List<ContaEntity>,
    val receivable: List<ContaEntity>,
    val recorrentes: List<TransacaoEntity>,
    val avulsas: List<TransacaoEntity>,
    val debtsDue: List<DebtInstallment>,
) {
    private val lancamentos get() = recorrentes + avulsas
    private val gastos get() = lancamentos.filter { it.tipo == TransactionType.Gasto.name }
    private val receitas get() = lancamentos.filter { it.tipo == TransactionType.Receita.name }

    val toPayCents: Long get() = payable.sumOf { it.valorCentavos } + gastos.sumOf { it.valorCentavos } + debtsDue.sumOf { it.valorCentavos }
    val toPayCount: Int get() = payable.size + gastos.size + debtsDue.size
    val toGetCents: Long get() = receivable.sumOf { it.valorCentavos } + receitas.sumOf { it.valorCentavos }
    val toGetCount: Int get() = receivable.size + receitas.size
    val saldoCents: Long get() = toGetCents - toPayCents

    companion object {
        fun of(
            contas: List<ContaEntity>,
            transacoes: List<TransacaoEntity>,
            dividas: List<DividaEntity>,
            month: YearMonth,
            today: LocalDate,
        ): MonthCashFlow {
            val contasDoMes = contas.filter { YearMonth.from(it.vencimento.toLocalDate()) == month }.sortedBy { it.vencimento }
            val (recorrentes, avulsas) = transacoes
                .filter { it.faturaId == null }
                .transactionsInMonth(month.atDay(1))
                .sortedByDescending { it.data }
                .partition { it.recorrente }
            return MonthCashFlow(
                payable = contasDoMes.filter { it.tipo == "a_pagar" },
                receivable = contasDoMes.filter { it.tipo == "a_receber" },
                recorrentes = recorrentes,
                avulsas = avulsas,
                debtsDue = DebtSchedule.installmentsInMonth(dividas, month, today),
            )
        }
    }
}
