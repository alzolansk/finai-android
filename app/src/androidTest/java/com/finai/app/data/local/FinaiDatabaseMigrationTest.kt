package com.finai.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.finai.app.data.local.entity.NotificacaoEnviadaEntity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O teste que a Fase 6 exigia antes de tirar `fallbackToDestructiveMigration()`
 * do [FinaiDatabase] (planning.md §9 — endurecimento).
 *
 * Até aqui, subir a versão do banco apagava o histórico financeiro do usuário
 * em silêncio, e as migrações 1→2 e 2→3 nunca chegaram a ser escritas. Agora
 * elas existem — e este teste é o que garante que existem *corretas*: ele cria
 * um banco na versão 1 com dados dentro, abre pelo Room de verdade (o que roda
 * 1→2→3→4 em sequência e depois valida o schema resultante contra as entidades
 * atuais) e confere que os dados continuam lá.
 *
 * Não usa `MigrationTestHelper` de propósito: ele precisa do JSON de schema de
 * cada versão antiga, e `exportSchema` só foi ligado agora — as versões 1 a 3
 * do app foram publicadas sem deixar esse registro. Criar a v1 com o DDL
 * explícito abaixo testa exatamente o mesmo caminho e não depende de arqueologia
 * no histórico do git. Da versão 4 em diante o JSON exportado existe, e uma
 * migração futura pode ser testada pelos dois caminhos.
 *
 * Roda em aparelho/emulador: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class FinaiDatabaseMigrationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile: File get() = context.getDatabasePath(TEST_DB)

    @Before
    fun limparAntes() = apagarBancoDeTeste()

    @After
    fun limparDepois() = apagarBancoDeTeste()

    @Test
    fun migracaoDaVersao1AteAAtualPreservaOsDadosDoUsuario() {
        criarBancoVersao1()

        val db = abrirComRoom()
        try {
            runBlocking {
                val dividas = db.dividaDao().observeAll().first()
                assertEquals(1, dividas.size)
                assertEquals("Cartão Nubank", dividas[0].nome)
                assertEquals(250_000L, dividas[0].valorAbertoCentavos)
                // Coluna criada pela MIGRATION_1_2: a dívida é anterior a ela, então
                // o valor original é 0 (honesto — o dado não existia para recuperar).
                assertEquals(0L, dividas[0].valorOriginalCentavos)
                // Colunas da MIGRATION_5_6: sem total nem vencimento conhecidos.
                assertEquals(0, dividas[0].parcelasTotais)
                assertNull(dividas[0].proximoVencimento)

                val transacoes = db.transacaoDao().observeAll().first()
                assertEquals(1, transacoes.size)
                assertEquals("Mercado", transacoes[0].descricao)
                assertEquals(18_990L, transacoes[0].valorCentavos)
                // Coluna da MIGRATION_6_7: nada antigo vira entrada extra sozinho.
                assertEquals(false, transacoes[0].extra)
                // Coluna criada pela MIGRATION_3_4, com o default que a entidade declara.
                assertEquals("Gasto", transacoes[0].tipo)

                val contas = db.contaDao().observeAll().first()
                assertEquals(1, contas.size)
                assertEquals("Aluguel", contas[0].nome)

                val mensagens = db.mensagemChatDao().observeAll().first()
                assertEquals(1, mensagens.size)
                assertEquals("Quanto posso gastar hoje?", mensagens[0].texto)

                // Tabela criada pela MIGRATION_2_3: existe e está utilizável.
                db.notificacaoEnviadaDao().upsert(NotificacaoEnviadaEntity("alerta-teste", "2026-09-12"))
                assertEquals("2026-09-12", db.notificacaoEnviadaDao().find("alerta-teste")?.ultimoEnvio)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun abrirDuasVezesSeguidasNaoRefazMigracaoNemPerdeDado() {
        criarBancoVersao1()

        abrirComRoom().use { runBlocking { assertEquals(1, it.dividaDao().observeAll().first().size) } }
        // Segunda abertura: o banco já está na versão atual. Se alguma migração
        // fosse re-executada (ou o fallback destrutivo voltasse), isto falharia.
        abrirComRoom().use { runBlocking { assertEquals(1, it.dividaDao().observeAll().first().size) } }
    }

    private fun abrirComRoom(): FinaiDatabase =
        Room.databaseBuilder(context, FinaiDatabase::class.java, TEST_DB)
            .addMigrations(*FinaiDatabase.MIGRATIONS)
            .build()

    private inline fun FinaiDatabase.use(block: (FinaiDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private fun apagarBancoDeTeste() {
        SQLiteDatabase.deleteDatabase(dbFile)
    }

    /**
     * DDL da versão 1 do banco (Fase 0), reproduzido exatamente como o Room o
     * gerava então: sem `dividas.valorOriginalCentavos` (v2), sem a tabela
     * `notificacoes_enviadas` (v3) e sem `transacoes.tipo` (v4).
     */
    private fun criarBancoVersao1() {
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transacoes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`data` INTEGER NOT NULL, `descricao` TEXT NOT NULL, `valorCentavos` INTEGER NOT NULL, " +
                    "`categoria` TEXT NOT NULL, `contaOrigem` TEXT NOT NULL, `recorrente` INTEGER NOT NULL, " +
                    "`origem` TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `contas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nome` TEXT NOT NULL, `valorCentavos` INTEGER NOT NULL, `vencimento` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, `tipo` TEXT NOT NULL, `recorrente` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `objetivos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`tipo` TEXT NOT NULL, `nome` TEXT NOT NULL, `valorAlvoCentavos` INTEGER NOT NULL, " +
                    "`valorGuardadoCentavos` INTEGER NOT NULL, `prazo` INTEGER NOT NULL, `prioridade` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dividas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nome` TEXT NOT NULL, `valorAbertoCentavos` INTEGER NOT NULL, " +
                    "`taxaJurosMensalBasisPoints` INTEGER NOT NULL, `parcelasRestantes` INTEGER NOT NULL, " +
                    "`valorParcelaCentavos` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `orcamento_categorias` (`categoria` TEXT NOT NULL, " +
                    "`limiteMensalCentavos` INTEGER NOT NULL, `mesReferencia` TEXT NOT NULL, " +
                    "PRIMARY KEY(`categoria`, `mesReferencia`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `assinaturas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nome` TEXT NOT NULL, `valorCentavos` INTEGER NOT NULL, `ultimoUso` INTEGER, `status` TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mensagens_chat` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`papel` TEXT NOT NULL, `texto` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `uso_provedor_ia` (`provedor` TEXT NOT NULL, `data` TEXT NOT NULL, " +
                    "`quantidadeChamadasHoje` INTEGER NOT NULL, PRIMARY KEY(`provedor`, `data`))",
            )

            db.execSQL(
                "INSERT INTO dividas (nome, valorAbertoCentavos, taxaJurosMensalBasisPoints, parcelasRestantes, " +
                    "valorParcelaCentavos) VALUES ('Cartão Nubank', 250000, 1350, 8, 35000)",
            )
            db.execSQL(
                "INSERT INTO transacoes (data, descricao, valorCentavos, categoria, contaOrigem, recorrente, origem) " +
                    "VALUES (1757635200000, 'Mercado', 18990, 'Alimentação', 'Carteira', 0, 'manual')",
            )
            db.execSQL(
                "INSERT INTO contas (nome, valorCentavos, vencimento, status, tipo, recorrente) " +
                    "VALUES ('Aluguel', 180000, 1757635200000, 'pendente', 'a_pagar', 1)",
            )
            db.execSQL(
                "INSERT INTO mensagens_chat (papel, texto, timestamp) " +
                    "VALUES ('usuario', 'Quanto posso gastar hoje?', 1757635200000)",
            )

            db.version = 1
            assertTrue("banco de teste deve existir antes da migração", dbFile.exists())
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "finai_migration_test.db"
    }
}
