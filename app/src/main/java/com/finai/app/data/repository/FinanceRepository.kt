package com.finai.app.data.repository

import androidx.room.withTransaction
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import kotlinx.coroutines.flow.Flow
import com.finai.app.domain.CardPayment
import com.finai.app.domain.DebtSchedule
import com.finai.app.domain.EditScope
import com.finai.app.domain.EntryChange
import com.finai.app.domain.RecurringSeries
import com.finai.app.domain.SeriesEdit
import com.finai.app.domain.isRecurringSeries
import java.time.YearMonth
import com.finai.app.domain.CardPurchase
import com.finai.app.domain.Completion
import com.finai.app.domain.EntryReceipt
import com.finai.app.domain.GoalReceipt
import com.finai.app.domain.TransactionType
import com.finai.app.domain.isOpenReserve
import com.finai.app.domain.toEpochMillis
import com.finai.app.domain.toLocalDate

/**
 * Thin facade over the Room DAOs — one place the ViewModels talk to instead
 * of reaching into [FinaiDatabase] directly. No business logic lives here;
 * that's `domain/`'s job (planning.md §6).
 */
class FinanceRepository(private val db: FinaiDatabase) {
    private val transacaoDao = db.transacaoDao()
    private val contaDao = db.contaDao()
    private val faturaCartaoDao = db.faturaCartaoDao()
    private val objetivoDao = db.objetivoDao()
    private val dividaDao = db.dividaDao()
    private val orcamentoDao = db.orcamentoCategoriaDao()
    private val assinaturaDao = db.assinaturaDao()
    private val mensagemChatDao = db.mensagemChatDao()
    private val notificacaoEnviadaDao = db.notificacaoEnviadaDao()

    val transacoes: Flow<List<TransacaoEntity>> = transacaoDao.observeAll()
    val contas: Flow<List<ContaEntity>> = contaDao.observeAll()
    val faturasCartao: Flow<List<FaturaCartaoEntity>> = faturaCartaoDao.observeAll()
    val objetivos: Flow<List<ObjetivoEntity>> = objetivoDao.observeAll()
    val dividas: Flow<List<DividaEntity>> = dividaDao.observeAll()
    val assinaturas: Flow<List<AssinaturaEntity>> = assinaturaDao.observeAll()

    fun orcamentosDoMes(mesReferencia: String): Flow<List<OrcamentoCategoriaEntity>> =
        orcamentoDao.observeForMonth(mesReferencia)

    suspend fun salvarTransacao(transacao: TransacaoEntity) = transacaoDao.upsert(transacao)

    /**
     * Excluir um lançamento desfaz o que ele mexeu, na mesma transação: o item de fatura
     * recalcula o total da fatura e o aporte tira o mesmo valor do objetivo (que reabre se
     * deixar de alcançar o alvo).
     */
    suspend fun excluirTransacao(transacao: TransacaoEntity, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val stored = transacaoDao.getAll().firstOrNull { it.id == transacao.id } ?: return@withTransaction
        transacaoDao.delete(stored)
        stored.faturaId?.let { id ->
            val invoice = faturaCartaoDao.getAll().firstOrNull { it.id == id } ?: return@let
            val account = contaDao.getAll().firstOrNull { it.id == invoice.contaId } ?: return@let
            contaDao.update(account.copy(valorCentavos = transacaoDao.getAll().filter { it.faturaId == id }.sumOf { it.valorCentavos }))
        }
        stored.objetivoId?.let { retirarDoObjetivo(it, stored.valorCentavos, now) }
    }

