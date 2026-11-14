package com.finai.app.data.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.finai.app.MainActivity
import com.finai.app.R
import com.finai.app.data.capture.CaptureNotifications
import com.finai.app.domain.notify.NotifyAction
import com.finai.app.domain.notify.NotifyButton
import com.finai.app.domain.notify.NotifyLook
import com.finai.app.domain.notify.NotifySpec

/**
 * As notificações do FinAI com desenho próprio: selo colorido, uma frase, o valor em destaque,
 * barra de progresso e **botões que agem ali mesmo** (pagar, adiar, salvar, "nada hoje").
 *
 * Só desenha e entrega. O que escrever e quais botões mostrar vem pronto do
 * [com.finai.app.domain.notify.NotificationPlanner]; o que cada botão faz está no
 * [NotificationActionReceiver]. Usa `DecoratedCustomViewStyle`, então cabeçalho, fundo e
 * expansão continuam sendo os do sistema (tema claro/escuro e tela de bloqueio corretos).
 */
class RichNotifier(private val context: Context) {
    private val base = FinaiNotifier(context)

    fun notificationId(key: String): Int = BASE_ID + (key.hashCode() and 0x7FFFFFF) % 900_000

    fun cancel(key: String) = NotificationManagerCompat.from(context).cancel(notificationId(key))

    /** Devolve se a notificação saiu (sem permissão, nada sai e nada quebra). */
    fun post(spec: NotifySpec, channelId: String, timeoutMs: Long? = null, silent: Boolean = false): Boolean {
        if (!base.hasNotificationPermission()) return false
        val id = notificationId(spec.key)
        val accent = ContextCompat.getColor(context, accentRes(spec.look))
        val badge = badge(spec.look)

        val small = RemoteViews(context.packageName, R.layout.notification_finai_small).apply {
            setImageViewBitmap(R.id.notif_badge, badge)
            setTextViewText(R.id.notif_title, spec.title)
            setTextViewText(R.id.notif_body, spec.body)
            setTextViewText(R.id.notif_amount, spec.amount.orEmpty())
            setViewVisibility(R.id.notif_amount, if (spec.amount == null) View.GONE else View.VISIBLE)
        }
        val big = RemoteViews(context.packageName, R.layout.notification_finai_big).apply {
            setImageViewBitmap(R.id.notif_badge, badge)
            setTextViewText(R.id.notif_eyebrow, spec.eyebrow)
            setTextColor(R.id.notif_eyebrow, accent)
            setTextViewText(R.id.notif_title, spec.title)
            setTextViewText(R.id.notif_body, spec.body)
            setViewVisibility(R.id.notif_amount_row, if (spec.amount == null) View.GONE else View.VISIBLE)
            setTextViewText(R.id.notif_amount, spec.amount.orEmpty())
            setTextViewText(R.id.notif_amount_label, spec.amountLabel.orEmpty())
            spec.progress?.let { p ->
                val bar = when {
                    spec.look == NotifyLook.Limit && p >= 100 -> R.id.notif_progress_rose
                    spec.look == NotifyLook.Limit || spec.look == NotifyLook.Streak -> R.id.notif_progress_amber
                    else -> R.id.notif_progress_emerald
                }
                setViewVisibility(bar, View.VISIBLE)
                setProgressBar(bar, 100, p.coerceIn(0, 100), false)
            }
            spec.progressLabel?.let { setTextViewText(R.id.notif_progress_label, it); setViewVisibility(R.id.notif_progress_label, View.VISIBLE) }
            val slots = listOf(R.id.notif_button_1, R.id.notif_button_2, R.id.notif_button_3)
            setViewVisibility(R.id.notif_buttons, if (spec.buttons.isEmpty()) View.GONE else View.VISIBLE)
            spec.buttons.take(slots.size).forEachIndexed { i, button ->
                setViewVisibility(slots[i], View.VISIBLE)
                setTextViewText(slots[i], button.label)
                setOnClickPendingIntent(slots[i], pending(spec, button.action, id, i + 1))
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_finai)
            .setColor(accent)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setContentIntent(pending(spec, spec.tap, id, 0))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP)
            .setPriority(if (spec.look == NotifyLook.Overdue || spec.look == NotifyLook.Shortfall) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            // Na tela bloqueada, só o título: valor e nome ficam para depois de desbloquear.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_stat_finai)
                .setContentTitle("FinAI").setContentText(spec.eyebrow.lowercase().replaceFirstChar { it.uppercase() }).build())
        // Leitores de tela e relógios recebem os mesmos botões como ações comuns do sistema.
        spec.buttons.take(3).forEachIndexed { i, b -> builder.addInvisibleAction(NotificationCompat.Action(0, b.label, pending(spec, b.action, id, i + 11))) }
        if (timeoutMs != null) builder.setTimeoutAfter(timeoutMs)
        if (silent) builder.setSilent(true)
        return try {
            NotificationManagerCompat.from(context).notify(id, builder.build()); true
        } catch (_: SecurityException) { false }
    }

