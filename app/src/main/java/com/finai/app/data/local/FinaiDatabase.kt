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
import com.finai.app.data.local.dao.FaturaCartaoDao
import com.finai.app.data.local.dao.MensagemChatDao
import com.finai.app.data.local.dao.NotificacaoEnviadaDao
import com.finai.app.data.local.dao.ObjetivoDao
import com.finai.app.data.local.dao.OrcamentoCategoriaDao
import com.finai.app.data.local.dao.TransacaoDao
import com.finai.app.data.local.dao.UsoProvedorIaDao
import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
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
        FaturaCartaoEntity::class,
        ContaEntity::class,
        ObjetivoEntity::class,
        DividaEntity::class,
        OrcamentoCategoriaEntity::class,
        AssinaturaEntity::class,
        MensagemChatEntity::class,
        UsoProvedorIaEntity::class,
        NotificacaoEnviadaEntity::class,
    ],
    version = 10,
    // Fase 6: o schema de cada versão passa a ser exportado para
    // `app/schemas/` e versionado no git. É o que permite escrever (e testar)
    // uma migração sem adivinhar o DDL que o Room gerou na versão anterior —
    // era exatamente o que faltava quando as migrações 1→2 e 2→3 deixaram de
    // ser escritas.
    exportSchema = true,
)
abstract class FinaiDatabase : RoomDatabase() {
    abstract fun transacaoDao(): TransacaoDao
    abstract fun faturaCartaoDao(): FaturaCartaoDao
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

        /** Faturas passam a ser compromissos únicos, sem perder a data original das compras. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transacoes` ADD COLUMN `faturaId` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `faturas_cartao` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`contaId` INTEGER NOT NULL, `referencia` TEXT NOT NULL, " +
                        "`fechamento` INTEGER, `vencimento` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_faturas_cartao_contaId` ON `faturas_cartao` (`contaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transacoes_faturaId` ON `transacoes` (`faturaId`)")
            }
        }

        /** Parcelas de dívida passam a ter vencimento e total, para a Agenda projetar até a última. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dividas` ADD COLUMN `parcelasTotais` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `dividas` ADD COLUMN `proximoVencimento` INTEGER")
            }
        }

        /** Receita pode ser marcada como entrada extra (Linha do tempo do ano). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transacoes` ADD COLUMN `extra` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Chat em conversas separadas (`conversaId`, `contexto` do card que abriu
         * a conversa) e descrição do objetivo, que a leitura da IA usa. Mensagens
         * antigas ficam todas na conversa 0.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `mensagens_chat` ADD COLUMN `conversaId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `mensagens_chat` ADD COLUMN `contexto` TEXT")
                db.execSQL("ALTER TABLE `objetivos` ADD COLUMN `descricao` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Metas concluídas e dívidas quitadas ganham data, para o histórico nas telas. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `objetivos` ADD COLUMN `concluidoEm` INTEGER")
                db.execSQL("ALTER TABLE `dividas` ADD COLUMN `quitadaEm` INTEGER")
            }
        }

        /**
         * Fase 7, item 5 — renda principal explícita e dia combinado da dívida, numa migração só.
         *
         * `transacoes.rendaPrincipal`: marca as receitas que hoje já são tratadas como salário
         * pelo nome (`PayCycle.isSalary`: "salário"/"holerite", sem "13"/"décimo"/"férias", não
         * extra, fora de fatura). O `LIKE` do SQLite ignora caixa só em ASCII e não tira acento,
         * então as grafias com "á"/"Á" entram à parte. O filtro de "13" é mais largo que o da
         * regex do app; o que escapar continua reconhecido pelo nome em tempo de execução.
         * Se nenhuma casar, marca a maior receita recorrente, que é o
         * que o app já usava como salário deduzido. Assim o ciclo de quem atualiza não muda.
         *
         * `dividas.diaVencimento`: o dia do próximo vencimento. Se ele cai no último dia do mês,
         * vira 31 — era isso que a regra de fim de mês já fazia, e o comportamento se mantém.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transacoes` ADD COLUMN `rendaPrincipal` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE `transacoes` SET `rendaPrincipal` = 1 WHERE `tipo` = 'Receita' AND `extra` = 0 " +
                        "AND `faturaId` IS NULL AND `valorCentavos` > 0 " +
                        "AND (`descricao` LIKE '%salario%' OR `descricao` LIKE '%salário%' OR `descricao` LIKE '%salÁrio%' " +
                        "OR `descricao` LIKE '%holerite%') " +
                        "AND `descricao` NOT LIKE '%13%' AND `descricao` NOT LIKE '%decimo%' AND `descricao` NOT LIKE '%décimo%' " +
                        "AND `descricao` NOT LIKE '%ferias%' AND `descricao` NOT LIKE '%férias%'",
                )
                db.execSQL(
                    "UPDATE `transacoes` SET `rendaPrincipal` = 1 WHERE `id` = (" +
                        "SELECT `id` FROM `transacoes` WHERE `tipo` = 'Receita' AND `recorrente` = 1 AND `extra` = 0 " +
                        "AND `faturaId` IS NULL AND `valorCentavos` > 0 ORDER BY `valorCentavos` DESC, `data` ASC LIMIT 1) " +
                        "AND NOT EXISTS (SELECT 1 FROM `transacoes` WHERE `rendaPrincipal` = 1)",
                )
                db.execSQL("ALTER TABLE `dividas` ADD COLUMN `diaVencimento` INTEGER")
                db.execSQL(
                    "UPDATE `dividas` SET `diaVencimento` = CASE " +
                        "WHEN strftime('%d', `proximoVencimento` / 1000, 'unixepoch', 'localtime', '+1 day') = '01' THEN 31 " +
                        "ELSE CAST(strftime('%d', `proximoVencimento` / 1000, 'unixepoch', 'localtime') AS INTEGER) END " +
                        "WHERE `proximoVencimento` IS NOT NULL",
                )
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

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
