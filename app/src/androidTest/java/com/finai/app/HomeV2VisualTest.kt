package com.finai.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.prefs.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Smoke no app inteiro com o cenário debug carregado; execução dirigida, separada da suíte. */
class HomeV2VisualTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shot(name: String) {
        compose.waitForIdle()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = InstrumentationRegistry.getArguments().getString("qaName") ?: "390"
        val file = File(target.getExternalFilesDir(null), "v2-qa/$prefix-$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tapBill() {
        compose.onNode(hasContentDescription("Fatura Nubank", substring = true) and hasClickAction()).performScrollTo()
        // Evita as áreas cobertas pelo cabeçalho e pela barra flutuantes.
        compose.onRoot().performTouchInput { swipeUp(startY = height * .75f, endY = height * .45f) }
        compose.onNode(hasContentDescription("Fatura Nubank", substring = true) and hasClickAction()).performClick()
    }

    private fun top() {
        compose.onRoot().performTouchInput { swipeDown(startY = height * .25f, endY = height * .85f) }
        compose.waitForIdle()
    }

    @Test fun todayExplanationAndPaidState() {
        org.junit.Assume.assumeNotNull(InstrumentationRegistry.getArguments().getString("qaName"))
        val prefs = FinaiPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { prefs.setThemeMode(ThemeMode.DARK) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("R$ 1.220", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("LIVRE ATÉ O ADIANTAMENTO · 20/11").assertExists()
        shot("hoje-dark")
        tapBill()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pago").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pago").assertExists()
        shot("hoje-pago-linha")
        top()
        shot("hoje-pago")
        tapBill()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pago").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Pago").assertDoesNotExist()
        top()
        compose.onNodeWithText("Por quê ›").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("De onde vem o livre").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("De onde vem o livre").assertExists()
        shot("livre-dark")
        compose.onNodeWithText("− Vai sair", substring = true).performClick()
        compose.onNodeWithText("Picpay · parcela 5 de 12").performScrollTo().assertExists()
        shot("livre-movimentos")
        compose.onNodeWithText("Conferir com o banco").performScrollTo().assertExists()
        compose.onNodeWithText("Conferir", useUnmergedTree = true).performScrollTo()
        shot("livre-banco")
        compose.onNodeWithContentDescription("Voltar").performClick()
        runBlocking { prefs.setThemeMode(ThemeMode.LIGHT) }
        shot("hoje-light")
        compose.onNodeWithText("Por quê ›").performScrollTo().performClick()
        shot("livre-light")
        compose.onNodeWithContentDescription("Voltar").performClick()
        runBlocking { prefs.setThemeMode(ThemeMode.DARK) }
        compose.onNodeWithContentDescription("Ações rápidas").performClick()
        compose.onNodeWithText("Lançar gasto").performClick()
        shot("categorias-inicio")
        compose.onNode(hasContentDescription("Categoria") and hasScrollAction()).performScrollToNode(hasText("Outros"))
        compose.onNodeWithText("Assinaturas").assertExists()
        shot("categorias-fim")
    }
}
