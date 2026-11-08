package com.finai.app.data.capture

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.finai.app.MainActivity
import com.finai.app.R
import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import com.finai.app.data.notifications.FinaiNotificationChannels
import com.finai.app.data.notifications.FinaiNotifier
import com.finai.app.domain.brl2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * As duas notificações que o FinAI posta nesta funcionalidade, e o caminho do toque de volta
 * para o app. Nunca abre uma tela por cima de outro app: só notificação comum, sem
 * full-screen intent nem sobreposição.
 */
object CaptureNotifications {
    const val ACTION_OPEN_SUGGESTION = "com.finai.app.action.OPEN_SUGGESTION"
    const val EXTRA_SUGGESTION_ID = "com.finai.app.extra.SUGGESTION_ID"
    /** Marcador das notificações "Teste do FinAI". Sem ele, o listener ignora o que vem do FinAI. */
    const val EXTRA_TEST_ID = "com.finai.app.extra.TEST_ID"
    const val TEST_SUBTEXT = "Teste do FinAI"
    private const val SUGGESTION_GROUP = "finai_sugestoes"
    private const val SUGGESTION_BASE_ID = 40_000

    private fun suggestionNotificationId(id: Long) = SUGGESTION_BASE_ID + (id % 100_000).toInt()

    /** "Movimentação encontrada", quando o FinAI não está na tela. Tocar abre exatamente esta sugestão. */
    fun notifySuggestion(context: Context, s: SugestaoLancamentoEntity) {
        if (!FinaiNotifier(context).hasNotificationPermission()) return
        val open = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SUGGESTION
            putExtra(EXTRA_SUGGESTION_ID, s.id)
            // App aberto: volta à mesma tela (onNewIntent); fechado: abre direto na sugestão.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, suggestionNotificationId(s.id), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val value = s.valorCentavos?.let(::brl2) ?: "Valor a confirmar"
        val detail = listOf(value, s.descricao).filter { it.isNotBlank() }.joinToString(" · ")
        val prefix = if (s.isTest) "Teste do FinAI · " else ""
        val title = "${prefix}Movimentação encontrada"
        // Na tela bloqueada aparece só o título: valor e estabelecimento ficam para depois de desbloquear.
        val public = NotificationCompat.Builder(context, FinaiNotificationChannels.CAPTURE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_finai)
            .setContentTitle(title)
            .setContentText("Toque para conferir")
            .build()
        val notification = NotificationCompat.Builder(context, FinaiNotificationChannels.CAPTURE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_finai)
            .setContentTitle(title)
            .setContentText("$detail — toque para conferir")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\nNada foi lançado ainda: confira e salve no FinAI."))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setGroup(SUGGESTION_GROUP)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(suggestionNotificationId(s.id), notification)
        } catch (_: SecurityException) {
            // Permissão revogada no meio: a sugestão continua em "Para confirmar" dentro do app.
        }
    }

    fun cancelSuggestion(context: Context, id: Long) =
        NotificationManagerCompat.from(context).cancel(suggestionNotificationId(id))

    /**
     * Uma notificação real "Teste do FinAI". Não finge ser banco: sai com o nome e o ícone do
     * FinAI, no canal de testes, com o subtítulo "Teste do FinAI". Devolve false se o Android não
     * deixou publicar (notificações do FinAI bloqueadas).
     */
    fun postTest(context: Context, notificationId: Int, testId: Long, title: String, text: String): Boolean {
        if (!FinaiNotifier(context).hasNotificationPermission()) return false
        val manager = NotificationManagerCompat.from(context)
        val channel = manager.getNotificationChannel(FinaiNotificationChannels.TEST_CHANNEL_ID)
        if (channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) return false
        val open = PendingIntent.getActivity(
            context, notificationId, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, FinaiNotificationChannels.TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_finai)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(TEST_SUBTEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addExtras(android.os.Bundle().apply { putLong(EXTRA_TEST_ID, testId) })
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return try { manager.notify(notificationId, notification); true } catch (_: SecurityException) { false }
    }

    fun cancelTests(context: Context, ids: Collection<Int>) {
        val manager = NotificationManagerCompat.from(context)
        ids.forEach { manager.cancel(it) }
    }

    fun listenerComponent(context: Context) = ComponentName(context, FinanceNotificationListener::class.java)
}

/** O app está visível? Decide entre o quadro dentro do app e a notificação "Movimentação encontrada". */
object AppVisibility {
    @Volatile private var started = 0
    val isVisible: Boolean get() = started > 0
    fun onStart() { started++ }
    fun onStop() { started = (started - 1).coerceAtLeast(0) }
}

/** Pedidos para abrir uma sugestão (toque na notificação), lidos pelo `FinaiApp`. */
object CaptureLinks {
    private val _open = MutableStateFlow<Long?>(null)
    val open: StateFlow<Long?> = _open.asStateFlow()
    fun request(id: Long) { _open.value = id }
    fun consume() { _open.value = null }

    /** Se o listener está ligado agora (o Android o conectou). Só informativo: a autorização é lida do sistema. */
    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()
    fun setListenerConnected(connected: Boolean) { _listenerConnected.value = connected }
}