    /**
     * Edição pela Agenda, numa transação. Avulso: grava no próprio registro (item de fatura
     * recalcula o total da fatura e fica no mesmo cartão; fatura paga bloqueia mudar o valor).
     * Recorrente: [scope] decide entre a exceção do mês e "deste mês em diante"
     * ([RecurringSeries.edit]), que segmenta a série sem tocar nos meses anteriores. Aporte e
     * pagamento de parcela não são editáveis: mudar o valor deles desalinharia o objetivo ou a
     * dívida. Devolve o id da linha que a Agenda destaca.
     */
    suspend fun editarLancamento(id: Long, month: YearMonth, change: EntryChange, scope: EditScope?): Long = db.withTransaction {
        val all = transacaoDao.getAll()
        val stored = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Esse lançamento não existe mais.")
        require(stored.objetivoId == null) { "Aporte não é editado aqui: exclua e registre de novo." }
        require(stored.origem != DebtSchedule.PAYMENT_ORIGIN) { "Para corrigir um pagamento de parcela, exclua e pague de novo." }
        if (stored.isRecurringSeries) {
            val edit = RecurringSeries.edit(
                stored, month, change, requireNotNull(scope) { "Escolha se a mudança vale só para este mês." },
                RecurringSeries.laterSegments(stored, month, all),
            )
            aplicar(edit)
        } else {
            val updated = RecurringSeries.editSingle(stored, change).let { e ->
                // O item continua na fatura do cartão em que foi lançado.
                if (stored.faturaId != null) e.copy(contaOrigem = stored.contaOrigem) else e
            }
            val faturaId = stored.faturaId
            if (faturaId != null && updated.valorCentavos != stored.valorCentavos) {
                val invoice = faturaCartaoDao.getAll().firstOrNull { it.id == faturaId }
                val account = invoice?.let { f -> contaDao.getAll().firstOrNull { it.id == f.contaId } }
                require(account?.status != "pago") {
                    "A fatura desta compra já foi paga. Desmarque o pagamento dela na Agenda para mudar o valor."
                }
                transacaoDao.upsert(updated)
                account?.let { a ->
                    contaDao.update(a.copy(valorCentavos = transacaoDao.getAll().filter { it.faturaId == faturaId }.sumOf { it.valorCentavos }))
                }
            } else {
                transacaoDao.upsert(updated)
            }
            id
        }
    }

    /**
     * Excluir uma ocorrência de recorrente: só a do mês ([EditScope.OnlyThis], a série continua)
     * ou dela em diante ([EditScope.ThisAndFollowing], os meses anteriores ficam).
     */
    suspend fun excluirOcorrencia(id: Long, month: YearMonth, scope: EditScope) = db.withTransaction {
        val all = transacaoDao.getAll()
        val stored = all.firstOrNull { it.id == id } ?: return@withTransaction
        require(stored.isRecurringSeries) { "Esse lançamento não se repete." }
        when (scope) {
            EditScope.OnlyThis -> transacaoDao.upsert(RecurringSeries.deleteOnly(stored, month))
            EditScope.ThisAndFollowing -> aplicar(RecurringSeries.deleteFrom(stored, month, RecurringSeries.laterSegments(stored, month, all)))
        }
        Unit
    }

    /** Grava uma [SeriesEdit]; devolve o id do trecho que passa a valer no mês editado. */
    private suspend fun aplicar(edit: SeriesEdit): Long {
        edit.delete.forEach { transacaoDao.delete(it) }
        edit.update?.let { transacaoDao.upsert(it) }
        val inserted = edit.insert?.let { transacaoDao.upsert(it) }
        return inserted ?: edit.update?.id ?: 0L
    }

    /** Lançamento comum do "+" (gasto no débito, receita, transferência entre contas). */
    suspend fun salvarLancamento(entry: TransacaoEntity): EntryReceipt = db.withTransaction {
        require(entry.id == 0L && entry.valorCentavos > 0 && entry.objetivoId == null)
        val id = transacaoDao.upsert(entry)
        EntryReceipt(TransactionType.valueOf(entry.tipo), listOf(id), entry.data.toLocalDate())
    }

