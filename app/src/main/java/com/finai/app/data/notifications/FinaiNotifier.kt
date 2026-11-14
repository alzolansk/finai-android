package com.finai.app.data.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Permissão e limpeza das notificações. A entrega com desenho e botões fica em
 * [RichNotifier] (que usa a checagem de permissão daqui); sem permissão
 * (Android 13+), a rotina do Worker continua rodando normalmente, só a
 * notificação em si não aparece.
 */
class FinaiNotifier(private val context: Context) {

    /** Usado por "Apagar todos os dados": descarta notificações já entregues sobre dados que não existem mais. */
    fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()

    /** O lote entregue pode conter o aviso que acabou de ser dispensado/resolvido. */
    fun cancelFinanceAlerts() {
        NotificationManagerCompat.from(context).cancel(ALERTS_NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(COACH_NOTIFICATION_ID)
    }

    /** True se o app tem permissão para mostrar notificações agora — usado só para diagnóstico na tela de IA. */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationManagerCompat.from(context).areNotificationsEnabled()
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ALERTS_NOTIFICATION_ID = 1001
        const val COACH_NOTIFICATION_ID = 1002
    }
}
