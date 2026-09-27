package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SavingsProjectionTest {

    private val today = LocalDate.of(2026, 9, 27)

    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun tx(desc: String, cents: Long, tipo: TransactionType, date: LocalDate, recorrente: Boolean = false) =
        TransacaoEntity(
            data = millis(date), valorCentavos = cents, descricao = desc, categoria = "Outros",
            contaOrigem = "Carteira", origem = "manual", recorrente = recorrente, tipo = tipo.name,
        )

    // Salário lançado avulso em set e out; contas fixas recorrentes.
    private val transacoes = listOf(
        tx("Salario", 255_000, TransactionType.Receita, LocalDate.of(2026, 9, 30)),
        tx("Salario", 410_000, TransactionType.Receita, LocalDate.of(2026, 10, 31)),
        tx("Contas de casa", 240_000, TransactionType.Gasto, LocalDate.of(2026, 9, 30), recorrente = true),
    )

    @Test
    fun `meses sem salario lancado repetem o ultimo como estimativa`() {
        val p = SavingsProjection.of(emptyList(), transacoes, emptyList(), YearMonth.of(2027, 1), today)
        assertEquals(listOf(15_000L, 170_000L, 170_000L, 170_000L, 170_000L), p.months.map { it.balanceCents })
        assertEquals(0L, p.months[1].estimatedSalaryCents) // outubro tem salário lançado
        assertEquals(410_000L, p.estimatedSalaryCents)
        assertEquals(15_000L + 170_000L * 4, p.cumulativeThrough(YearMonth.of(2027, 1)))
    }

    @Test
    fun `parcela de divida so pesa enquanto existe`() {
        val divida = DividaEntity(
            nome = "Empréstimo", valorOriginalCentavos = 300_000, valorAbertoCentavos = 200_000,
            taxaJurosMensalBasisPoints = 0, parcelasRestantes = 2, valorParcelaCentavos = 100_000,
            parcelasTotais = 3, proximoVencimento = millis(LocalDate.of(2026, 10, 10)),
        )
        val p = SavingsProjection.of(emptyList(), transacoes, listOf(divida), YearMonth.of(2026, 12), today)
        assertEquals(listOf(15_000L, 70_000L, 70_000L, 170_000L), p.months.map { it.balanceCents })
    }

    @Test
    fun `meta que nao cabe no mes cabe ate o prazo`() {
        val goal = ObjetivoEntity(
            tipo = "Viagem", nome = "Italia 2028", valorAlvoCentavos = 2_000_000, valorGuardadoCentavos = 0,
            prazo = millis(LocalDate.of(2028, 4, 1)), prioridade = 1,
        )
        val p = SavingsProjection.of(emptyList(), transacoes, emptyList(), SavingsProjection.horizonFor(listOf(goal), today), today)
        val plan = GoalCalculator.plan(listOf(goal), monthlyCapacityCents = 15_000, today = today, projection = p).single()
        assertEquals(GoalStatus.OnTrack, plan.status)
        assertTrue(plan.projectedAvailableCents!! >= 2_000_000)
    }
}
