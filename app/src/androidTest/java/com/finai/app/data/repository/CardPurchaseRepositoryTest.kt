package com.finai.app.data.repository

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.*
import com.finai.app.data.backup.BackupCodec
import com.finai.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CardPurchaseRepositoryTest {
    @Test fun purchasesJoinImportedInvoiceAndPersistWithExactTotals() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "card-purchase-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
        try {
            val repository = FinanceRepository(db)
            val date = LocalDate.of(2026, 1, 15)
            val due = LocalDate.of(2026, 1, 31)
            val entry = TransacaoEntity(data = date.toEpochMillis(), descricao = "Ingresso", valorCentavos = 10000,
                categoria = "Lazer", contaOrigem = "Nubank", recorrente = false, origem = "manual")
            repository.salvarFatura(ContaEntity(nome = "Fatura Nubank", valorCentavos = 500, vencimento = due.toEpochMillis(),
                status = "pendente", tipo = "a_pagar", recorrente = false),
                FaturaCartaoEntity(contaId = 0, referencia = "Nubank", fechamento = null, vencimento = due.toEpochMillis()),
                listOf(entry.copy(valorCentavos = 500, origem = "importado")))
            repository.salvarCompraCartao(entry, CardPayment(" nubank ", due, 3))
            repository.salvarCompraCartao(entry.copy(valorCentavos = 100), CardPayment("Nubank", due))
            assertEquals(3, db.faturaCartaoDao().getAll().size)
            assertEquals(listOf(3934L, 3333L, 3333L), db.contaDao().getAll().sortedBy { it.vencimento }.map { it.valorCentavos })
            assertTrue(db.transacaoDao().getAll().all { it.faturaId != null && it.data == date.toEpochMillis() })
            val flow = MonthCashFlow.of(db.contaDao().getAll(), db.transacaoDao().getAll(), emptyList(), YearMonth.from(due), date)
            assertEquals(3934L, flow.toPayCents)
            assertEquals(1, flow.toPayCount)
            val backup = repository.exportar(12, 0)
            assertEquals(backup.transacoes, BackupCodec.decode(BackupCodec.encode(backup), 12).transacoes)
            val account = db.contaDao().getAll().minBy { it.vencimento }
            repository.salvarConta(account.copy(status = "pago"))
            try {
                repository.salvarCompraCartao(entry, CardPayment("Nubank", due, 3))
                fail("Paid invoice must reject the whole purchase")
            } catch (_: IllegalArgumentException) { }
            assertEquals(5, db.transacaoDao().getAll().size)
            db.close()
            db = Room.databaseBuilder(context, FinaiDatabase::class.java, name).build()
            assertEquals(3, db.transacaoDao().getAll().count { it.parcelasTotal == 3 })
            val item = db.transacaoDao().getAll().first { it.parcelaNumero == 2 }
            FinanceRepository(db).excluirTransacao(item)
            assertEquals(0L, db.contaDao().getAll().sortedBy { it.vencimento }[1].valorCentavos)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