    /** Botão que abre tela = Activity direto (o Android 12+ proíbe abrir pelo receiver); o resto, broadcast. */
    private fun pending(spec: NotifySpec, action: NotifyAction, notificationId: Int, slot: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val code = notificationId * 31 + slot
        if (action.opensApp) {
            val intent = Intent(context, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (action == NotifyAction.OpenSuggestion) {
                    this.action = CaptureNotifications.ACTION_OPEN_SUGGESTION
                    putExtra(CaptureNotifications.EXTRA_SUGGESTION_ID, spec.targetId ?: -1L)
                } else {
                    this.action = AppLinks.ACTION_OPEN
                    putExtra(AppLinks.EXTRA_ROUTE, when (action) {
                        NotifyAction.OpenAgenda -> AppLinks.AGENDA
                        NotifyAction.OpenSpending -> AppLinks.SPENDING
                        NotifyAction.OpenPlans -> AppLinks.PLANS
                        NotifyAction.OpenEntry -> AppLinks.ENTRY
                        NotifyAction.OpenClosing -> AppLinks.CLOSING
                        else -> AppLinks.INBOX
                    })
                }
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getActivity(context, code, intent, flags)
        }
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = NotificationActionReceiver.ACTION
            putExtra(NotificationActionReceiver.EXTRA_ACTION, action.name)
            putExtra(NotificationActionReceiver.EXTRA_KEY, spec.key)
            putExtra(NotificationActionReceiver.EXTRA_TARGET, spec.targetId ?: -1L)
            putExtra(NotificationActionReceiver.EXTRA_TITLE, spec.title)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }

    private fun accentRes(look: NotifyLook): Int = when (look) {
        NotifyLook.Overdue, NotifyLook.Shortfall -> R.color.notif_rose
        NotifyLook.Bill, NotifyLook.Limit, NotifyLook.Streak -> R.color.notif_amber
        NotifyLook.Coach -> R.color.notif_sky
        else -> R.color.notif_emerald
    }

    /** Selo: quadrado arredondado em degradê com o símbolo do tipo. Desenhado aqui para não carregar uma imagem por tipo. */
    private fun badge(look: NotifyLook): Bitmap {
        val size = (46 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
        val (from, to) = when (look) {
            NotifyLook.Overdue, NotifyLook.Shortfall -> 0xFFFB7185.toInt() to 0xFFBE123C.toInt()
            NotifyLook.Bill, NotifyLook.Limit -> 0xFFFCD34D.toInt() to 0xFFF59E0B.toInt()
            NotifyLook.Streak -> 0xFFFDBA74.toInt() to 0xFFEA580C.toInt()
            NotifyLook.Coach -> 0xFF93C5FD.toInt() to 0xFF2563EB.toInt()
            else -> 0xFF6EE7B7.toInt() to 0xFF047857.toInt()
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), from, to, Shader.TileMode.CLAMP) }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.3f, size * 0.3f, paint)
        // Brilho no canto de cima, para o selo não ficar chapado.
        canvas.drawRoundRect(RectF(size * 0.08f, size * 0.06f, size * 0.92f, size * 0.48f), size * 0.24f, size * 0.24f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.5f; textAlign = Paint.Align.CENTER }
        canvas.drawText(look.emoji, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2f, text)
        return bitmap
    }

    companion object {
        const val GROUP = "finai_avisos"
        private const val BASE_ID = 60_000
    }
}
