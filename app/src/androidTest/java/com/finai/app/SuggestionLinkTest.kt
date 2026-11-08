package com.finai.app

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.capture.CaptureNotifications
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import com.finai.app.domain.toEpochMillis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Tocar em "Movimentação encontrada" abre a sugestão certa: com o app fechado (intent inicial),
 * com o app aberto (onNewIntent, SINGLE_TOP) e quando ela já foi resolvida.
 */
class SuggestionLinkTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = FinaiDatabase.get(context).sugestaoLancamentoDao()
    private val created = mutableListOf<Long>()

    private fun insert(descricao: String, valor: Long, estado: String = "pendente"): Long = runBlocking {
        val now = System.currentTimeMillis()
        dao.insert(SugestaoLancamentoEntity(
            origem = "teste", pacote = "com.finai.app", appNome = "FinAI (teste)", capturadoEm = now, atualizadoEm = now,
            chaveSistema = "link-test-$descricao-$now", assinatura = "sig-$descricao", tipo = "DebitPurchase", valorCentavos = valor,
            valoresEncontrados = "", descricao = descricao, categoria = "Alimentação", data = LocalDate.now().toEpochMillis(),
            dataExplicita = false, parcelas = null, valorParcelaCentavos = null, cartaoFinal = null, contaSugerida = null,
            estado = estado, possivelRepeticao = false, avisadaEm = now, resolvidaEm = if (estado == "pendente") null else now,
            lancamentoIds = "", textoOriginal = null, testeId = null,
        )).also { created += it }
    }

    private fun intentFor(id: Long) = Intent(context, MainActivity::class.java).apply {
        action = CaptureNotifications.ACTION_OPEN_SUGGESTION
        putExtra(CaptureNotifications.EXTRA_SUGGESTION_ID, id)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    @After fun clean() = runBlocking { created.forEach { id -> dao.get(id)?.let { dao.update(it.copy(estado = "ignorada", resolvidaEm = 0)) } }; dao.deleteResolvedBefore(1) }

    @Test fun opensFromColdStartAndWhileOpen() {
        val first = insert("Padaria Link", 1850)
        val second = insert("Cafeteria Link", 990)
        ActivityScenario.launch<MainActivity>(intentFor(first)).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Padaria Link").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Movimentação encontrada").assertExists()
            // App já aberto: o toque chega por onNewIntent e troca para a outra sugestão.
            context.startActivity(intentFor(second))
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Cafeteria Link").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun resolvedSuggestionShowsItsState() {
        val done = insert("Mercado Resolvido", 4290, estado = "confirmada")
        ActivityScenario.launch<MainActivity>(intentFor(done)).use {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Esta movimentação já foi salva", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test fun deletedSuggestionSaysItIsGone() {
        ActivityScenario.launch<MainActivity>(intentFor(987_654_321L)).use {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("não existe mais", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
