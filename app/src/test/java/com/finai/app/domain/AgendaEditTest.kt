package com.finai.app.domain

import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Edição na Agenda: parcela de dívida com data alterada só nela, recorrente alterado "somente
 * este mês" ou "deste mês em diante", avulso, dias 28–31 e totais do mês.
 */
class AgendaEditTest {
    private val today = LocalDate.of(2026, 4, 1)

    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    private fun internet(start: LocalDate = LocalDate.of(2026, 1, 10), id: Long = 7) = TransacaoEntity(
        id = id, data = start.toEpochMillis(), descricao = "Internet", valorCentavos = 12_000, categoria = "Serviços",
        contaOrigem = "Nubank", recorrente = true, origem = "manual",
    )

    private fun change(date: LocalDate, cents: Long = 12_000, descricao: String = "Internet", categoria: String = "Serviços", conta: String = "Nubank") =
        EntryChange(date, cents, descricao, categoria, conta)

    /** Grava a edição numa "tabela" em memória, como o repositório faz (update + insert com id novo). */
    private fun apply(table: List<TransacaoEntity>, edit: SeriesEdit): List<TransacaoEntity> {
        val gone = edit.delete.map { it.id }.toSet()
        val updated = table.filter { it.id !in gone }.map { if (it.id == edit.update?.id) edit.update!! else it }
        return updated + listOfNotNull(edit.insert?.copy(id = (table.maxOf { it.id } + 1)))
    }

    /** Como a Agenda chama: a série gravada e os trechos seguintes dela. */
    private fun editAt(table: List<TransacaoEntity>, id: Long, month: YearMonth, change: EntryChange, scope: EditScope): List<TransacaoEntity> {
        val series = table.first { it.id == id }
        return apply(table, RecurringSeries.edit(series, month, change, scope, RecurringSeries.laterSegments(series, month, table)))
    }

    private fun inMonth(table: List<TransacaoEntity>, y: Int, m: Int) = table.transactionsInMonth(LocalDate.of(y, m, 1))

    // ── 1, 2 · parcela de dívida ──────────────────────────────────────

    private fun loan() = DividaEntity(
        id = 1, nome = "Empréstimo", valorOriginalCentavos = 600_000, valorAbertoCentavos = 300_000,
        taxaJurosMensalBasisPoints = 200, parcelasRestantes = 6, valorParcelaCentavos = 50_000,
        parcelasTotais = 12, proximoVencimento = LocalDate.of(2026, 4, 30).toEpochMillis(), diaVencimento = 30,
    )

    @Test fun changingOneInstallmentDateMovesOnlyThatInstallment() {
        val d = DebtSchedule.withInstallmentDate(loan(), ym(2026, 5), LocalDate.of(2026, 5, 31), today)
        fun due(m: Int) = DebtSchedule.installmentInMonth(d, ym(2026, m), today)!!
        assertEquals(LocalDate.of(2026, 4, 30), due(4).vencimento)
        assertEquals(LocalDate.of(2026, 5, 31), due(5).vencimento)
        assertEquals(LocalDate.of(2026, 6, 30), due(6).vencimento)
        // Valor, número e vínculo com a dívida preservados; a regra geral não mudou.
        assertEquals(50_000, due(5).valorCentavos)
        assertEquals(8, due(5).numero)
        assertEquals(1L, due(5).divida.id)
        assertEquals(30, d.diaVencimento)
        assertEquals(loan().proximoVencimento, d.proximoVencimento)
        // Uma parcela por mês: nada duplicado.
        assertEquals(1, DebtSchedule.installmentsInMonth(listOf(d), ym(2026, 5), today).size)
    }

    @Test fun installmentAdjustmentSurvivesPaymentsAndUndo() {
        val adjusted = DebtSchedule.withInstallmentDate(loan(), ym(2026, 5), LocalDate.of(2026, 5, 31), today)
        val paid = DebtSchedule.afterPayment(adjusted, LocalDate.of(2026, 4, 30))
        assertEquals(LocalDate.of(2026, 5, 31), DebtSchedule.installmentInMonth(paid, ym(2026, 5), today)!!.vencimento)
        assertTrue(DebtSchedule.installmentInMonth(paid, ym(2026, 5), today)!!.isNext)
        // Pagar a de maio tira ela da projeção: a parcela paga não é mais editável.
        val paidMay = DebtSchedule.afterPayment(paid, LocalDate.of(2026, 5, 31))
        assertNull(DebtSchedule.installmentInMonth(paidMay, ym(2026, 5), today))
        assertEquals(LocalDate.of(2026, 6, 30), DebtSchedule.installmentInMonth(paidMay, ym(2026, 6), today)!!.vencimento)
        assertFalse(runCatching { DebtSchedule.withInstallmentDate(paidMay, ym(2026, 5), LocalDate.of(2026, 5, 20), today) }.isSuccess)
    }

