package com.finai.app.domain.scenario

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.FinancialPlan
import com.finai.app.domain.TransactionType
import com.finai.app.domain.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A compra hipotética entra no fluxo central só em memória e o impacto sai por ciclo. Base:
 * salário de R$ 3.000 todo dia 30 e aluguel de R$ 2.000 todo dia 12 — R$ 1.000 livres por ciclo.
 */
class ScenarioSimulatorTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun tx(date: LocalDate, cents: Long, tipo: TransactionType, recorrente: Boolean = false, desc: String = "Teste", principal: Boolean = false) =
        TransacaoEntity(
            data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = "Outros",
            contaOrigem = "Carteira", recorrente = recorrente, origem = "manual", tipo = tipo.name, rendaPrincipal = principal)

    private val salario = tx(LocalDate.of(2026, 7, 30), 300_000, TransactionType.Receita, recorrente = true, desc = "Salário")
    private val aluguel = tx(LocalDate.of(2026, 7, 12), 200_000, TransactionType.Gasto, recorrente = true, desc = "Aluguel")

    private fun snapshot(
        contas: List<ContaEntity> = emptyList(),
        transacoes: List<TransacaoEntity> = listOf(salario, aluguel),
        dividas: List<DividaEntity> = emptyList(),
        faturas: List<FaturaCartaoEntity> = emptyList(),
    ) = FinanceSnapshot(contas, transacoes, dividas, emptyList(), faturas)

    private fun intent(text: String) = PurchaseIntentParser.parse(text, today)!!

    @Test fun fourInstallmentsReduceEachCycleOnlyByTheirInstallment() {
        val r = ScenarioSimulator.simulate(snapshot(), intent("posso comprar uma cadeira de R$ 1.000 em 4x no cartão, primeira dia 10?"), today)!!
        assertTrue(r.byCycle)
        val hit = r.asked.affected
        assertEquals(4, hit.size)
        hit.forEach {
            assertEquals(100_000L, it.livreBeforeCents)
            assertEquals(75_000L, it.livreAfterCents)
            assertEquals(25_000L, it.reductionCents)
        }
        assertEquals(LocalDate.of(2026, 10, 30), hit[1].start)
        assertEquals(LocalDate.of(2027, 1, 10), hit[3].payments.single().date)
        assertTrue(r.asked.billsCovered)
        assertEquals(Verdict.Fits, r.asked.verdict)
        // Pagar à vista hoje zera o livre do ciclo: cabe, mas apertado. Parcelar é o melhor pelos números.
        val cash = r.alternatives.first { it.kind == OptionKind.CashNow }
        assertEquals(0L, cash.periods.first().livreAfterCents)
        assertEquals(Verdict.Tight, cash.verdict)
        assertEquals(OptionKind.Asked, r.best.kind)
        assertTrue(r.factsBlock().contains("nada foi lançado"))
    }

    @Test fun purchaseThatLeavesABillUncoveredDoesNotFit() {
        // Este ciclo ainda tem uma conta de R$ 500 no dia 20: sobram R$ 500, não R$ 1.000.
        val iptu = ContaEntity(nome = "IPTU", valorCentavos = 50_000, vencimento = LocalDate.of(2026, 10, 20).toEpochMillis(),
            status = "pendente", tipo = "a_pagar", recorrente = false)
        val r = ScenarioSimulator.simulate(snapshot(contas = listOf(iptu)), intent("posso gastar R$ 800 no pix hoje?"), today)!!
        val cur = r.asked.periods.first()
        assertEquals(50_000L, cur.livreBeforeCents)
        assertEquals(-30_000L, cur.livreAfterCents)
        assertEquals(LocalDate.of(2026, 10, 20), cur.worstDate)
        assertFalse(r.asked.billsCovered)
        assertEquals(Verdict.DoesNotFit, r.asked.verdict)
        // Esperar o salário de 30/10 resolve, mas o ciclo seguinte fica apertado (R$ 200 < 10% de R$ 3.000).
        val wait = r.alternatives.single { it.kind == OptionKind.WaitPayday }
        assertEquals(LocalDate.of(2026, 10, 30), wait.scenario.first)
        assertEquals(20_000L, wait.affected.single().livreAfterCents)
        assertEquals(Verdict.Tight, wait.verdict)
        assertEquals(OptionKind.WaitPayday, r.best.kind)
        assertTrue(r.reply(), r.reply().contains("falta R$ 300 em 20/10"))
    }

    @Test fun installmentThatLandsOnAnInvoiceIsReportedAndBreaksTheCycle() {
        val fatura = ContaEntity(id = 40, nome = "Fatura Nubank", valorCentavos = 90_000, vencimento = LocalDate.of(2026, 10, 10).toEpochMillis(),
            status = "pendente", tipo = "a_pagar", recorrente = false)
        val s = snapshot(contas = listOf(fatura), faturas = listOf(FaturaCartaoEntity(id = 1, contaId = 40, referencia = "Nubank", fechamento = null, vencimento = fatura.vencimento)))
        val r = ScenarioSimulator.simulate(s, intent("posso comprar uma cadeira de R$ 1.000 em 4x no cartão, primeira dia 10?"), today)!!
        val cur = r.asked.periods.first()
        assertEquals(10_000L, cur.livreBeforeCents)
        assertEquals(-15_000L, cur.livreAfterCents)
        assertTrue(cur.collisions.single(), cur.collisions.single().contains("Fatura Nubank de 10/10"))
        assertEquals(Verdict.DoesNotFit, r.asked.verdict)
    }

    @Test fun existingDebtInstallmentsShowUpInEachCycle() {
        val divida = DividaEntity(id = 3, nome = "Empréstimo", valorOriginalCentavos = 300_000, valorAbertoCentavos = 180_000,
            taxaJurosMensalBasisPoints = 200, parcelasRestantes = 6, valorParcelaCentavos = 30_000, parcelasTotais = 10,
            proximoVencimento = LocalDate.of(2026, 10, 20).toEpochMillis(), diaVencimento = 20)
        val r = ScenarioSimulator.simulate(snapshot(dividas = listOf(divida)), intent("comprar cadeira R$ 1.000 em 2x no cartão dia 10"), today)!!
        val (a, b) = r.asked.affected
        assertEquals(30_000L, a.debtInstallmentsCents)
        assertEquals(30_000L, b.debtInstallmentsCents)
        assertEquals(70_000L, b.livreBeforeCents)
        assertEquals(20_000L, b.livreAfterCents)
    }

    @Test fun avulsoSalaryIsEstimatedInFutureCycles() {
        val s = snapshot(transacoes = listOf(tx(LocalDate.of(2026, 9, 30), 300_000, TransactionType.Receita, desc = "Pagamento", principal = true), aluguel))
        val r = ScenarioSimulator.simulate(s, intent("comprar cadeira R$ 1.000 em 2x no cartão dia 10"), today)!!
        val next = r.asked.affected[1]
        assertTrue(next.estimatedIncome)
        assertEquals(100_000L, next.livreBeforeCents)
        assertEquals(50_000L, next.livreAfterCents)
        assertTrue(r.notes.any { it.contains("estimativa") })
    }

    @Test fun recommendationShrinksButTheFreeValueIsTheCentralOne() {
        val rotativo = DividaEntity(id = 7, nome = "Rotativo", valorOriginalCentavos = 400_000, valorAbertoCentavos = 400_000,
            taxaJurosMensalBasisPoints = 1_200, parcelasRestantes = 0, valorParcelaCentavos = 0)
        val s = snapshot(dividas = listOf(rotativo))
        val plan = FinancialPlan.build(s.contas, s.transacoes, s.dividas, s.objetivos, today)
        val r = ScenarioSimulator.simulate(s, intent("posso gastar R$ 400 no pix hoje?"), today)!!
        assertEquals(plan.livreCents, r.asked.periods.first().livreBeforeCents)
        assertTrue(r.factsBlock(), r.factsBlock().contains("\"Rotativo\" de R$ 1.000 para R$ 600"))
    }

    @Test fun scenarioIsDisposableAndRecordsAreSeparate() {
        val s = snapshot()
        val before = s.copy()
        val r = ScenarioSimulator.simulate(s, intent("posso comprar uma cadeira de R$ 1.000 em 4x no cartão, primeira dia 10?"), today)!!
        assertEquals(before, s)
        val sim = r.asked.scenario.asTransactions()
        assertTrue(sim.all { it.id < 0 && it.origem == PurchaseScenario.SIMULATION_ORIGIN })
        val records = r.asked.scenario.toRecords()
        assertEquals(listOf(0L, 0L, 0L, 0L), records.map { it.id })
        assertEquals("Cadeira · parcela 2 de 4", records[1].descricao)
        assertEquals(100_000L, records.sumOf { it.valorCentavos })
    }

    @Test fun assistantAsksThenSimulatesAfterTheAnswer() {
        val s = snapshot()
        val first = PurchaseAssistant.handle("Posso comprar uma cadeira de R$ 1.000 em 4x?", null, s, today)
        assertTrue(first is PurchaseAssistant.Step.Ask)
        first as PurchaseAssistant.Step.Ask
        assertTrue(first.question, first.question.contains("primeira parcela"))
        val second = PurchaseAssistant.handle("no cartão, dia 10", first.intent, s, today)
        assertTrue(second is PurchaseAssistant.Step.Simulated)
        assertEquals(4, (second as PurchaseAssistant.Step.Simulated).result.asked.scenario.count)
        assertEquals(PurchaseAssistant.Step.NotPurchase, PurchaseAssistant.handle("obrigado!", null, s, today))
    }

    @Test fun guardRejectsNumbersTheSimulationDidNotProduce() {
        val r = ScenarioSimulator.simulate(snapshot(), intent("posso comprar uma cadeira de R$ 1.000 em 4x no cartão, primeira dia 10?"), today)!!
        val facts = r.factsBlock()
        assertTrue(ScenarioReplyGuard.accepts("{{Livre até o salário|R$ 750}} Cabe: as contas continuam cobertas e a 1ª parcela sai em 10/10.", facts))
        assertFalse(ScenarioReplyGuard.accepts("Cabe, e ainda sobram R$ 820 por mês.", facts))
        assertFalse(ScenarioReplyGuard.accepts("A última parcela sai em 15/02.", facts))
        assertTrue(ScenarioReplyGuard.accepts(r.reply(), facts))
    }

    @Test fun withoutMainIncomeThePeriodsAreMonths() {
        // Só uma conta de R$ 300 no dia 20 e uma receita avulsa no dia 1: sem renda principal.
        val s = snapshot(
            contas = listOf(ContaEntity(nome = "Luz", valorCentavos = 30_000, vencimento = LocalDate.of(2026, 10, 20).toEpochMillis(),
                status = "pendente", tipo = "a_pagar", recorrente = false)),
            transacoes = listOf(tx(LocalDate.of(2026, 10, 1), 100_000, TransactionType.Receita, desc = "Freela")),
        )
        val r = ScenarioSimulator.simulate(s, intent("comprar fone R$ 400 em 2x no boleto dia 15"), today)!!
        assertFalse(r.byCycle)
        assertTrue(r.notes.any { it.contains("por mês") })
        val (oct, nov) = r.asked.affected
        assertEquals(LocalDate.of(2026, 10, 31), oct.end)
        assertEquals(20_000L, oct.purchaseCents)
        assertEquals(LocalDate.of(2026, 11, 1), nov.start)
        assertEquals(-20_000L, nov.livreAfterCents)
        assertEquals(Verdict.DoesNotFit, r.asked.verdict)
    }
}
