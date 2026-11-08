package com.finai.app.data.capture

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.finai.app.data.notifications.FinaiNotificationChannels
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.domain.capture.CapturedNotification
import com.finai.app.domain.capture.SuggestionOrigin
import com.finai.app.util.FinaiLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * O leitor de notificações do lançamento assistido. O Android dá a este serviço acesso a todas as
 * notificações do aparelho; ele **descarta antes de ler o conteúdo** tudo que não vem de um app
 * escolhido pelo usuário (a checagem usa só o nome do pacote). Do próprio FinAI só aceita as
 * notificações "Teste do FinAI" do canal de testes, com o marcador de teste — nunca os avisos de
 * "Movimentação encontrada", o que impede qualquer ciclo.
 *
 * Captura pausada, permissão revogada ou app fora da lista: a notificação é ignorada sem
 * registro. O resto do app não depende deste serviço.
 */
class FinanceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        CaptureLinks.setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        CaptureLinks.setListenerConnected(false)
    }

    override fun onDestroy() {
        CaptureLinks.setListenerConnected(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) {
            onOwnNotification(sbn)
            return
        }
        scope.launch {
            try {
                val prefs = FinaiPreferences(applicationContext)
                // Só o motivo do descarte vai para o log de debug; o conteúdo nunca é lido aqui.
                if (!prefs.captureEnabled.first()) { FinaiLog.d(TAG, "descartada: captura pausada"); return@launch }
                if (pkg !in prefs.capturePackages.first()) { FinaiLog.d(TAG, "descartada: app fora da lista"); return@launch }
                val captured = read(sbn, appLabel(pkg)) ?: run { FinaiLog.d(TAG, "descartada: sem texto ou resumo de grupo"); null } ?: return@launch
                CaptureProcessor(applicationContext).process(captured, SuggestionOrigin.Notification, testId = null)
            } catch (t: Throwable) {
                // Só a classe do erro (FinaiLog): nada do conteúdo da notificação.
                FinaiLog.w(TAG, "Falha ao processar uma notificação", t)
            }
        }
    }

    /** Só "Teste do FinAI": canal de testes + marcador. O resto do FinAI é ignorado. */
    private fun onOwnNotification(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.channelId != FinaiNotificationChannels.TEST_CHANNEL_ID) return
        val testId = n.extras?.getLong(CaptureNotifications.EXTRA_TEST_ID, -1L) ?: -1L
        if (testId < 0) return
        scope.launch {
            try {
                val repository = CaptureRepository(com.finai.app.data.local.FinaiDatabase.get(applicationContext))
                if (repository.testRun(testId) == null) return@launch // teste apagado por "Limpar testes"
                repository.setTestStatus(testId, TestStatus.Received)
                val captured = read(sbn, "FinAI (teste)", dropSubText = CaptureNotifications.TEST_SUBTEXT) ?: return@launch
                CaptureProcessor(applicationContext).process(captured, SuggestionOrigin.TestNotification, testId)
            } catch (t: Throwable) {
                FinaiLog.w(TAG, "Falha ao processar a notificação de teste", t)
            }
        }
    }

    /** Os campos de texto, e só eles. Resumo de grupo e notificação contínua não são movimentação. */
    private fun read(sbn: StatusBarNotification, label: String, dropSubText: String? = null): CapturedNotification? {
        val n = sbn.notification ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (sbn.isOngoing) return null
        val e = n.extras ?: return null
        val lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty()
        val messages = runCatching {
            @Suppress("DEPRECATION")
            e.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { (it as? android.os.Bundle)?.getCharSequence("text")?.toString() }
        }.getOrNull().orEmpty()
        val title = (e.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: e.getCharSequence(Notification.EXTRA_TITLE))?.toString()
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: messages.lastOrNull()
        val big = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val sub = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it != dropSubText }
        if (title.isNullOrBlank() && text.isNullOrBlank() && big.isNullOrBlank() && lines.isEmpty()) return null
        return CapturedNotification(
            packageName = sbn.packageName, appLabel = label, systemKey = sbn.key, postedAt = sbn.postTime,
            title = title, text = text, bigText = big, subText = sub, lines = lines,
        )
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object { const val TAG = "FinanceListener" }
}