    /** Compra no cartão: todas as parcelas e faturas entram juntas, ou nenhuma. */
    suspend fun salvarCompraCartao(entry: TransacaoEntity, payment: CardPayment): EntryReceipt = db.withTransaction {
        val gravadas = CardPurchase.schedule(entry, payment).map { (due, item) ->
            salvarFatura(
                ContaEntity(nome = "Fatura ${payment.card.trim()}", valorCentavos = item.valorCentavos,
                    vencimento = due.toEpochMillis(), status = "pendente", tipo = "a_pagar", recorrente = false),
                FaturaCartaoEntity(contaId = 0, referencia = payment.card.trim(), fechamento = null, vencimento = due.toEpochMillis()),
                listOf(item),
            )
        }
        EntryReceipt(
            type = TransactionType.Gasto,
            transactionIds = gravadas.flatMap { it.itemIds },
            agendaDate = payment.firstDueDate,
            createdInvoiceIds = gravadas.filter { it.criada }.map { it.faturaId },
            touchedInvoiceIds = gravadas.filter { !it.criada }.map { it.faturaId },
            invoiceContaId = gravadas.firstOrNull()?.contaId,
        )
    }

    /**
     * Aporte: a transferência e o novo valor guardado do objetivo numa transação só. O recibo
     * leva o antes e o depois lidos do banco, que é o que a confirmação anima.
     */
    suspend fun registrarAporte(entry: TransacaoEntity, now: Long = System.currentTimeMillis()): EntryReceipt = db.withTransaction {
        val goalId = requireNotNull(entry.objetivoId)
        require(entry.id == 0L && entry.tipo == TransactionType.Transferencia.name && entry.valorCentavos > 0 && !entry.recorrente)
        val goal = objetivoDao.getAll().firstOrNull { it.id == goalId }
            ?: throw IllegalArgumentException("Esse objetivo não existe mais. Escolha outro destino.")
        val id = transacaoDao.upsert(entry.copy(contaDestino = entry.contaDestino?.takeIf { it.isNotBlank() } ?: goal.nome))
        val update = Completion.onGoalSaved(goal, goal.copy(valorGuardadoCentavos = goal.valorGuardadoCentavos + entry.valorCentavos), now)
        objetivoDao.update(update.objetivo)
        val stored = objetivoDao.getAll().first { it.id == goalId }
        EntryReceipt(
            type = TransactionType.Transferencia,
            transactionIds = listOf(id),
            agendaDate = entry.data.toLocalDate(),
            goal = GoalReceipt(
                goalId = goalId, name = stored.nome, beforeCents = goal.valorGuardadoCentavos,
                afterCents = stored.valorGuardadoCentavos, targetCents = if (stored.isOpenReserve) 0 else stored.valorAlvoCentavos,
                celebrate = update.celebrate,
            ),
        )
    }

    /**
     * "Desfazer" da confirmação: apaga o que o recibo criou e reverte o que ele mexeu, tudo ou
     * nada. Fatura criada pela compra some se ficou vazia; fatura que já existia volta ao total
     * anterior. Uma fatura paga nesse meio-tempo bloqueia o desfazer (o valor dela já fechou).
     */
    suspend fun desfazer(receipt: EntryReceipt, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val ids = receipt.transactionIds.toSet()
        val removed = transacaoDao.getAll().filter { it.id in ids }
        val invoiceIds = (receipt.createdInvoiceIds + receipt.touchedInvoiceIds).toSet()
        val invoices = faturaCartaoDao.getAll().filter { it.id in invoiceIds }
        val contas = contaDao.getAll()
        invoices.forEach { f ->
            require(contas.firstOrNull { it.id == f.contaId }?.status != "pago") {
                "Essa fatura já foi marcada como paga. Desmarque o pagamento na Agenda para desfazer."
            }
        }
        removed.forEach { transacaoDao.delete(it) }
        val remaining = transacaoDao.getAll()
        invoices.forEach { f ->
            val conta = contas.firstOrNull { it.id == f.contaId } ?: return@forEach
            val items = remaining.filter { it.faturaId == f.id }
            if (items.isEmpty() && f.id in receipt.createdInvoiceIds) {
                faturaCartaoDao.delete(f)
                contaDao.delete(conta)
            } else {
                contaDao.update(conta.copy(valorCentavos = items.sumOf { it.valorCentavos }))
            }
        }
        removed.filter { it.objetivoId != null }.groupBy { it.objetivoId!! }
            .forEach { (goalId, list) -> retirarDoObjetivo(goalId, list.sumOf { it.valorCentavos }, now) }
    }