    @Test fun installmentDateMustStayInItsMonthAndRegularDateClearsTheAdjustment() {
        assertFalse(runCatching { DebtSchedule.withInstallmentDate(loan(), ym(2026, 5), LocalDate.of(2026, 6, 1), today) }.isSuccess)
        val moved = DebtSchedule.withInstallmentDate(loan(), ym(2026, 5), LocalDate.of(2026, 5, 31), today)
        val back = DebtSchedule.withInstallmentDate(moved, ym(2026, 5), LocalDate.of(2026, 5, 30), today)
        assertEquals("", back.ajustesVencimento)
    }

    @Test fun changingTheDayFromAnInstallmentKeepsTheEarlierOnes() {
        val d = DebtSchedule.withDueDayFrom(loan(), ym(2026, 6), 10, today)
        fun due(m: Int) = DebtSchedule.installmentInMonth(d, ym(2026, m), today)!!.vencimento
        assertEquals(LocalDate.of(2026, 4, 30), due(4))
        assertEquals(LocalDate.of(2026, 5, 30), due(5))
        assertEquals(LocalDate.of(2026, 6, 10), due(6))
        assertEquals(LocalDate.of(2026, 9, 10), due(9))
        assertEquals(10, d.diaVencimento)
    }

    // ── 3, 4, 5 · recorrente ────────────────────────────────────────

    @Test fun onlyThisMonthChangesOnlyThatMonth() {
        val table = listOf(internet())
        val edited = apply(table, RecurringSeries.edit(table[0], ym(2026, 11), change(LocalDate.of(2026, 11, 10), 13_500), EditScope.OnlyThis))
        assertEquals(1, edited.size) // nenhum registro novo
        assertEquals(12_000, inMonth(edited, 2026, 10).single().valorCentavos)
        assertEquals(13_500, inMonth(edited, 2026, 11).single().valorCentavos)
        assertEquals(12_000, inMonth(edited, 2026, 12).single().valorCentavos)
        assertEquals(7L, inMonth(edited, 2026, 11).single().id)
    }

    @Test fun thisAndFollowingKeepsHistory() {
        val table = listOf(internet())
        val edited = apply(table, RecurringSeries.edit(table[0], ym(2026, 11), change(LocalDate.of(2026, 11, 10), 13_500), EditScope.ThisAndFollowing))
        assertEquals(12_000, inMonth(edited, 2026, 1).single().valorCentavos)
        assertEquals(12_000, inMonth(edited, 2026, 10).single().valorCentavos)
        assertEquals(13_500, inMonth(edited, 2026, 11).single().valorCentavos)
        assertEquals(13_500, inMonth(edited, 2027, 6).single().valorCentavos)
        // Cada mês continua com uma cobrança só.
        (1..24).forEach { i ->
            val m = ym(2026, 1).plusMonths(i.toLong() - 1)
            assertEquals("mês $m", 1, edited.transactionsInMonth(m.atDay(1)).size)
        }
    }

    @Test fun thisAndFollowingOnTheFirstMonthChangesTheSeriesItself() {
        val table = listOf(internet())
        val edit = RecurringSeries.edit(table[0], ym(2026, 1), change(LocalDate.of(2026, 1, 10), 9_900), EditScope.ThisAndFollowing)
        assertNull(edit.insert)
        assertEquals(9_900, inMonth(apply(table, edit), 2026, 8).single().valorCentavos)
    }

    @Test fun laterExceptionsSurviveASplitAndEarlierOnesStayInHistory() {
        var table = listOf(internet())
        table = apply(table, RecurringSeries.edit(table[0], ym(2026, 3), change(LocalDate.of(2026, 3, 10), 11_000), EditScope.OnlyThis))
        table = apply(table, RecurringSeries.edit(table[0], ym(2026, 12), change(LocalDate.of(2026, 12, 5)), EditScope.OnlyThis))
        table = apply(table, RecurringSeries.edit(table[0], ym(2026, 11), change(LocalDate.of(2026, 11, 10), 13_500), EditScope.ThisAndFollowing))
        assertEquals(11_000, inMonth(table, 2026, 3).single().valorCentavos)
        val dec = inMonth(table, 2026, 12).single()
        assertEquals(LocalDate.of(2026, 12, 5), dec.data.toLocalDate())
        assertEquals(13_500, dec.valorCentavos)
    }

