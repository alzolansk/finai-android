package com.finai.app.domain.balance

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.ADJUSTMENT_LABEL
import com.finai.app.domain.ADJUSTMENT_ORIGIN
import com.finai.app.domain.ADJUSTMENT_TYPE
import com.finai.app.domain.IncomePart
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Fase 1 (planning-v2): saldo contínuo, próxima entrada e livre com margem. */
class FreeToSpendTest {
    private val today = LocalDate.of(2026, 11, 8)
    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    private var nextId = 1L
    private fun tx(
        date: LocalDate, cents: Long, tipo: String = "Gasto", desc: String = "Teste", recorrente: Boolean = false,
        principal: Boolean = false, objetivoId: Long? = null, parte: String? = null, extra: Boolean = false,
    ) = TransacaoEntity(
        id = nextId++, data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = "Outros",
        contaOrigem = "Nubank", recorrente = recorrente, tipo = tipo, origem = "manual", rendaPrincipal = principal,
        objetivoId = objetivoId, rendaParte = parte, rendaGrupo = parte?.let { "g" }, extra = extra,
    )

    private fun adjust(date: LocalDate, cents: Long) = tx(date, cents, ADJUSTMENT_TYPE, ADJUSTMENT_LABEL).copy(origem = ADJUSTMENT_ORIGIN)
    private fun conta(nome: String, cents: Long, vence: LocalDate, pago: Boolean = false) = ContaEntity(
        id = nextId++, nome = nome, valorCentavos = cents, vencimento = vence.toEpochMillis(),
        status = if (pago) "pago" else "pendente", tipo = "a_pagar", recorrente = false,
    )

    /** Renda em duas partes: adiantamento dia 20 (principal) e saldo dia 5, as duas recorrentes. */
    private val adiantamento = tx(d(11, 20), 240_000, "Receita", "Adiantamento", recorrente = true, principal = true, parte = "adiantamento")
    private val saldo = tx(d(12, 5), 360_000, "Receita", "Saldo", recorrente = true, parte = "saldo")

    private fun free(
        contas: List<ContaEntity> = emptyList(), t: List<TransacaoEntity>, day: LocalDate = today,
        dividas: List<DividaEntity> = emptyList(), margin: Long = 30_000,
    ) = FreeToSpend.of(contas, t, dividas, day, margin)

    private val base = listOf(adjust(today, 348_000), adiantamento, saldo)
    private val contas = listOf(
        conta("Fatura", 132_000, d(11, 10)), conta("Academia", 18_000, d(11, 12)),
        conta("Condomínio", 34_000, d(11, 15)), conta("Internet", 12_000, d(11, 18)),
    )

    @Test fun freeIsTheLowestBalanceMinusTheMargin() {
        val f = free(contas, base)!!
        assertEquals(348_000L, f.accountCents)
        assertEquals(d(11, 20), f.nextIncome.date)
        assertEquals(d(11, 19), f.windowEnd)
        assertEquals(12, f.days)
        assertEquals(152_000L, f.lowest.cents)
        assertEquals(d(11, 18), f.lowest.date)
        assertEquals(122_000L, f.freeCents)
        assertEquals(10_166L, f.perDayCents) // R$ 101,66: centavos inteiros, para baixo
        assertEquals(196_000L, f.goingOutCents)
        assertNull(f.shortfall)
        assertFalse(f.insideMargin)
        // Série diária de hoje até a véspera: 12 dias.
        assertEquals(12, f.series.size)
        assertEquals(348_000L, f.series.first().cents)
        assertEquals(152_000L, f.series.last().cents)
    }

    @Test fun marginZeroLeavesTheWholeLowestBalanceFree() {
        assertEquals(152_000L, free(contas, base, margin = 0)!!.freeCents)
        assertEquals(52_000L, free(contas, base, margin = 100_000)!!.freeCents)
    }

    @Test fun balanceInsideTheMarginIsNotAShortfall() {
        val f = free(listOf(conta("Aluguel", 330_000, d(11, 10))), base)!!
        assertEquals(18_000L, f.lowest.cents)
        assertNull(f.shortfall)
        assertTrue(f.insideMargin)
        assertEquals(0L, f.freeCents)
    }

    @Test fun predictedShortfallReplacesTheFree() {
        val f = free(listOf(conta("Aluguel", 400_000, d(11, 10))), base)!!
        assertNotNull(f.shortfall)
        assertEquals(52_000L, f.shortfall!!.cents)
        assertEquals(d(11, 10), f.shortfall!!.date)
        assertEquals("Aluguel", f.shortfall!!.causa)
        assertEquals(0L, f.freeCents)
        assertEquals(0L, f.perDayCents)
    }

    @Test fun lastMonthsLeftoverShowsUpInNovemberAndNothingZeroesOnPayday() {
        val t = listOf(
            tx(d(10, 5), 500_000, "Receita", "Salário out"), tx(d(10, 20), 100_000, "Gasto", "Mercado"), adiantamento,
        )
        // Sobra de outubro (R$ 4.000) está em conta em novembro.
        assertEquals(400_000L, free(emptyList(), t)!!.accountCents)
        // No dia do adiantamento, o saldo não recomeça: a renda soma ao que já havia.
        val payday = free(emptyList(), t, day = d(11, 20))!!
        assertEquals(400_000L + 240_000L, payday.accountCents)
    }

