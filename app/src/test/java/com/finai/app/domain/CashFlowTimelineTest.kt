package com.finai.app.domain

import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.scenario.FinanceSnapshot
import com.finai.app.domain.scenario.PurchaseAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * A Agenda sequencial no caso que motivou a tela: salário em 30/10, fatura em 12/11, próximo
 * salário em 30/11. Hoje é 05/11.
 *
 * Ciclo atual, em ordem: 30/10 salário +5.000 → 5.000; 01/11 mercado −300 → 4.700; 05/11 parcela
 * do Picpay −481 → 4.219; 12/11 fatura −2.000 → 2.219; 20/11 aluguel −1.800 → 419; 25/11 freela
 * +800 → 1.219. Menor saldo até o salário: R$ 419 em 20/11. Sobra no fim: R$ 1.219.
 */
class CashFlowTimelineTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun d(day: Int, month: Int = 11) = LocalDate.of(2026, month, day)

    private fun tx(date: LocalDate, cents: Long, tipo: TransactionType, recorrente: Boolean = false, desc: String = "Teste", faturaId: Long? = null, origem: String = "manual") =
        TransacaoEntity(
            id = nextId++, data = date.toEpochMillis(), descricao = desc, valorCentavos = cents, categoria = "Outros",
            contaOrigem = "Carteira", recorrente = recorrente, origem = origem, tipo = tipo.name, faturaId = faturaId)

    private var nextId = 1L

    private fun conta(id: Long, nome: String, cents: Long, vence: LocalDate, status: String = "pendente", tipo: String = "a_pagar") = ContaEntity(
        id = id, nome = nome, valorCentavos = cents, vencimento = vence.toEpochMillis(), status = status, tipo = tipo, recorrente = false)

    private val salario = tx(d(30, 7), 500_000, TransactionType.Receita, recorrente = true, desc = "Salário")
    private val mercado = tx(d(1), 30_000, TransactionType.Gasto, desc = "Mercado")
    private val freela = tx(d(25), 80_000, TransactionType.Receita, desc = "Freela")
    private val faturaNov = conta(10, "Fatura Nubank", 200_000, d(12))
    private val aluguel = conta(11, "Aluguel", 180_000, d(20))
    private val faturaDez = conta(12, "Fatura Nubank", 150_000, d(12, 12))
    private val picpay = DividaEntity(
        id = 20, nome = "Picpay", valorOriginalCentavos = 48_100 * 8, valorAbertoCentavos = 48_100 * 4,
        taxaJurosMensalBasisPoints = 0, parcelasRestantes = 4, valorParcelaCentavos = 48_100,
        parcelasTotais = 8, proximoVencimento = d(5).toEpochMillis())

    private val transacoes = listOf(salario, mercado, freela)
    private val contas = listOf(faturaNov, aluguel, faturaDez)
    private val dividas = listOf(picpay)

    private fun timeline(
        c: List<ContaEntity> = contas, t: List<TransacaoEntity> = transacoes, dv: List<DividaEntity> = dividas,
    ) = CashFlowTimeline.build(c, t, dv, today)

    @Test fun balanceContinuesAcrossTheMonthAndOnlyRestartsOnPayday() {
        val seg = timeline().current!!
        assertEquals(d(30, 10), seg.start)
        assertEquals(d(30), seg.nextPayday)
        assertEquals(listOf(d(30, 10), d(1), d(5), d(12), d(20), d(25)), seg.movements.map { it.date })
        // 01/11 parte dos R$ 5.000 de outubro: a virada do mês não zera nada.
        assertEquals(listOf(500_000L, 470_000L, 421_900L, 221_900L, 41_900L, 121_900L), seg.movements.map { it.balanceAfterCents })
        assertTrue(seg.movements.first().mainIncome)
        assertEquals(121_900L, seg.sobraCents)
    }

    @Test fun lowestBalanceIsTheFreeUntilPaydayOfTheCentralPlan() {
        val tl = timeline()
        val plan = FinancialPlan.build(contas, transacoes, dividas, emptyList(), today)
        assertEquals(41_900L, tl.cycle!!.availableCents)
        assertEquals(plan.livreCents, tl.cycle!!.availableCents)
        assertEquals(CycleFloor(d(20), 41_900), tl.current!!.floor)
        val lowest = tl.current!!.movements.single { it.lowest }
        assertEquals("Aluguel", lowest.descricao)
        // A sobra no fim do ciclo não é o livre.
        assertEquals(121_900L, tl.cycle!!.livreCents)
    }

    @Test fun monthlyViewStillPutsTheInvoiceInNovember() {
        val nov = MonthCashFlow.of(contas, transacoes, dividas, YearMonth.of(2026, 11), today)
        val out = MonthCashFlow.of(contas, transacoes, dividas, YearMonth.of(2026, 10), today)
        assertTrue(nov.payable.any { it.id == faturaNov.id })
        assertFalse(out.payable.any { it.id == faturaNov.id })
        // Na sequencial, a mesma fatura sai do dinheiro do salário de outubro.
        assertTrue(timeline().current!!.movements.any { (it.source as? CycleSource.Conta)?.conta?.id == faturaNov.id })
    }

    @Test fun statusesAreSeenFromToday() {
        val byName = timeline().current!!.movements.associateBy { it.descricao }
        assertEquals(MovementStatus.Done, byName["Salário"]!!.status)
        assertEquals(MovementStatus.Done, byName["Mercado"]!!.status)
        assertEquals(MovementStatus.DueToday, byName["Picpay"]!!.status)
        assertEquals(MovementStatus.Expected, byName["Fatura Nubank"]!!.status)
        assertEquals(MovementStatus.Expected, byName["Freela"]!!.status)
    }

    @Test fun invoicePaidEarlyLeavesTheMoneyTodayButStaysInNovemberOnTheMonthlyView() {
        val paid = contas.map { if (it.id == faturaNov.id) it.copy(status = "pago") else it }
        val tl = timeline(c = paid)
        val row = tl.current!!.movements.single { (it.source as? CycleSource.Conta)?.conta?.id == faturaNov.id }
        assertEquals(today, row.date)
        assertEquals(d(12), row.movedFrom)
        assertEquals(MovementStatus.Paid, row.status)
        // Saldo de hoje já sem os R$ 2.000; o menor saldo (e o livre) não muda.
        assertEquals(221_900L, tl.cycle!!.saldoHojeCents)
        assertEquals(41_900L, tl.cycle!!.availableCents)
        assertEquals(FinancialPlan.build(paid, transacoes, dividas, emptyList(), today).livreCents, tl.cycle!!.availableCents)
        // A fatura não aparece duas vezes nem vai para o ciclo seguinte.
        assertEquals(1, tl.segments.flatMap { it.movements }.count { (it.source as? CycleSource.Conta)?.conta?.id == faturaNov.id })
        assertTrue(MonthCashFlow.of(paid, transacoes, dividas, YearMonth.of(2026, 11), today).payable.any { it.id == faturaNov.id })
    }

    @Test fun invoicePaidEarlyLowersTheFreeWhenTheTightDayCameBeforeItsDueDate() {
        // Gasto de R$ 4.000 em 08/11 e reembolso de R$ 4.000 em 10/11, antes da fatura de 12/11:
        // com a fatura por pagar, 08/11 fica em R$ 219. Paga hoje, o dinheiro já saiu e 08/11 fica negativo.
        val t = transacoes + tx(d(8), 400_000, TransactionType.Gasto, desc = "Notebook") +
            tx(d(10), 400_000, TransactionType.Receita, desc = "Reembolso")
        assertEquals(21_900L, timeline(t = t).cycle!!.floor.cents)
        val paid = contas.map { if (it.id == faturaNov.id) it.copy(status = "pago") else it }
        val tl = timeline(c = paid, t = t)
        assertEquals(-178_100L, tl.cycle!!.floor.cents)
        assertNotNull(tl.cycle!!.shortfall)
        assertEquals(0L, tl.cycle!!.availableCents)
    }

    @Test fun payingTheInstallmentDoesNotCountItTwice() {
        // "Paguei a parcela": grava o gasto de hoje e a dívida anda um mês.
        val pago = tx(today, 48_100, TransactionType.Gasto, desc = "Picpay · parcela 5 de 8", origem = DebtSchedule.PAYMENT_ORIGIN)
        val tl = timeline(t = transacoes + pago, dv = listOf(DebtSchedule.afterPayment(picpay, today)))
        val cur = tl.current!!.movements
        assertEquals(1, cur.count { it.descricao.startsWith("Picpay") })
        assertEquals(MovementStatus.Done, cur.single { it.descricao.startsWith("Picpay") }.status)
        assertEquals(41_900L, tl.cycle!!.availableCents)
        // A parcela seguinte (05/12) é do próximo ciclo.
        val next = tl.segments.first { it.kind == SegmentKind.Future }
        assertTrue(next.movements.any { it.descricao == "Picpay" && it.date == d(5, 12) })
    }

    @Test fun nextCycleStartsFromItsOwnSalary() {
        val next = timeline().segments.first { it.kind == SegmentKind.Future }
        assertEquals(d(30), next.start)
        assertEquals(d(30, 12), next.nextPayday)
        assertEquals(500_000L, next.movements.first().balanceAfterCents)
        assertTrue(next.movements.first().mainIncome)
        assertTrue(next.movements.any { (it.source as? CycleSource.Conta)?.conta?.id == faturaDez.id })
        assertFalse(next.movements.any { (it.source as? CycleSource.Conta)?.conta?.id == faturaNov.id })
        assertEquals(MovementStatus.Expected, next.movements.first().status)
    }

    @Test fun invoiceItemsTransfersAndGoalsAreNotOutflows() {
        val item = tx(d(3), 90_000, TransactionType.Gasto, desc = "Compra no cartão", faturaId = 1)
        val transf = tx(d(4), 50_000, TransactionType.Transferencia, desc = "Para a poupança")
        val tl = timeline(t = transacoes + item + transf)
        assertFalse(tl.current!!.movements.any { it.descricao == "Compra no cartão" || it.descricao == "Para a poupança" })
        assertEquals(41_900L, tl.cycle!!.availableCents)
        // Meta pedindo aporte não vira despesa: o livre continua o mesmo.
        val meta = ObjetivoEntity(id = 1, tipo = "Viagem", nome = "Viagem", valorAlvoCentavos = 1_000_000, valorGuardadoCentavos = 0,
            prazo = d(1, 6).plusYears(1).toEpochMillis(), prioridade = 1)
        assertEquals(41_900L, FinancialPlan.build(contas, transacoes, dividas, listOf(meta), today).livreCents)
    }

    @Test fun previousCycleShowsOnlyWhatHappenedAndOverdueBillComesToToday() {
        val luz = conta(13, "Luz", 20_000, d(20, 10))
        val agua = conta(14, "Água", 10_000, d(15, 10), status = "pago")
        val tl = timeline(c = contas + luz + agua)
        val prev = tl.segments.first()
        assertEquals(SegmentKind.Previous, prev.kind)
        assertEquals(d(30, 9), prev.start)
        assertTrue(prev.movements.any { it.descricao == "Água" })
        assertFalse(prev.movements.any { it.descricao == "Luz" })
        assertNull(prev.floor)
        val luzHoje = tl.current!!.movements.single { it.descricao == "Luz" }
        assertEquals(today, luzHoje.date)
        assertEquals(d(20, 10), luzHoje.movedFrom)
        assertEquals(MovementStatus.Overdue, luzHoje.status)
    }

    @Test fun withoutMainIncomeNoBalanceIsInvented() {
        val tl = timeline(t = listOf(mercado))
        assertNull(tl.cycle)
        assertTrue(tl.segments.isEmpty())
        assertTrue(tl.unprojected.isNotEmpty())
        assertTrue(tl.unprojected.all { it.balanceAfterCents == null })
        assertTrue(tl.aiBlock().contains("sem renda principal"))
    }

    @Test fun aiBlockFollowsTheChronologicalOrder() {
        val block = timeline().aiBlock()
        assertTrue(block, block.contains("12/11 Fatura Nubank: -R$ 2.000 → saldo R$ 2.219"))
        assertTrue(block, block.contains("Menor saldo até o salário: R$ 419 em 20/11"))
        assertTrue(block, block.contains("Livre para gastar hoje sem deixar nenhum pagamento acima sem dinheiro: R$ 419"))
        assertTrue(block, block.contains("Sobra no fim do ciclo (véspera de 30/11): R$ 1.219"))
        // Saídas do ciclo 300 + 481 + 2.000 + 1.800 = 4.581 de 5.800 de entradas.
        assertTrue(block, block.contains("R$ 4.581, 78% das entradas"))
        assertTrue(block, block.contains("Próximo ciclo (30/11 a 29/12)"))
    }

    @Test fun spendingTodayIsCheckedAgainstTheBillsAhead() {
        val snapshot = FinanceSnapshot(contas, transacoes, dividas, emptyList())
        val muito = PurchaseAssistant.handle("Se eu gastar R$ 500 hoje, conseguirei pagar as contas previstas?", null, snapshot, today)
        assertTrue(muito is PurchaseAssistant.Step.Simulated)
        assertFalse((muito as PurchaseAssistant.Step.Simulated).result.asked.billsCovered)
        // "Gastar hoje" sem forma vira Pix/débito hoje, e a resposta diz que supôs isso.
        assertTrue(muito.result.notes.any { it.contains("Pix/débito") })
        val pouco = PurchaseAssistant.handle("Se eu gastar R$ 300 hoje, conseguirei pagar as contas previstas?", null, snapshot, today)
        assertTrue((pouco as PurchaseAssistant.Step.Simulated).result.asked.billsCovered)
    }
}
