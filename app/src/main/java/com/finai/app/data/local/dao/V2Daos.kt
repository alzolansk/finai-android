package com.finai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.finai.app.data.local.entity.FechamentoEntity
import com.finai.app.data.local.entity.MemoriaEntity
import com.finai.app.data.local.entity.RegraCategoriaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoriaDao {
    @Query("SELECT * FROM memoria ORDER BY criadoEm DESC") fun observeAll(): Flow<List<MemoriaEntity>>
    @Query("SELECT * FROM memoria ORDER BY criadoEm DESC") suspend fun getAll(): List<MemoriaEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: MemoriaEntity): Long
    @Query("DELETE FROM memoria WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM memoria WHERE origem = :origem") suspend fun deleteByOrigin(origem: String)
    @Query("DELETE FROM memoria") suspend fun deleteAll()
}

@Dao
interface RegraCategoriaDao {
    @Query("SELECT * FROM regras_categoria") fun observeAll(): Flow<List<RegraCategoriaEntity>>
    @Query("SELECT * FROM regras_categoria") suspend fun getAll(): List<RegraCategoriaEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: RegraCategoriaEntity)
    @Query("DELETE FROM regras_categoria") suspend fun deleteAll()
}

@Dao
interface FechamentoDao {
    @Query("SELECT * FROM fechamentos") fun observeAll(): Flow<List<FechamentoEntity>>
    @Query("SELECT * FROM fechamentos") suspend fun getAll(): List<FechamentoEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: FechamentoEntity)
    @Query("DELETE FROM fechamentos") suspend fun deleteAll()
}
