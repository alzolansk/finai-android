package com.finai.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.data.prefs.ThemeMode
import com.finai.app.ui.theme.FinaiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    // Resultado ignorado de propósito: se negada, o app segue funcionando por completo
    // (planning.md §4) — FinaiNotifier só deixa de postar a notificação em si.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = FinaiPreferences(this)
        // Lido antes do primeiro quadro (arquivo pequeno do DataStore): sem isso, quem escolheu
        // o tema escuro via um quadro claro piscar a cada abertura.
        val initialMode = runCatching { runBlocking { prefs.themeMode.first() } }.getOrDefault(ThemeMode.LIGHT)
        applySystemBars(dark = isDark(initialMode, systemDark = isSystemNightMode()))
        requestNotificationPermissionIfNeeded()
        setContent {
            val mode by prefs.themeMode.collectAsState(initial = initialMode)
            val dark = isDark(mode, systemDark = isSystemInDarkTheme())
            LaunchedEffect(dark) { applySystemBars(dark) }
            FinaiTheme(darkTheme = dark) {
                FinaiApp()
            }
        }
    }

    private fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    private fun isSystemNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * Ícones da barra de status/navegação seguem o tema *do app*, não o do sistema. O padrão
     * (SystemBarStyle.auto) seguia o modo escuro do Android e pintava relógio/bateria de branco
     * sobre o fundo claro — ficavam invisíveis. O fundo da janela acompanha para a abertura e a
     * troca de tela não piscarem branco no tema escuro.
     */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF0E0E10.toInt() else 0xFFFAFAFA.toInt()))
    }

    /** POST_NOTIFICATIONS só existe (e só precisa de permissão em runtime) a partir do Android 13. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
