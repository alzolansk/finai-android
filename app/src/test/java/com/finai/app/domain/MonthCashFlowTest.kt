package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthCashFlowTest {
    private val today = LocalDate.of(2026, 9, 26)
    private val setembro = YearMonth.of(2026, 9)

    private fun tx(date: LocalDate, cents: Long, tipo: TransactionType, recorrente: Boolean = false, faturaId: Long? = null) = TransacaoEntity(
        data = date.toEpochMillis(), descricao = "Teste", valorCentavos = cents, categoria = "Outros",
        contaOrigem = "Carteira", recorrente = recorrente, origem = "manual", tipo = tipo.name, faturaId = faturaId)

    private fun divida(nome: String, parcela: Long, restantes: Int, total: Int, vence: LocalDate?) = DividaEntity(
        nome = nome, valorOriginalCentavos = parcela * total, valorAbertoCentavos = parcela * restantes,
        taxaJurosMensalBasisPoints = 0, parcelasRestantes = restantes, valorParcelaCentavos = parcela,
        parcelasTotais = total, proximoVencimento = vence?.toEpochMillis())

    private fun flow(contas: List<ContaEntity> = emptyList(), transacoes: List<TransacaoEntity> = emptyList(), dividas: List<DividaEntity> = emptyList()) =
        MonthCashFlow.of(contas, transacoes, dividas, setembro, today)

    @Test fun onlyCurrentMonthCounts() {
        val f = flow(transacoes = listOf(
            tx(LocalDate.of(2026, 8, 10), 100_000, TransactionType.Receita),
            tx(LocalDate.of(2026, 8, 12), 30_000, TransactionType.Gasto),
            tx(LocalDate.of(2026, 9, 5), 50_000, TransactionType.Receita),
            tx(LocalDate.of(2026, 9, 6), 20_000, TransactionType.Gasto),
            tx(LocalDate.of(2026, 9, 7), 9_000, TransactionType.Transferencia),
            tx(LocalDate.of(2026, 10, 1), 7_000, TransactionType.Gasto),
        ))
        assertEquals(30_000L, f.saldoCents)
    }

    @Test fun recurringFromEarlierMonthCountsOnceThisMonth() {
        val f = flow(transacoes = listOf(
            tx(LocalDate.of(2026, 6, 5), 500_000, TransactionType.Receita, recorrente = true),
            tx(LocalDate.of(2026, 9, 10), 120_000, TransactionType.Gasto),
        ))
        assertEquals(380_000L, f.saldoCents)
    }

    /** O caso relatado: Agenda de setembro com 2 parcelas + 4 recorrentes e o salário. */
    @Test fun saldoIsReceivablesMinusPayablesOfTheAgenda() {
        val f = flow(
            transacoes = listOf(
                tx(LocalDate.of(2026, 9, 25), 100_000, TransactionType.Gasto, recorrente = true),
                tx(LocalDate.of(2026, 9, 25), 22_000, TransactionType.Gasto, recorrente = true),
                tx(LocalDate.of(2026, 9, 25), 10_000, TransactionType.Gasto, recorrente = true),
                tx(LocalDate.of(2026, 9, 25), 8_500, TransactionType.Gasto, recorrente = true),
                tx(LocalDate.of(2026, 9, 26), 255_000, TransactionType.Receita),
            ),
            dividas = listOf(
                divida("Nubank", 25_000, restantes = 1, total = 2, vence = LocalDate.of(2026, 9, 30)),
                divida("Picpay", 48_100, restantes = 4, total = 8, vence = null),
            ),
        )
        assertEquals(213_600L, f.toPayCents)
        assertEquals(6, f.toPayCount)
        assertEquals(255_000L, f.toGetCents)
        assertEquals(255_000L - 213_600L, f.saldoCents)
    }

    @Test fun invoiceCountsByItsDueDateNotByPurchaseItems() {
        val fatura = ContaEntity(nome = "Fatura", valorCentavos = 60_000, vencimento = LocalDate.of(2026, 9, 15).toEpochMillis(),
            status = "pendente", tipo = "a_pagar", recorrente = false)
        val f = flow(contas = listOf(fatura), transacoes = listOf(
            tx(LocalDate.of(2026, 9, 2), 40_000, TransactionType.Gasto, faturaId = 1),
            tx(LocalDate.of(2026, 9, 3), 20_000, TransactionType.Gasto, faturaId = 1),
        ))
        assertEquals(60_000L, f.toPayCents)
        assertEquals(-60_000L, f.saldoCents)
    }

    @Test fun payingAnInstallmentKeepsTheSaldo() {
        val nubank = divida("Nubank", 25_000, restantes = 1, total = 2, vence = LocalDate.of(2026, 9, 30))
        val antes = flow(dividas = listOf(nubank))
        // O que pagarParcela grava: o gasto do dia + a dívida avançada.
        val depois = flow(
            dividas = listOf(DebtSchedule.afterPayment(nubank, today)),
            transacoes = listOf(tx(today, 25_000, TransactionType.Gasto)),
        )
        assertEquals(antes.saldoCents, depois.saldoCents)
    }
}
