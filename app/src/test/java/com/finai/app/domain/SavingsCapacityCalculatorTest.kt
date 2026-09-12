package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SavingsCapacityCalculatorTest {

    @Test
    fun `capacity is recurring income minus recurring fixed costs and debt installments`() {
        val contas = listOf(
            ContaEntity(nome = "Salário", valorCentavos = 800_000, vencimento = 0, status = "previsto", tipo = "a_receber", recorrente = true),
            ContaEntity(nome = "Bônus único", valorCentavos = 300_000, vencimento = 0, status = "previsto", tipo = "a_receber", recorrente = false), // ignored: not recurring
            ContaEntity(nome = "Aluguel", valorCentavos = 200_000, vencimento = 0, status = "pendente", tipo = "a_pagar", recorrente = true),
            ContaEntity(nome = "Compra avulsa", valorCentavos = 999_999, vencimento = 0, status = "pendente", tipo = "a_pagar", recorrente = false), // ignored
        )
        val dividas = listOf(
            DividaEntity(nome = "Consignado", valorOriginalCentavos = 0, valorAbertoCentavos = 500_000, taxaJurosMensalBasisPoints = 100, parcelasRestantes = 10, valorParcelaCentavos = 50_000),
        )
        val capacity = SavingsCapacityCalculator.monthlyCapacityCents(contas, dividas)
        assertEquals(800_000L - 200_000L - 50_000L, capacity)
    }
}
