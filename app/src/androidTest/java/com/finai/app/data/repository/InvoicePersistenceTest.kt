package com.finai.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoicePersistenceTest {
    @Test fun invoice_is_one_bill_and_keeps_each_purchase_date() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, FinaiDatabase::class.java).build()
        try {
            val due = LocalDate.of(2026, 4, 10).toEpochMillis()
            val repository = FinanceRepository(database)
            repository.salvarFatura(
                ContaEntity(nome = "Fatura Cartão FinAI", valorCentavos = 12_500, vencimento = due, status = "pendente", tipo = "a_pagar", recorrente = false),
                FaturaCartaoEntity(contaId = 0, referencia = "Cartão FinAI", fechamento = null, vencimento = due),
                listOf(
                    TransacaoEntity(data = LocalDate.of(2026, 3, 2).toEpochMillis(), descricao = "Mercado", valorCentavos = 5_000, categoria = "Alimentação", contaOrigem = "Cartão FinAI", recorrente = false, origem = "importado"),
                    TransacaoEntity(data = LocalDate.of(2026, 3, 18).toEpochMillis(), descricao = "Livro", valorCentavos = 7_500, categoria = "Lazer", contaOrigem = "Cartão FinAI", recorrente = false, origem = "importado"),
                ),
            )

            val bills = repository.contas.first()
            val invoice = repository.faturasCartao.first().single()
            val items = repository.transacoes.first()
            assertEquals(1, bills.size)
            assertEquals(12_500, bills.single().valorCentavos)
            assertEquals(due, bills.single().vencimento)
            assertEquals(2, items.size)
            assertEquals(setOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 18)), items.map { it.data.toLocalDate() }.toSet())
            assertEquals(setOf(invoice.id), items.mapNotNull { it.faturaId }.toSet())
        } finally {
            database.close()
        }
    }
}
