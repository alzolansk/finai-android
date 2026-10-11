package com.finai.app

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.ui.components.TransactionEntryScreen
import com.finai.app.ui.theme.FinaiTheme
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class EntryKeypadLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun narrowScreenKeepsSaveAndAllKeysVisible() = checkLayout(320, 640, 1f)
    @Test fun largeFontKeepsSaveAndAllKeysVisible() = checkLayout(320, 640, 1.5f)
    @Test fun darkScreenKeepsSaveAndAllKeysVisible() = checkLayout(320, 640, 1f, dark = true)
    @Test fun landscapeKeepsSaveAndAllKeysVisible() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        checkLayout(640, 360, 1f)
    }

    @After fun restorePortraitOrientation() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    private fun checkLayout(width: Int, height: Int, fontScale: Float, dark: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                FinaiTheme(darkTheme = dark) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        TransactionEntryScreen(visible = true, accounts = listOf("Carteira"),
                            onDismiss = {}, onSave = {}, onViewEntry = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Salvar gasto").assertIsDisplayed().assertIsNotEnabled()
        listOf("Gasto", "Receita", "Transferência").forEach { label ->
            val tab = compose.onNodeWithText(label).assertIsDisplayed()
            val tabHeight = tab.fetchSemanticsNode().boundsInRoot.height
            assertTrue("$label needs a 48 dp touch target", tabHeight >= 48f * compose.activity.resources.displayMetrics.density)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            val getLayout = tab.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!
            compose.runOnIdle {
                getLayout.invoke(layouts)
                assertTrue("$label must not be clipped at font scale $fontScale", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
            }
        }
        (0..9).forEach { compose.onNodeWithText(it.toString()).assertIsDisplayed() }
        compose.onNodeWithContentDescription("Dois zeros").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Apagar dígito").assertIsDisplayed().performClick()
        val save = compose.onNodeWithText("Salvar gasto").fetchSemanticsNode().boundsInRoot
        val topKey = compose.onNodeWithContentDescription("1").fetchSemanticsNode().boundsInRoot
        if (width > height) assertTrue("Save must sit beside the keypad", save.right <= topKey.left)
        else assertTrue("Save must sit above the keypad", save.bottom <= topKey.top)
        compose.onNodeWithText("Selecionar categoria").performScrollTo()
        compose.onNodeWithText("Transferência").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText("Salvar transferência").assertIsDisplayed()
        compose.onNodeWithText("Gasto").performClick()
        compose.onNodeWithContentDescription("Valor: R$ 0,00").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "entry-${width}-${height}-${fontScale}-${dark}.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
