package com.finai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.ConversaResumo
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.local.entity.NotificacaoEnviadaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.local.entity.UsoProvedorIaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 1 real CRUD per entity, backing the manual-entry flows and the
 * deterministic calculators in `domain/` — see planning.md §6 and §9 (Fase 1).
 */

@Dao
interface TransacaoDao {
    @Query("SELECT * FROM transacoes ORDER BY data DESC")
    fun observeAll(): Flow<List<TransacaoEntity>>

    @Query("SELECT * FROM transacoes WHERE data BETWEEN :startMillis AND :endMillis ORDER BY data DESC")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransacaoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transacao: TransacaoEntity): Long

    @Delete
    suspend fun delete(transacao: TransacaoEntity)

    @Query("DELETE FROM transacoes")
    suspend fun deleteAll()
}

@Dao
interface FaturaCartaoDao {
    @Query("SELECT * FROM faturas_cartao ORDER BY vencimento DESC, id DESC")
    fun observeAll(): Flow<List<FaturaCartaoEntity>>

    @Insert
    suspend fun insert(fatura: FaturaCartaoEntity): Long

    @Query("DELETE FROM faturas_cartao")
    suspend fun deleteAll()
}

@Dao
interface ContaDao {
    @Query("SELECT * FROM contas ORDER BY vencimento ASC")
    fun observeAll(): Flow<List<ContaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conta: ContaEntity): Long

    @Update
    suspend fun update(conta: ContaEntity)

    @Delete
    suspend fun delete(conta: ContaEntity)

    @Query("DELETE FROM contas")
    suspend fun deleteAll()
}

@Dao
interface ObjetivoDao {
    @Query("SELECT * FROM objetivos ORDER BY prioridade ASC")
    fun observeAll(): Flow<List<ObjetivoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(objetivo: ObjetivoEntity): Long

    @Update
    suspend fun update(objetivo: ObjetivoEntity)

    @Delete
    suspend fun delete(objetivo: ObjetivoEntity)

    @Query("DELETE FROM objetivos")
    suspend fun deleteAll()
}

@Dao
interface DividaDao {
    @Query("SELECT * FROM dividas ORDER BY taxaJurosMensalBasisPoints DESC")
    fun observeAll(): Flow<List<DividaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(divida: DividaEntity): Long

    @Update
    suspend fun update(divida: DividaEntity)

    @Delete
    suspend fun delete(divida: DividaEntity)

    @Query("DELETE FROM dividas")
    suspend fun deleteAll()
}

@Dao
interface OrcamentoCategoriaDao {
    @Query("SELECT * FROM orcamento_categorias WHERE mesReferencia = :mesReferencia")
    fun observeForMonth(mesReferencia: String): Flow<List<OrcamentoCategoriaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(orcamento: OrcamentoCategoriaEntity)

    @Query("DELETE FROM orcamento_categorias")
    suspend fun deleteAll()
}

@Dao
interface AssinaturaDao {
    @Query("SELECT * FROM assinaturas ORDER BY nome ASC")
    fun observeAll(): Flow<List<AssinaturaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assinatura: AssinaturaEntity): Long

    @Update
    suspend fun update(assinatura: AssinaturaEntity)

    @Query("DELETE FROM assinaturas")
    suspend fun deleteAll()
}

@Dao
interface MensagemChatDao {
    // id desempata mensagens gravadas no mesmo milissegundo (pergunta + resposta rápida da IA).
    @Query("SELECT * FROM mensagens_chat ORDER BY timestamp ASC, id ASC")
    fun observeAll(): Flow<List<MensagemChatEntity>>

    @Query("SELECT * FROM mensagens_chat WHERE conversaId = :conversaId ORDER BY timestamp ASC, id ASC")
    fun observeConversa(conversaId: Long): Flow<List<MensagemChatEntity>>

    // Título = primeira pergunta do usuário na conversa.
    @Query(
        "SELECT m.conversaId AS conversaId, MIN(m.timestamp) AS inicio, MAX(m.timestamp) AS ultima, COUNT(*) AS total, " +
            "(SELECT p.texto FROM mensagens_chat AS p WHERE p.conversaId = m.conversaId AND p.papel = 'usuario' " +
            "ORDER BY p.timestamp ASC, p.id ASC LIMIT 1) AS titulo " +
            "FROM mensagens_chat AS m GROUP BY m.conversaId ORDER BY ultima DESC",
    )
    fun observeConversas(): Flow<List<ConversaResumo>>

    @Query("DELETE FROM mensagens_chat WHERE conversaId = :conversaId")
    suspend fun deleteConversa(conversaId: Long)

    @Insert
    suspend fun insert(mensagem: MensagemChatEntity): Long

    @Query("DELETE FROM mensagens_chat")
    suspend fun deleteAll()
}

@Dao
interface UsoProvedorIaDao {
    @Query("SELECT * FROM uso_provedor_ia WHERE data = :data")
    fun observeForDay(data: String): Flow<List<UsoProvedorIaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(uso: UsoProvedorIaEntity)
}

/** Backing store da deduplicação de notificações da Fase 5 — ver [NotificacaoEnviadaEntity]. */
@Dao
interface NotificacaoEnviadaDao {
    @Query("SELECT * FROM notificacoes_enviadas WHERE chave = :chave")
    suspend fun find(chave: String): NotificacaoEnviadaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(registro: NotificacaoEnviadaEntity)

    /** Remove registros de eventos que não existem mais (ex.: conta que foi paga) — evita crescer para sempre. */
    @Query("DELETE FROM notificacoes_enviadas WHERE chave NOT IN (:chavesAtivas)")
    suspend fun removerExceto(chavesAtivas: List<String>)

    @Query("DELETE FROM notificacoes_enviadas")
    suspend fun deleteAll()
}