    private suspend fun retirarDoObjetivo(goalId: Long, cents: Long, now: Long) {
        val goal = objetivoDao.getAll().firstOrNull { it.id == goalId } ?: return
        val update = Completion.onGoalSaved(goal, goal.copy(valorGuardadoCentavos = (goal.valorGuardadoCentavos - cents).coerceAtLeast(0)), now)
        objetivoDao.update(update.objetivo)
    }

    /** Compra confirmada depois de uma simulação: todas as parcelas entram, ou nenhuma. */
    suspend fun salvarTransacoes(transacoes: List<TransacaoEntity>) = db.withTransaction {
        transacoes.forEach { transacaoDao.upsert(it) }
    }

    suspend fun salvarConta(conta: ContaEntity) =
        if (conta.id == 0L) contaDao.upsert(conta) else contaDao.update(conta).let { conta.id }
    suspend fun excluirConta(conta: ContaEntity) = db.withTransaction {
        faturaCartaoDao.getAll().filter { it.contaId == conta.id }.forEach { invoice ->
            transacaoDao.getAll().filter { it.faturaId == invoice.id }.forEach { transacaoDao.delete(it) }
            faturaCartaoDao.delete(invoice)
        }
        contaDao.delete(conta)
    }

    /** O que [salvarFatura] gravou: a fatura (nova ou reaproveitada), a conta dela e os itens. */
    data class FaturaGravada(val faturaId: Long, val contaId: Long, val criada: Boolean, val itemIds: List<Long>)

    /** Persiste a conta da Agenda, a fatura e seus itens numa transação SQLite atômica. */
    suspend fun salvarFatura(conta: ContaEntity, fatura: FaturaCartaoEntity, itens: List<TransacaoEntity>): FaturaGravada =
        db.withTransaction {
            val existing = faturaCartaoDao.getAll().firstOrNull {
                it.referencia.trim().equals(fatura.referencia.trim(), ignoreCase = true) &&
                    it.vencimento.toLocalDate() == fatura.vencimento.toLocalDate()
            }
            if (existing != null) {
                val account = requireNotNull(contaDao.getAll().firstOrNull { it.id == existing.contaId })
                require(account.status != "pago") { "Esta fatura já foi paga. Escolha uma fatura em aberto." }
                val ids = itens.map { transacaoDao.upsert(it.copy(faturaId = existing.id)) }
                contaDao.update(account.copy(valorCentavos = transacaoDao.getAll().filter { it.faturaId == existing.id }.sumOf { it.valorCentavos }))
                return@withTransaction FaturaGravada(existing.id, account.id, criada = false, itemIds = ids)
            }
            val contaId = contaDao.upsert(conta)
            val faturaId = faturaCartaoDao.insert(fatura.copy(contaId = contaId))
            val ids = itens.map { transacaoDao.upsert(it.copy(faturaId = faturaId)) }
            FaturaGravada(faturaId, contaId, criada = true, itemIds = ids)
        }

    suspend fun salvarObjetivo(objetivo: ObjetivoEntity) =
        if (objetivo.id == 0L) objetivoDao.upsert(objetivo) else objetivoDao.update(objetivo).let { objetivo.id }
    suspend fun excluirObjetivo(objetivo: ObjetivoEntity) = objetivoDao.delete(objetivo)

