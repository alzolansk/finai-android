package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * "Capacidade de poupança mensal" — planning.md §3.3/§6: quanto sobra no mês
 * para as metas. É o balanço do mês da Agenda ([MonthCashFlow]: recebimentos −
 * contas a pagar, parcelas incluídas) sem as entradas extras (13º, bônus,
 * rescisão), que não se repetem e não podem sustentar um aporte mensal.
 *
 * Antes lia só `ContaEntity` recorrente, que não tem mais cadastro na UI: o
 * salário e as contas fixas lançados pelo "+" (transações recorrentes) ficavam
 * de fora, sobravam só as parcelas de dívida, e a capacidade saía negativa em
 * milhares — sem bater com nenhum número que o usuário via. A IA recebia esse
 * valor e falava em rombo onde a Agenda mostrava sobra.
 */
object SavingsCapacityCalculator {
    fun monthlyCapacityCents(
        contas: List<ContaEntity>,
        transacoes: List<TransacaoEntity>,
        dividas: List<DividaEntity>,
        today: LocalDate = LocalDate.now(),
    ): Long {
        val month = YearMonth.from(today)
        val flow = MonthCashFlow.of(contas, transacoes, dividas, month, today)
        val extras = (flow.recorrentes + flow.avulsas)
            .filter { it.tipo == TransactionType.Receita.name && it.extra }
            .sumOf { it.valorCentavos }
        return flow.saldoCents - extras
    }
}
