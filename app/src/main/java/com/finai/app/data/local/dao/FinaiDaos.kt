package com.finai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.local.entity.UsoProvedorIaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Minimal CRUD per entity — enough to prove the Room wiring compiles and
 * runs. Phase 1 adds the real queries each screen needs (safe-to-spend,
 * debt ordering, budget progress, ...) per planning.md §6.
 */

@Dao
interface TransacaoDao {
    @Query("SELECT * FROM transacoes ORDER BY data DESC")
    fun observeAll(): Flow<List<TransacaoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transacao: TransacaoEntity): Long
}

@Dao
interface ContaDao {
    @Query("SELECT * FROM contas ORDER BY vencimento ASC")
    fun observeAll(): Flow<List<ContaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conta: ContaEntity): Long
}

@Dao
interface ObjetivoDao {
    @Query("SELECT * FROM objetivos ORDER BY prioridade ASC")
    fun observeAll(): Flow<List<ObjetivoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(objetivo: ObjetivoEntity): Long
}

@Dao
interface DividaDao {
    @Query("SELECT * FROM dividas ORDER BY taxaJurosMensalBasisPoints DESC")
    fun observeAll(): Flow<List<DividaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(divida: DividaEntity): Long
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
    @Query("SELECT * FROM assinaturas WHERE status = 'ativa'")
    fun observeActive(): Flow<List<AssinaturaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assinatura: AssinaturaEntity): Long
}

@Dao
interface MensagemChatDao {
    @Query("SELECT * FROM mensagens_chat ORDER BY timestamp ASC")
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