    suspend fun salvarDivida(divida: DividaEntity) =
        if (divida.id == 0L) dividaDao.upsert(divida) else dividaDao.update(divida).let { divida.id }
    suspend fun excluirDivida(divida: DividaEntity) = dividaDao.delete(divida)

    suspend fun salvarLimiteOrcamento(orcamento: OrcamentoCategoriaEntity) = orcamentoDao.upsert(orcamento)

    suspend fun atualizarAssinatura(assinatura: AssinaturaEntity) = assinaturaDao.update(assinatura)
    suspend fun salvarAssinatura(assinatura: AssinaturaEntity) = assinaturaDao.upsert(assinatura)

    /**
     * "Apagar todos os dados" das Configurações: limpa tudo que é dado
     * financeiro do usuário (lançamentos, contas, objetivos, dívidas,
     * orçamentos, assinaturas), o histórico de chat e o registro de
     * deduplicação de notificações — deixando o Room no mesmo estado vazio de
     * uma instalação nova. Numa transação só: ou tudo sai, ou nada sai (uma
     * falha no meio não deve deixar o banco pela metade). Chaves de IA
     * (`AiKeyStore`) e contagem de cota (`ProviderUsoIaEntity`/
     * `ProviderUsageStore`) são configuração técnica, não dado financeiro —
     * ficam de fora de propósito.
     */
    /** Retrato de todas as tabelas de dado do usuário, para exportar (Fase 7, item 12). */
    suspend fun exportar(schemaVersion: Int, now: Long): com.finai.app.data.backup.FinaiBackup = db.withTransaction {
        com.finai.app.data.backup.FinaiBackup(
            schemaVersion = schemaVersion,
            exportedAt = now,
            transacoes = transacaoDao.getAll(),
            faturas = faturaCartaoDao.getAll(),
            contas = contaDao.getAll(),
            objetivos = objetivoDao.getAll(),
            dividas = dividaDao.getAll(),
            orcamentos = orcamentoDao.getAll(),
            assinaturas = assinaturaDao.getAll(),
            mensagens = mensagemChatDao.getAll(),
        )
    }

    /**
     * Restaurar **substitui tudo**: apaga os dados atuais e grava os do arquivo, com os mesmos
     * ids (a fatura aponta para a conta, o item para a fatura), numa transação só — uma falha
     * no meio deixa o banco como estava.
     */
    suspend fun restaurar(backup: com.finai.app.data.backup.FinaiBackup) {
        db.withTransaction {
            transacaoDao.deleteAll()
            faturaCartaoDao.deleteAll()
            contaDao.deleteAll()
            objetivoDao.deleteAll()
            dividaDao.deleteAll()
            orcamentoDao.deleteAll()
            assinaturaDao.deleteAll()
            mensagemChatDao.deleteAll()
            notificacaoEnviadaDao.deleteAll()
            backup.contas.forEach { contaDao.upsert(it) }
            backup.faturas.forEach { faturaCartaoDao.insert(it) }
            backup.transacoes.forEach { transacaoDao.upsert(it) }
            backup.objetivos.forEach { objetivoDao.upsert(it) }
            backup.dividas.forEach { dividaDao.upsert(it) }
            backup.orcamentos.forEach { orcamentoDao.upsert(it) }
            backup.assinaturas.forEach { assinaturaDao.upsert(it) }
            backup.mensagens.forEach { mensagemChatDao.insert(it) }
        }
    }

    suspend fun apagarTodosOsDados() {
        db.withTransaction {
            transacaoDao.deleteAll()
            faturaCartaoDao.deleteAll()
            contaDao.deleteAll()
            objetivoDao.deleteAll()
            dividaDao.deleteAll()
            orcamentoDao.deleteAll()
            assinaturaDao.deleteAll()
            mensagemChatDao.deleteAll()
            notificacaoEnviadaDao.deleteAll()
        }
    }
}