    @Test fun thisAndFollowingFromAnEarlierMonthAlsoReplacesLaterSegments() {
        var table = listOf(internet())
        table = editAt(table, 7, ym(2026, 11), change(LocalDate.of(2026, 11, 10), 13_500), EditScope.ThisAndFollowing)
        // Exceção de um mês do trecho novo: continua valendo depois da nova mudança.
        val second = table.first { it.id != 7L }
        assertEquals(7L, second.serieId)
        table = editAt(table, second.id, ym(2027, 2), change(LocalDate.of(2027, 2, 10), 99_00), EditScope.OnlyThis)
        table = editAt(table, 7, ym(2026, 6), change(LocalDate.of(2026, 6, 10), 12_500), EditScope.ThisAndFollowing)
        assertEquals(12_000, inMonth(table, 2026, 5).single().valorCentavos)
        assertEquals(12_500, inMonth(table, 2026, 10).single().valorCentavos)
        assertEquals(12_500, inMonth(table, 2026, 11).single().valorCentavos)
        assertEquals(12_500, inMonth(table, 2027, 8).single().valorCentavos)
        assertEquals(99_00, inMonth(table, 2027, 2).single().valorCentavos)
        assertEquals(2, table.size)
        (0L..30L).forEach { i -> assertEquals(1, table.transactionsInMonth(ym(2026, 1).plusMonths(i).atDay(1)).size) }
    }

    @Test fun deleteOnlyThisMonthSkipsOneOccurrence() {
        val table = listOf(RecurringSeries.deleteOnly(internet(), ym(2026, 5)))
        assertTrue(inMonth(table, 2026, 5).isEmpty())
        assertEquals(1, inMonth(table, 2026, 4).size)
        assertEquals(1, inMonth(table, 2026, 6).size)
    }

    @Test fun deleteFromAMonthKeepsHistoryAndRemovesLaterSegments() {
        var table = listOf(internet())
        table = editAt(table, 7, ym(2026, 11), change(LocalDate.of(2026, 11, 10), 13_500), EditScope.ThisAndFollowing)
        val series = table.first { it.id == 7L }
        table = apply(table, RecurringSeries.deleteFrom(series, ym(2026, 8), RecurringSeries.laterSegments(series, ym(2026, 8), table)))
        assertEquals(1, table.size)
        assertEquals(1, inMonth(table, 2026, 7).size)
        assertTrue(inMonth(table, 2026, 8).isEmpty())
        assertTrue(inMonth(table, 2027, 1).isEmpty())
        // No primeiro mês, some a série inteira.
        assertTrue(apply(listOf(internet()), RecurringSeries.deleteFrom(internet(), ym(2026, 1))).isEmpty())
    }

