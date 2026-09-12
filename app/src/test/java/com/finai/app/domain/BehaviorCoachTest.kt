package com.finai.app.domain

import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorCoachTest {

    // Sexta-feira — usado para construir datas de dia útil/fim de semana previsíveis.
    private val today = LocalDate.of(2026, 3, 20)

    private fun tx(dia: LocalDate, categoria: String, valor: Long, descricao: String = categoria, recorrente: Boolean = false) =
        TransacaoEntity(
            data = dia.toEpochMillis(), descricao = descricao, valorCentavos = valor,
            categoria = categoria, contaOrigem = "Nubank", recorrente = recorrente, origem = "manual",
        )

    @Test
    fun `sem transacoes nao detecta nenhum padrao`() {
        assertTrue(BehaviorCoach.detect(emptyList(), today).isEmpty())
    }

    @Test
    fun `categoria que cresce mais de 30 por cento vira padrao de crescimento`() {
        val lastMonth = today.minusMonths(1)
        val transacoes = listOf(
            tx(lastMonth.withDayOfMonth(5), "Lazer", 10_000),
            tx(today.withDayOfMonth(5), "Lazer", 15_000), // +50%
        )
        val patterns = BehaviorCoach.detect(transacoes, today)
        assertTrue(patterns.any { it.kind == BehaviorPatternKind.CATEGORY_GROWTH && it.id == "coach:crescimento:Lazer" })
    }

    @Test
    fun `crescimento pequeno nao gera padrao`() {
        val lastMonth = today.minusMonths(1)
        val transacoes = listOf(
            tx(lastMonth.withDayOfMonth(5), "Saúde", 10_000),
            tx(today.withDayOfMonth(5), "Saúde", 10_500), // +5%
        )
        assertTrue(BehaviorCoach.detect(transacoes, today).none { it.kind == BehaviorPatternKind.CATEGORY_GROWTH })
    }

    @Test
    fun `estabelecimento repetido 4 ou mais vezes vira habito frequente`() {
        val transacoes = (1..4).map { tx(today.withDayOfMonth(it), "Alimentação", 4_000, descricao = "iFood") }
        val patterns = BehaviorCoach.detect(transacoes, today)
        val habito = patterns.firstOrNull { it.kind == BehaviorPatternKind.FREQUENT_MERCHANT }
        assertEquals("coach:habito:ifood", habito?.id)
        assertTrue(habito!!.detail.contains("4 vezes"))
    }

    @Test
    fun `assinatura recorrente nao conta como habito frequente`() {
        val transacoes = (1..5).map { tx(today.withDayOfMonth(it), "Assinaturas", 2_000, descricao = "Streaming X", recorrente = true) }
        assertTrue(BehaviorCoach.detect(transacoes, today).none { it.kind == BehaviorPatternKind.FREQUENT_MERCHANT })
    }

    @Test
    fun `gasto concentrado no fim de semana e detectado`() {
        // today = sexta 20/03/2026 -> sábado 21, domingo 22, sábado 28 (dentro do mesmo mês)
        val transacoes = listOf(
            tx(LocalDate.of(2026, 3, 21), "Lazer", 20_000), // sábado
            tx(LocalDate.of(2026, 3, 22), "Lazer", 20_000), // domingo
            tx(LocalDate.of(2026, 3, 28), "Lazer", 20_000), // sábado
            tx(LocalDate.of(2026, 3, 10), "Lazer", 5_000), // terça — pequena fatia
        )
        val patterns = BehaviorCoach.detect(transacoes, today)
        val fimDeSemana = patterns.firstOrNull { it.kind == BehaviorPatternKind.WEEKEND_CONCENTRATION }
        assertEquals("coach:fimdesemana:Lazer", fimDeSemana?.id)
    }

    @Test
    fun `gasto concentrado em dias uteis e detectado`() {
        // segunda a sexta na mesma semana de today (16 a 20/03/2026), sem nenhum no fim de semana
        val transacoes = (16..20).map { tx(LocalDate.of(2026, 3, it), "Alimentação", 3_000, descricao = "Almoço $it") }
        val patterns = BehaviorCoach.detect(transacoes, today)
        val diaUtil = patterns.firstOrNull { it.kind == BehaviorPatternKind.WEEKDAY_CONCENTRATION }
        assertEquals("coach:diautil:Alimentação", diaUtil?.id)
    }

    @Test
    fun `crescimento tem prioridade sobre outros padroes quando varios existem`() {
        val lastMonth = today.minusMonths(1)
        val transacoes = listOf(
            tx(lastMonth.withDayOfMonth(5), "Lazer", 10_000),
            tx(today.withDayOfMonth(5), "Lazer", 20_000), // crescimento +100%
        ) + (1..4).map { tx(today.withDayOfMonth(it), "Alimentação", 4_000, descricao = "iFood") } // hábito frequente
        val patterns = BehaviorCoach.detect(transacoes, today)
        assertEquals(BehaviorPatternKind.CATEGORY_GROWTH, patterns.first().kind)
    }
}
