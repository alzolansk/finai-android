package com.finai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
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
}

@Dao
interface OrcamentoCategoriaDao {
    @Query("SELECT * FROM orcamento_categorias WHERE mesReferencia = :mesReferencia")
    fun observeForMonth(mesReferencia: String): Flow<List<OrcamentoCategoriaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(orcamento: OrcamentoCategoriaEntity)
}

@Dao
interface AssinaturaDao {
    @Query("SELECT * FROM assinaturas ORDER BY nome ASC")
    fun observeAll(): Flow<List<AssinaturaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assinatura: AssinaturaEntity): Long

    @Update
    suspend fun update(assinatura: AssinaturaEntity)
}

@Dao
interface MensagemChatDao {
    // id desempata mensagens gravadas no mesmo milissegundo (pergunta + resposta rápida da IA).
    @Query("SELECT * FROM mensagens_chat ORDER BY timestamp ASC, id ASC")
    fun observeAll(): Flow<List<MensagemChatEntity>>

    @Insert
    suspend fun insert(mensagem: MensagemChatEntity): Long
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
}
