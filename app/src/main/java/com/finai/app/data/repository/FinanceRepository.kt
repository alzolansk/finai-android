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
    suspend fun excluirTransacao(transacao: TransacaoEntity) = transacaoDao.delete(transacao)

    suspend fun salvarConta(conta: ContaEntity) =
        if (conta.id == 0L) contaDao.upsert(conta) else contaDao.update(conta).let { conta.id }
    suspend fun excluirConta(conta: ContaEntity) = contaDao.delete(conta)

    /** Persiste a conta da Agenda, a fatura e seus itens numa transação SQLite atômica. */
    suspend fun salvarFatura(conta: ContaEntity, fatura: FaturaCartaoEntity, itens: List<TransacaoEntity>) {
        db.withTransaction {
            val contaId = contaDao.upsert(conta)
            val faturaId = faturaCartaoDao.insert(fatura.copy(contaId = contaId))
            itens.forEach { transacaoDao.upsert(it.copy(faturaId = faturaId)) }
        }
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
