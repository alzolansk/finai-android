package com.finai.app.domain

import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetCalculatorTest {

    @Test
    fun `spend over limit is flagged Over`() {
        val today = LocalDate.of(2026, 3, 15)
        val categorias = listOf(OrcamentoCategoriaEntity("Lazer", 100_000, monthKey(today)))
        val transacoes = listOf(
            TransacaoEntity(data = today.toEpochMillis(), descricao = "Show", valorCentavos = 120_000, categoria = "Lazer", contaOrigem = "", recorrente = false, origem = "manual"),
        )
        val result = BudgetCalculator.forCategories(categorias, transacoes, today).single()
        assertEquals(BudgetTone.Over, result.tone)
        assertEquals(120_000L, result.spentCents)
    }

    @Test
    fun `pace projection flags Warn before the limit is actually blown`() {
        // day 10 of a 30-day month, already at 50% of a monthly limit -> projects to 150%
        val today = LocalDate.of(2026, 4, 10)
        val categorias = listOf(OrcamentoCategoriaEntity("Alimentação", 100_000, monthKey(today)))
        val transacoes = listOf(
            TransacaoEntity(data = today.toEpochMillis(), descricao = "Mercado", valorCentavos = 50_000, categoria = "Alimentação", contaOrigem = "", recorrente = false, origem = "manual"),
        )
        val result = BudgetCalculator.forCategories(categorias, transacoes, today).single()
        assertEquals(BudgetTone.Warn, result.tone)
    }

    @Test
    fun `spend well under pace is Ok`() {
        val today = LocalDate.of(2026, 4, 10)
        val categorias = listOf(OrcamentoCategoriaEntity("Saúde", 100_000, monthKey(today)))
        val transacoes = listOf(
            TransacaoEntity(data = today.toEpochMillis(), descricao = "Farmácia", valorCentavos = 10_000, categoria = "Saúde", contaOrigem = "", recorrente = false, origem = "manual"),
        )
        val result = BudgetCalculator.forCategories(categorias, transacoes, today).single()
        assertEquals(BudgetTone.Ok, result.tone)
    }

    @Test
    fun `transactions outside the category are ignored`() {
        val today = LocalDate.of(2026, 4, 10)
        val categorias = listOf(OrcamentoCategoriaEntity("Transporte", 100_000, monthKey(today)))
        val transacoes = listOf(
            TransacaoEntity(data = today.toEpochMillis(), descricao = "Show", valorCentavos = 90_000, categoria = "Lazer", contaOrigem = "", recorrente = false, origem = "manual"),
        )
        val result = BudgetCalculator.forCategories(categorias, transacoes, today).single()
        assertEquals(0L, result.spentCents)
    }
}
