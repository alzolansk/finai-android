package com.finai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.finai.app.data.local.entity.AvisoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AvisoDao {
    @Query("SELECT * FROM avisos") fun observeAll(): Flow<List<AvisoEntity>>
    @Query("SELECT * FROM avisos") suspend fun getAll(): List<AvisoEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: AvisoEntity)
    @Query("DELETE FROM avisos") suspend fun deleteAll()
}
