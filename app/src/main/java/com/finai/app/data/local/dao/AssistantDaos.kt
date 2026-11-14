package com.finai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.finai.app.data.local.entity.AcaoAssistenteEntity
import com.finai.app.data.local.entity.AcompanhamentoEntity
import com.finai.app.data.local.entity.PlanoEntity
import com.finai.app.data.local.entity.SessaoAssistenteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessaoAssistenteDao {
    @Query("SELECT * FROM sessoes_assistente WHERE conversaId = :conversaId") suspend fun get(conversaId: Long): SessaoAssistenteEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: SessaoAssistenteEntity)
    @Query("DELETE FROM sessoes_assistente WHERE conversaId = :conversaId") suspend fun delete(conversaId: Long)
    @Query("DELETE FROM sessoes_assistente") suspend fun deleteAll()
}

@Dao
interface AcaoAssistenteDao {
    @Query("SELECT * FROM acoes_assistente WHERE chave = :chave") suspend fun byKey(chave: String): AcaoAssistenteEntity?
    @Query("SELECT * FROM acoes_assistente WHERE id = :id") suspend fun byId(id: Long): AcaoAssistenteEntity?
    @Query("SELECT * FROM acoes_assistente WHERE conversaId = :conversaId AND estado = 'applied' ORDER BY id DESC LIMIT 1")
    suspend fun lastApplied(conversaId: Long): AcaoAssistenteEntity?
    @Query("SELECT * FROM acoes_assistente ORDER BY id DESC") fun observeAll(): Flow<List<AcaoAssistenteEntity>>
    @Insert suspend fun insert(value: AcaoAssistenteEntity): Long
    @Update suspend fun update(value: AcaoAssistenteEntity)
    /** Recibo antigo não é desfeito com segurança: só os mais recentes ficam guardados. */
    @Query("DELETE FROM acoes_assistente WHERE id NOT IN (SELECT id FROM acoes_assistente ORDER BY id DESC LIMIT :keep)") suspend fun prune(keep: Int)
    @Query("DELETE FROM acoes_assistente") suspend fun deleteAll()
}

@Dao
interface AcompanhamentoDao {
    @Query("SELECT * FROM acompanhamentos ORDER BY id DESC") fun observeAll(): Flow<List<AcompanhamentoEntity>>
    @Query("SELECT * FROM acompanhamentos ORDER BY id DESC") suspend fun getAll(): List<AcompanhamentoEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: AcompanhamentoEntity): Long
    @Query("DELETE FROM acompanhamentos WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM acompanhamentos") suspend fun deleteAll()
}

@Dao
interface PlanoDao {
    @Query("SELECT * FROM planos ORDER BY criadoEm DESC") fun observeAll(): Flow<List<PlanoEntity>>
    @Query("SELECT * FROM planos ORDER BY criadoEm DESC") suspend fun getAll(): List<PlanoEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: PlanoEntity): Long
    @Query("DELETE FROM planos WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM planos") suspend fun deleteAll()
}
