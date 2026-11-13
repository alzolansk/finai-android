package com.finai.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.*
import com.finai.app.data.notifications.InboxLinks
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.prefs.ThemeMode
import com.finai.app.domain.toEpochMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Execução dirigida no emulador com cenário debug. Não roda na suíte normal. */
class InboxV2VisualTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shot(name: String) {
        compose.waitForIdle()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = InstrumentationRegistry.getArguments().getString("qaName")
        val file = File(ctx.getExternalFilesDir(null), "v2-qa/fase-3/$prefix-$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun inboxGroupsSaveUndoPayDismissAndNotificationRoute() {
        assumeNotNull(InstrumentationRegistry.getArguments().getString("qaName"))
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = FinaiDatabase.get(ctx)
        val prefs = FinaiPreferences(ctx)
        val today = LocalDate.now()
        runBlocking { prefs.setThemeMode(ThemeMode.DARK) }
        compose.waitUntil(15000) { compose.onAllNodes(hasContentDescription("Caixa", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // Mesmo pedido consumido pelo PendingIntent de uma notificação financeira.
        ctx.startActivity(android.content.Intent(ctx, MainActivity::class.java).apply {
            action = InboxLinks.ACTION_OPEN
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        compose.waitUntil(15000) { compose.onAllNodesWithText("Caixa").fetchSemanticsNodes().isNotEmpty() }
        val suggestionId = runBlocking {
            db.transacaoDao().upsert(TransacaoEntity(id = 99001, data = today.toEpochMillis(), descricao = "Cinema QA",
                valorCentavos = 18000, categoria = "Lazer", contaOrigem = "Inter", recorrente = false, origem = "manual"))
            db.orcamentoCategoriaDao().upsert(OrcamentoCategoriaEntity("Lazer", 20000, today.toString().take(7)))
            db.sugestaoLancamentoDao().insert(SugestaoLancamentoEntity(origem = "simulacao", pacote = "com.finai.qa", appNome = "Teste do FinAI",
                capturadoEm = System.currentTimeMillis(), atualizadoEm = System.currentTimeMillis(), chaveSistema = "inbox-qa", assinatura = "inbox-qa",
                tipo = "DebitPurchase", valorCentavos = 4590, valoresEncontrados = "4590", descricao = "Mercado QA", categoria = "Alimentação",
                data = today.toEpochMillis(), dataExplicita = true, parcelas = null, valorParcelaCentavos = null, cartaoFinal = null,
                contaSugerida = "Inter", estado = "pendente", possivelRepeticao = false, avisadaEm = null, resolvidaEm = null,
                lancamentoIds = "", textoOriginal = null, testeId = null))
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("PARA CONFIRMAR", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mercado QA").assertExists()
        shot("caixa-dark-topo")
        compose.onNodeWithText("Salvar", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(15000) { runBlocking { db.sugestaoLancamentoDao().get(suggestionId)?.estado == "confirmada" } }
        compose.onNodeWithText("Lançamento salvo.").assertExists()
        compose.onNodeWithText("Desfazer").performScrollTo().performClick()
        compose.waitUntil(15000) { runBlocking { db.sugestaoLancamentoDao().get(suggestionId)?.estado == "pendente" } }
        compose.onNodeWithText("Ignorar").performScrollTo().performClick()
        compose.waitUntil(15000) { runBlocking { db.sugestaoLancamentoDao().get(suggestionId)?.estado == "ignorada" } }
        compose.onNodeWithText("Marcar paga").performScrollTo().performClick()
        compose.waitUntil(15000) { runBlocking { db.contaDao().getAll().firstOrNull { it.nome == "Fatura Nubank" }?.status == "pago" } }
        compose.onNodeWithText("Lazer perto do limite").performScrollTo().assertExists()
        shot("caixa-dark-acompanhar")
        // Dispensar só o limite, entre outros avisos de metas.
        compose.onNode(hasText("Dispensar") and hasAnyAncestor(hasTestTag("inbox-limite-Lazer-${today.toString().take(7)}")))
            .performScrollTo().performClick()
        compose.waitUntil(15000) { runBlocking { db.avisoDao().getAll().any { it.id.startsWith("limite-Lazer-") && it.estado == "dispensado" } } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Desfazer tudo"))
        compose.onNodeWithText("Desfazer tudo").assertExists()
        shot("caixa-dark-dispensados")
        runBlocking { prefs.setThemeMode(ThemeMode.LIGHT) }
        shot("caixa-light-dispensados")
        // Recriar a Activity: o estado continua no Room, o ponto visto não reaparece.
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodes(hasContentDescription("Caixa", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Caixa").performClick()
        compose.onNodeWithText("Lazer perto do limite").assertDoesNotExist()
        shot("caixa-reaberta")
        repeat(8) {
            val found = runCatching { compose.onNode(hasScrollAction()).performScrollToNode(hasText("Dispensar")) }.isSuccess
            if (found) {
                compose.onAllNodesWithText("Dispensar").onFirst().performClick()
                compose.waitForIdle()
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tudo em dia. Nenhuma decisão pendente."))
        compose.onNodeWithText("Tudo em dia. Nenhuma decisão pendente.").performScrollTo().assertExists()
        shot("caixa-vazia-light")
        runBlocking { prefs.setThemeMode(ThemeMode.DARK) }
        shot("caixa-vazia-dark")
    }
}
