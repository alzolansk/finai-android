package com.finai.app.data.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.finai.app.MainActivity
import com.finai.app.R

/**
 * Camada de "entrega" da Fase 5 (planning.md §9) — o único lugar que fala com
 * `NotificationManagerCompat`. Não decide nada: recebe o [NotificationContent]
 * já pronto (de [NotificationContentBuilder]) e um id de canal/notificação, e
 * só posta se a permissão de notificação (Android 13+) estiver concedida —
 * sem ela, a rotina do Worker continua rodando normalmente (recalcula e
 * marca o evento como "enviado" só quando a notificação realmente sai; ver
 * `FinanceCheckWorker`), só a notificação em si não aparece.
 */
class FinaiNotifier(private val context: Context) {

    fun notifyAlerts(content: NotificationContent, notificationId: Int) =
        post(FinaiNotificationChannels.ALERTS_CHANNEL_ID, notificationId, content)

    fun notifyCoach(content: NotificationContent, notificationId: Int) =
        post(FinaiNotificationChannels.COACH_CHANNEL_ID, notificationId, content)

    /** Usado por "Apagar todos os dados": descarta notificações já entregues sobre dados que não existem mais. */
    fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()

    /** True se o app tem permissão para mostrar notificações agora — usado só para diagnóstico na tela de IA. */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationManagerCompat.from(context).areNotificationsEnabled()
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun post(channelId: String, notificationId: Int, content: NotificationContent) {
        if (!hasNotificationPermission()) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_finai)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permissão revogada entre a checagem acima e o notify() — degrada silenciosamente,
            // consistente com planning.md §4 (a ausência de notificação nunca derruba o app).
        }
    }

    companion object {
        const val ALERTS_NOTIFICATION_ID = 1001
        const val COACH_NOTIFICATION_ID = 1002
    }
}
