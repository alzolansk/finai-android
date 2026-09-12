package com.finai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.finai.app.data.local.dao.AssinaturaDao
import com.finai.app.data.local.dao.ContaDao
import com.finai.app.data.local.dao.DividaDao
import com.finai.app.data.local.dao.MensagemChatDao
import com.finai.app.data.local.dao.NotificacaoEnviadaDao
import com.finai.app.data.local.dao.ObjetivoDao
import com.finai.app.data.local.dao.OrcamentoCategoriaDao
import com.finai.app.data.local.dao.TransacaoDao
import com.finai.app.data.local.dao.UsoProvedorIaDao
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.local.entity.NotificacaoEnviadaEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.data.local.entity.UsoProvedorIaEntity

/**
 * The app's only local database — everything financial lives here, on-device,
 * per planning.md §4's privacy-first requirement.
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
        NotificacaoEnviadaEntity::class,
    ],
    version = 4,
    // Fase 6: o schema de cada versão passa a ser exportado para
    // `app/schemas/` e versionado no git. É o que permite escrever (e testar)
    // uma migração sem adivinhar o DDL que o Room gerou na versão anterior —
    // era exatamente o que faltava quando as migrações 1→2 e 2→3 deixaram de
    // ser escritas.
    exportSchema = true,
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
    abstract fun notificacaoEnviadaDao(): NotificacaoEnviadaDao

    companion object {

        /**
         * Fase 0 → Fase 1: `DividaEntity` ganhou `valorOriginalCentavos`, que é
         * o que `DebtCalculator` usa para a barra de progresso de cada dívida.
         *
         * `DEFAULT 0` existe só para o `ALTER TABLE` poder criar a coluna como
         * `NOT NULL` numa tabela que já tem linhas; a entidade não declara
         * `defaultValue`, e o Room ignora um default do banco quando a entidade
         * não declara nenhum — então isto não quebra a validação de schema.
         * Uma dívida migrada fica com valor original 0 e progresso 0%, que é
         * honesto: o dado não existia antes para ser recuperado.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dividas` ADD COLUMN `valorOriginalCentavos` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Fase 5: tabela de deduplicação das notificações proativas ([NotificacaoEnviadaEntity]). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notificacoes_enviadas` " +
                        "(`chave` TEXT NOT NULL, `ultimoEnvio` TEXT NOT NULL, PRIMARY KEY(`chave`))",
                )
            }
        }

        /** Refatoração do lançamento manual: `TransacaoEntity.tipo` (Gasto/Receita/Transferência). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transacoes` ADD COLUMN `tipo` TEXT NOT NULL DEFAULT 'Gasto'")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        @Volatile private var instance: FinaiDatabase? = null

        fun get(context: Context): FinaiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FinaiDatabase::class.java,
                DATABASE_NAME,
            )
                // Fase 6: `fallbackToDestructiveMigration()` saiu daqui. Até a
                // Fase 5 o banco não tinha dado real a perder e apagar tudo numa
                // versão nova era aceitável; a partir do lançamento apagar o
                // histórico financeiro do usuário num update seria a pior falha
                // possível deste app — ele não tem backup em nuvem nem export
                // (planning.md §11). Com as três migrações acima todo caminho
                // 1→4 está coberto; uma versão futura sem migração agora falha
                // alto no build de teste, em vez de silenciosamente apagar dados
                // no aparelho do usuário.
                .addMigrations(*MIGRATIONS)
                // A única exceção: downgrade (o usuário instalar um APK mais
                // antigo por cima). Não há como "desmigrar" um schema, e
                // recriar o banco é melhor do que o app não abrir.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build().also { instance = it }
        }

        const val DATABASE_NAME = "finai.db"
    }
}