    @Test fun reconciliationAdjustmentReanchorsTheBalance() {
        val t = listOf(tx(d(11, 1), 500_000, "Receita", "Salário"), tx(d(11, 3), 100_000, "Gasto", "Mercado"), adiantamento)
        val before = free(emptyList(), t)!!.accountCents
        assertEquals(400_000L, before)
        // O banco tinha R$ 3.800: ajuste −R$ 200, e o saldo passa a ser o do banco.
        val after = free(emptyList(), t + adjust(today, -20_000))!!.accountCents
        assertEquals(380_000L, after)
        // Sem o ajuste (excluído), volta ao saldo de antes.
        assertEquals(before, free(emptyList(), t)!!.accountCents)
    }

    @Test fun splitIncomeSwitchesTheWindowOnTheAdvanceDay() {
        val t = listOf(adjust(d(11, 1), 300_000), adiantamento.copy(data = d(10, 20).toEpochMillis()), saldo)
        val before = free(emptyList(), t, day = d(11, 19), margin = 0)!!
        assertEquals(d(11, 20), before.nextIncome.date)
        assertEquals(IncomePart.Advance, before.nextIncome.part)
        assertEquals("adiantamento", before.nextIncome.noun)
        assertEquals(1, before.days)
        // No dia 20 o adiantamento já caiu: a janela vai até a véspera do saldo (05/12).
        val after = free(emptyList(), t, day = d(11, 20), margin = 0)!!
        assertEquals(d(12, 5), after.nextIncome.date)
        assertEquals(IncomePart.Balance, after.nextIncome.part)
        assertEquals(300_000L + 240_000L + 240_000L, after.accountCents) // ajuste + adiantamento de 20/10 e de 20/11
        assertEquals(d(12, 4), after.windowEnd)
    }

    @Test fun goalContributionLowersTheFreeOnlyOnce() {
        val without = free(contas, base)!!
        val aporte = tx(d(11, 8), 50_000, "Transferencia", "Aporte", objetivoId = 1)
        val withAporte = free(contas, base + aporte)!!
        assertEquals(without.accountCents - 50_000, withAporte.accountCents)
        assertEquals(without.freeCents - 50_000, withAporte.freeCents)
        // Transferência entre contas (sem objetivo) é neutra.
        val between = free(contas, base + tx(d(11, 8), 50_000, "Transferencia", "Entre contas"))!!
        assertEquals(without.freeCents, between.freeCents)
    }

    @Test fun overdueBillLeavesTodayAndOverdueIncomeDoesNot() {
        val vencida = conta("Luz", 20_000, d(10, 25))
        val f = free(listOf(vencida), base)!!
        assertEquals(348_000L - 20_000, f.series.first().cents)
        assertEquals(348_000L, f.accountCents) // o saldo em conta ainda não saiu
        assertEquals(d(10, 25), f.movements.single().movedFrom)
    }

    @Test fun billPaidAheadIsAlreadyInTheBalance() {
        val paga = conta("Fatura", 132_000, d(11, 10), pago = true)
        val f = free(listOf(paga), base)!!
        // O pagamento já saiu do saldo (está efetivado) e não sai de novo no dia 10.
        assertEquals(348_000L - 132_000, f.accountCents)
        assertTrue(f.movements.isEmpty())
        assertEquals(348_000L - 132_000, f.lowest.cents)
    }

    @Test fun noIncomeAtAllMeansNoWindow() {
        assertNull(free(contas, listOf(adjust(today, 100_000))))
    }

    @Test fun nextIncomeFallsBackToLastSalaryPlusOneMonthAsEstimate() {
        val salario = tx(d(10, 30), 500_000, "Receita", "Salário", principal = true)
        val next = NextIncomeFinder.find(emptyList(), listOf(salario), emptyList(), today)!!
        assertEquals(d(11, 30), next.date)
        assertTrue(next.estimated)
    }

    @Test fun extraIncomeDoesNotCloseThePeriodButEntersTheForecast() {
        val t = base + tx(d(11, 14), 90_000, "Receita", "Rescisão", extra = true)
        val f = free(contas, t)!!
        assertEquals(d(11, 20), f.nextIncome.date)
        assertEquals(90_000L, f.inflowsCents)
        // 14/11: −fatura −academia + rescisão; 15/11: −condomínio.
        assertEquals(348_000L - 132_000 - 18_000 + 90_000, f.series.first { it.date == d(11, 14) }.cents)
    }

    @Test fun aiBlockStatesAccountFreeAndMargin() {
        val text = free(contas, base)!!.aiBlock()
        assertTrue(text.contains("Em conta hoje"))
        assertTrue(text.contains("Menor saldo previsto"))
        assertTrue(text.contains("Margem de segurança"))
        assertTrue(text.contains("não zera no salário"))
    }
}
