package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity

/**
 * "Capacidade de poupança mensal" — planning.md §3.3/§6: renda recorrente
 * menos gastos fixos recorrentes e parcelas de dívida. Independent of any
 * single month's dated bills (that's [SafeToSpendCalculator]'s job); this is
 * the steady-state monthly pattern used to judge whether a goal's required
 * contribution is realistic.
 */
object SavingsCapacityCalculator {
    fun monthlyCapacityCents(contas: List<ContaEntity>, dividas: List<DividaEntity>): Long {
        val rendaRecorrente = contas.filter { it.tipo == "a_receber" && it.recorrente }.sumOf { it.valorCentavos }
        val gastosFixosRecorrentes = contas.filter { it.tipo == "a_pagar" && it.recorrente }.sumOf { it.valorCentavos }
        val parcelasDividas = dividas.sumOf { it.valorParcelaCentavos }
        return rendaRecorrente - gastosFixosRecorrentes - parcelasDividas
    }
}
