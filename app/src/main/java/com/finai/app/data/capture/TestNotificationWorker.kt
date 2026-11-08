package com.finai.app.data.capture

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.finai.app.data.local.FinaiDatabase
import java.util.concurrent.TimeUnit

/**
 * Publica uma notificação "Teste do FinAI" na hora marcada — dá tempo de sair do app antes.
 * Publicar é só isto: quem lê é o listener, pelo caminho real. Este worker nunca chama o parser.
 */
class TestNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val testId = inputData.getLong(KEY_TEST_ID, -1L).takeIf { it >= 0 } ?: return Result.failure()
        val repository = CaptureRepository(FinaiDatabase.get(applicationContext))
        val run = repository.testRun(testId) ?: return Result.success() // "Limpar testes" no meio
        val posted = CaptureNotifications.postTest(applicationContext, run.notificacaoId, testId, run.titulo, run.texto)
        if (!posted) {
            repository.setTestStatus(testId, TestStatus.NotPublished, "As notificações do FinAI estão bloqueadas neste aparelho.")
            return Result.success()
        }
        // O listener pode ter lido antes desta linha: só marca "publicada" se nada mais avançou.
        val listening = NotificationManagerCompat.getEnabledListenerPackages(applicationContext).contains(applicationContext.packageName)
        repository.setTestStatusIfWaiting(
            testId, TestStatus.Published,
            if (listening) "Esperando o listener ler." else "O acesso às notificações não está liberado: o listener não vai receber.",
        )
        return Result.success()
    }

    companion object {
        private const val KEY_TEST_ID = "testId"

        fun enqueue(context: Context, testId: Long, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<TestNotificationWorker>()
                .setInputData(workDataOf(KEY_TEST_ID to testId))
                .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancelAll(context: Context) = WorkManager.getInstance(context).cancelAllWorkByTag(TAG)

        private const val TAG = "finai-teste-notificacao"
    }
}
