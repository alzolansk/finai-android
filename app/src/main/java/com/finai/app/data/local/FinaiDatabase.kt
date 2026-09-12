package com.finai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.finai.app.data.local.dao.AssinaturaDao
import com.finai.app.data.local.dao.ContaDao
import com.finai.app.data.local.dao.DividaDao
import com.finai.app.data.local.dao.MensagemChatDao
import com.finai.app.data.local.dao.ObjetivoDao
import com.finai.app.data.local.dao.OrcamentoCategoriaDao
import com.finai.app.data.local.dao.TransacaoDao
import com.finai.app.data.local.dao.UsoProvedorIaDao
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.local.entity.UsoProvedorIaEntity

/**
 * The app's only local database — everything financial lives here, on-device,
 * per planning.md §4's privacy-first requirement. Not yet instantiated from
 * any screen in Phase 0; wire it up via [get] once real repositories land.
 */
@Database(
    entities = [
        TransacaoEntity::class,
        ContaEntity::class,
        ObjetivoEntity::class,
        DividaEntity::class,
        OrcamentoCategoriaEntity::class,
        AssinaturaEntity::class,
        MensagemChatEntity::class,
        UsoProvedorIaEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FinaiDatabase : RoomDatabase() {
    abstract fun transacaoDao(): TransacaoDao
    abstract fun contaDao(): ContaDao
    abstract fun objetivoDao(): ObjetivoDao
    abstract fun dividaDao(): DividaDao
    abstract fun orcamentoCategoriaDao(): OrcamentoCategoriaDao
    abstract fun assinaturaDao(): AssinaturaDao
    abstract fun mensagemChatDao(): MensagemChatDao
    abstract fun usoProvedorIaDao(): UsoProvedorIaDao

    companion object {
        @Volatile private var instance: FinaiDatabase? = null

        fun get(context: Context): FinaiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FinaiDatabase::class.java,
                "finai.db",
            )
                // Pre-launch app, no real user data to preserve yet (planning.md §11) —
                // destructive migration is fine until Fase 6 (endurecimento e lançamento).
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