    @Test fun exceptionDateIsNotUsedToRecomputeTheSequence() {
        var table = listOf(internet())
        table = apply(table, RecurringSeries.edit(table[0], ym(2026, 11), change(LocalDate.of(2026, 11, 3)), EditScope.OnlyThis))
        // Valor muda deste mês em diante, mas a data de novembro, que a tela só mostrou, não vira regra.
        val occurrence = inMonth(table, 2026, 11).single()
        table = apply(table, RecurringSeries.edit(table[0], ym(2026, 11), change(occurrence.data.toLocalDate(), 13_500), EditScope.ThisAndFollowing))
        assertEquals(LocalDate.of(2026, 11, 3), inMonth(table, 2026, 11).single().data.toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 10), inMonth(table, 2026, 12).single().data.toLocalDate())
        assertEquals(13_500, inMonth(table, 2026, 12).single().valorCentavos)
    }

    @Test fun onlyThisMonthAcceptsDescriptionCategoryAndAccount() {
        val table = listOf(internet())
        val edited = apply(table, RecurringSeries.edit(table[0], ym(2026, 5),
            change(LocalDate.of(2026, 5, 12), descricao = "Internet + TV", categoria = "Lazer", conta = "Itaú"), EditScope.OnlyThis))
        val may = inMonth(edited, 2026, 5).single()
        assertEquals("Internet + TV", may.descricao); assertEquals("Lazer", may.categoria); assertEquals("Itaú", may.contaOrigem)
        assertEquals(LocalDate.of(2026, 5, 12), may.data.toLocalDate())
        assertEquals("Internet", inMonth(edited, 2026, 6).single().descricao)
    }

    @Test fun editingBackToTheSeriesValuesClearsTheException() {
        var table = listOf(internet())
        table = apply(table, RecurringSeries.edit(table[0], ym(2026, 5), change(LocalDate.of(2026, 5, 10), 15_000), EditScope.OnlyThis))
        table = apply(table, RecurringSeries.edit(table[0], ym(2026, 5), change(LocalDate.of(2026, 5, 10)), EditScope.OnlyThis))
        assertEquals("", table[0].excecoes)
    }

    @Test fun recurringDateMustStayInTheSameMonth() {
        val r = runCatching { RecurringSeries.edit(internet(), ym(2026, 5), change(LocalDate.of(2026, 6, 1)), EditScope.OnlyThis) }
        assertTrue(r.isFailure)
    }

    // ── 6 · avulso ──────────────────────────────────────────────────

    @Test fun singleEntryIsEditedInPlace() {
        val t = internet().copy(recorrente = false, descricao = "Mercado", categoria = "Alimentação")
        val e = RecurringSeries.editSingle(t, change(LocalDate.of(2026, 1, 15), 8_990, "Mercado São João", "Alimentação", "Itaú"))
        assertEquals(7L, e.id)
        assertEquals(8_990, e.valorCentavos)
        assertEquals("Mercado São João", e.descricao)
        assertEquals(LocalDate.of(2026, 1, 15), e.data.toLocalDate())
        assertFalse(e.recorrente)
    }

    // ── 7 · persistência do formato ─────────────────────────────────

    @Test fun exceptionsRoundTripWithAnyText() {
        val list = listOf(
            OccurrenceOverride(ym(2026, 2), data = LocalDate.of(2026, 2, 28), valorCentavos = 1),
            OccurrenceOverride(ym(2026, 3), descricao = "Água; luz = 50% & café\nnovo", categoria = "Saúde", contaOrigem = "Itaú ç"),
        )
        assertEquals(list, OccurrenceCodec.decode(OccurrenceCodec.encode(list)))
        assertEquals(emptyList<OccurrenceOverride>(), OccurrenceCodec.decode("lixo\n2026-13;v=1"))
        val adj = mapOf(ym(2026, 5) to LocalDate.of(2026, 5, 31))
        assertEquals(adj, DebtSchedule.decodeAdjustments(DebtSchedule.encodeAdjustments(adj)))
        assertEquals(emptyMap<YearMonth, LocalDate>(), DebtSchedule.decodeAdjustments("2026-05=2026-06-01,x"))
    }

    // ── 8 · meses de 28, 29, 30 e 31 dias ───────────────────────────

    @Test fun day31SeriesUsesTheLastValidDayWithoutChangingTheBaseDay() {
        val t = internet(start = LocalDate.of(2027, 1, 31))
        fun date(y: Int, m: Int) = inMonth(listOf(t), y, m).single().data.toLocalDate()
        assertEquals(LocalDate.of(2027, 2, 28), date(2027, 2))
        assertEquals(LocalDate.of(2028, 2, 29), date(2028, 2))
        assertEquals(LocalDate.of(2027, 4, 30), date(2027, 4))
        assertEquals(LocalDate.of(2027, 5, 31), date(2027, 5))
        assertEquals(31, t.recurrenceDay)
    }

    @Test fun splitInAShortMonthKeepsTheBaseDay() {
        // Série do dia 31 alterada a partir de fevereiro (28): março continua no dia 31.
        var table = listOf(internet(start = LocalDate.of(2027, 1, 31)))
        table = apply(table, RecurringSeries.edit(table[0], ym(2027, 2), change(LocalDate.of(2027, 2, 28), 13_000), EditScope.ThisAndFollowing))
        assertEquals(LocalDate.of(2027, 3, 31), inMonth(table, 2027, 3).single().data.toLocalDate())
        // Série do dia 30 alterada em novembro (30 é o último dia): dezembro continua no 30, não no 31.
        var t30 = listOf(internet(start = LocalDate.of(2026, 1, 30)))
        t30 = apply(t30, RecurringSeries.edit(t30[0], ym(2026, 11), change(LocalDate.of(2026, 11, 30), 13_000), EditScope.ThisAndFollowing))
        assertEquals(LocalDate.of(2026, 12, 30), inMonth(t30, 2026, 12).single().data.toLocalDate())
        assertEquals(LocalDate.of(2027, 2, 28), inMonth(t30, 2027, 2).single().data.toLocalDate())
    }

    @Test fun exceptionInFebruaryDoesNotChangeMarch() {
        var table = listOf(internet(start = LocalDate.of(2027, 1, 29)))
        table = apply(table, RecurringSeries.edit(table[0], ym(2028, 2), change(LocalDate.of(2028, 2, 29), 20_000), EditScope.OnlyThis))
        assertEquals(LocalDate.of(2028, 2, 29), inMonth(table, 2028, 2).single().data.toLocalDate())
        assertEquals(LocalDate.of(2028, 3, 29), inMonth(table, 2028, 3).single().data.toLocalDate())
        assertEquals(LocalDate.of(2027, 2, 28), inMonth(table, 2027, 2).single().data.toLocalDate())
    }

    // ── 9, 10 · sem cobrança duplicada e totais ─────────────────────

    @Test fun monthTotalsFollowTheEdits() {
        val salario = TransacaoEntity(
            id = 1, data = LocalDate.of(2026, 1, 5).toEpochMillis(), descricao = "Salário", valorCentavos = 500_000,
            categoria = "Salário", contaOrigem = "Empresa", recorrente = true, origem = "manual", tipo = TransactionType.Receita.name,
            rendaPrincipal = true,
        )
        var table = listOf(salario, internet())
        fun saldo(y: Int, m: Int) = MonthCashFlow.of(emptyList(), table, emptyList(), ym(y, m), LocalDate.of(y, m, 1)).saldoCents
        assertEquals(488_000, saldo(2026, 11))
        table = apply(table, RecurringSeries.edit(table[1], ym(2026, 11), change(LocalDate.of(2026, 11, 10), 13_500), EditScope.OnlyThis))
        assertEquals(486_500, saldo(2026, 11))
        assertEquals(488_000, saldo(2026, 12))
        val series = table.first { it.id == 7L }
        table = apply(table, RecurringSeries.edit(series, ym(2026, 12), change(LocalDate.of(2026, 12, 10), 15_000), EditScope.ThisAndFollowing))
        assertEquals(485_000, saldo(2026, 12))
        assertEquals(485_000, saldo(2027, 3))
        assertEquals(486_500, saldo(2026, 11))
        assertEquals(488_000, saldo(2026, 10))
    }

    @Test fun payCycleUsesTheEditedSalaryOccurrence() {
        val salario = TransacaoEntity(
            id = 1, data = LocalDate.of(2026, 1, 5).toEpochMillis(), descricao = "Salário", valorCentavos = 500_000,
            categoria = "Salário", contaOrigem = "Empresa", recorrente = true, origem = "manual", tipo = TransactionType.Receita.name,
            rendaPrincipal = true,
        )
        // Salário de maio caiu no dia 8 (só neste mês).
        val edited = RecurringSeries.edit(salario, ym(2026, 5), EntryChange(LocalDate.of(2026, 5, 8), 500_000, "Salário", "Salário", "Empresa"), EditScope.OnlyThis).update!!
        val cycle = PayCycle.of(emptyList(), listOf(edited), emptyList(), LocalDate.of(2026, 5, 6))!!
        assertEquals(LocalDate.of(2026, 4, 5), cycle.inicio)
        assertEquals(LocalDate.of(2026, 5, 8), cycle.proximo)
        val next = PayCycle.of(emptyList(), listOf(edited), emptyList(), LocalDate.of(2026, 5, 20))!!
        assertEquals(LocalDate.of(2026, 5, 8), next.inicio)
        assertEquals(LocalDate.of(2026, 6, 5), next.proximo)
    }

    @Test fun endedSegmentStopsProjecting() {
        val ended = internet().copy(recorrenteAte = "2026-03")
        assertNotNull(inMonth(listOf(ended), 2026, 3).singleOrNull())
        assertTrue(inMonth(listOf(ended), 2026, 4).isEmpty())
    }

    @Test fun legacySeriesWithoutNewFieldsProjectsAsBefore() {
        val t = internet(start = LocalDate.of(2026, 9, 30))
        (0L..30L).forEach { i ->
            val m = ym(2026, 9).plusMonths(i)
            assertEquals(monthlyOccurrence(LocalDate.of(2026, 9, 30), m), inMonth(listOf(t), m.year, m.monthValue).single().data.toLocalDate())
        }
    }
}
