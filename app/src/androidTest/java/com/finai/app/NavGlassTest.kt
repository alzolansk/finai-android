package com.finai.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.finai.app.navigation.FinaiDestination
import com.finai.app.ui.components.*
import com.finai.app.ui.theme.FinaiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated UI: no database, seeding or changes to the user's preferences. */
class NavGlassTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun navigationCollapseThemesAndTouch() {
        val current = mutableStateOf(FinaiDestination.Home)
        val collapsed = mutableStateOf(false)
        val dark = mutableStateOf(false)
        var adds = 0
        compose.setContent {
            FinaiTheme(darkTheme = dark.value) {
                val backdrop = rememberGlassBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                        LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                            items(60) { i ->
                                Text("Conteúdo de contraste $i", Modifier.fillMaxWidth().height(64.dp)
                                    .background(if (i % 2 == 0) Color(0xFF047857) else if (dark.value) Color(0xFF18181B) else Color.White))
                            }
                        }
                        NavBarBottomScrim(true, Modifier.align(Alignment.BottomCenter))
                    }
                    FinaiBottomNav(current.value, false, collapsed.value, backdrop,
                        { current.value = it; collapsed.value = false }, { collapsed.value = false }, { adds++ },
                        Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.waitForIdle()
        save("light-expanded")
        compose.onNodeWithContentDescription("Agenda").performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithContentDescription("Objetivos").performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithContentDescription("Dívidas").performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithContentDescription("Ações rápidas").performTouchInput { click() }
        compose.runOnIdle { assertTrue(adds == 1); collapsed.value = true; dark.value = true }
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        save("dark-collapsed-scrolled")
        val tab = compose.onNodeWithContentDescription("Dívidas").assertIsSelected()
        assertTrue(tab.fetchSemanticsNode().boundsInRoot.height >= 48f * compose.activity.resources.displayMetrics.density)
        tab.performTouchInput { click() }
        compose.runOnIdle { assertTrue(!collapsed.value) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Agenda").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(220)
        save("touch-light")
        compose.onNodeWithContentDescription("Agenda").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        save("dark-expanded-released")
    }

    @Test fun blurWithoutAgslAndOpaqueFallback() {
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            val emptyBackdrop = rememberGlassBackdrop()
            val blur = rememberGlassSurface(backdrop)
            val fallback = rememberGlassSurface(emptyBackdrop)
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().glassSource(backdrop).background(Color(0xFF047857)))
                Column(Modifier.align(Alignment.Center).padding(12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.fillMaxWidth().height(64.dp).glassPosition(blur)
                        .drawBehind { drawGlass(backdrop, blur, GlassMaterial.Nav.copy(refract = false)) }.navGlassFinish(false))
                    Box(Modifier.fillMaxWidth().height(52.dp).glassPosition(fallback)
                        .drawBehind { drawGlass(emptyBackdrop, fallback, GlassMaterial.Nav) }.navGlassFinish(false))
                }
            }
        }
        compose.waitForIdle()
        save("no-agsl-and-fallback")
    }

    @Test fun refractionChangesEdgesButKeepsCenterStableAfterResize() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val refract = mutableStateOf(false)
        val collapsed = mutableStateOf(false)
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            val surface = rememberGlassSurface(backdrop)
            val material = GlassMaterial.Nav.copy(blurRadius = 1.dp, tint = Color.Transparent, refract = refract.value)
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().glassSource(backdrop).drawBehind {
                    val step = 8.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawRect(if ((y / step).toInt() % 2 == 0) Color.White else Color.Black,
                            Offset(0f, y), Size(size.width, step))
                        y += step
                    }
                })
                Box(Modifier.align(Alignment.Center).width(if (collapsed.value) 280.dp else 360.dp)
                    .height(if (collapsed.value) 52.dp else 64.dp).testTag("lens").glassPosition(surface)
                    .drawBehind { drawGlass(backdrop, surface, material) })
            }
        }
        for (small in listOf(false, true)) {
            compose.runOnIdle { collapsed.value = small; refract.value = false }
            val plain = compose.onNodeWithTag("lens").captureToImage().asAndroidBitmap()
            compose.runOnIdle { refract.value = true }
            val lens = compose.onNodeWithTag("lens").captureToImage().asAndroidBitmap()
            val x = lens.width / 2
            val center = lens.height / 2
            assertTrue("The central sample must stay stable", plain.getPixel(x, center) == lens.getPixel(x, center))
            val changed = (2 until lens.height / 4).count { y -> plain.getPixel(x, y) != lens.getPixel(x, y) }
            assertTrue("The curved rim must refract captured content at both sizes", changed > 2)
        }
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(compose.activity.getExternalFilesDir(null), "glass-$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
