package com.finai.app.data.capture

import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.FinaiDatabase
import com.finai.app.data.local.entity.TesteNotificacaoEntity
import com.finai.app.data.prefs.FinaiPreferences
import com.finai.app.domain.capture.MovementKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Captura de verdade, no aparelho: o `FinanceNotificationListener` ligado pelo Android lê a
 * notificação publicada e cria a sugestão no banco do app. Nada aqui chama o parser direto.
 *
 * O teste da notificação "Teste do FinAI" roda sozinho. Os testes `shell*` exigem um app que
 * não seja o FinAI publicando: o roteiro em `planning.md` (lançamento por notificações) usa
 * `adb shell cmd notification post` (pacote com.android.shell) entre as etapas; sem
 * `-e shellE2E true` eles são pulados.
 */
class CaptureListenerE2ETest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repo get() = CaptureRepository(FinaiDatabase.get(context))

    private fun shell(cmd: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }
    }

    @Before fun grant() {
        shell("cmd notification allow_listener ${context.packageName}/${FinanceNotificationListener::class.java.name}")
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        com.finai.app.data.notifications.FinaiNotificationChannels.ensureCreated(context)
        waitUntil(10_000) { NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) }
        waitUntil(10_000) { CaptureLinks.listenerConnected.value }
    }

    private fun waitUntil(timeoutMs: Long, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (check()) return true; Thread.sleep(200) }
        return check()
    }

    private fun status(id: Long) = runBlocking { repo.testRun(id)?.status }

    @Test fun finaiTestNotificationIsReadByTheRealListener() = runBlocking {
        assertTrue("o Android deve ter liberado o acesso", NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
        repo.clearTests()
        val now = System.currentTimeMillis()
        val nid = 69_001
        fun row(etapa: String) = TesteNotificacaoEntity(exemplo = 6, etapa = etapa, modo = "notificacao", titulo = "Compra aprovada",
            texto = "Compra no crédito de R$ 42,90 aprovada no Mercado.", notificacaoId = nid, status = TestStatus.Waiting.name,
            detalhe = "", sugestaoId = null, criadoEm = now, atualizadoEm = now)
        val first = repo.insertTest(row("Envio"))
        assertTrue(CaptureNotifications.postTest(context, nid, first, "Compra aprovada", "Compra no crédito de R$ 42,90 aprovada no Mercado."))
        assertTrue("listener deve criar a sugestão", waitUntil(10_000) { status(first) == TestStatus.SuggestionCreated.name })
        val s = repo.suggestions.first().single { it.testeId == first }
        assertEquals(4290L, s.valorCentavos)
        assertEquals(MovementKind.CreditPurchase.name, s.tipo)
        assertEquals("teste", s.origem)

        // Mesma notificação de novo (mesmo id, mesmo texto): repetição, nenhuma sugestão nova.
        val second = repo.insertTest(row("Reenvio"))
        CaptureNotifications.postTest(context, nid, second, "Compra aprovada", "Compra no crédito de R$ 42,90 aprovada no Mercado.")
        assertTrue(waitUntil(10_000) { status(second) == TestStatus.Repeated.name })

        // "Movimentação encontrada" do próprio FinAI (sem marcador de teste) nunca é lida: sem ciclo.
        CaptureNotifications.notifySuggestion(context, s)
        Thread.sleep(2_000)
        assertEquals(1, repo.suggestions.first().count { it.origem == "teste" })
        repo.clearTests()
        CaptureNotifications.cancelTests(context, listOf(nid))
        CaptureNotifications.cancelSuggestion(context, s.id)
    }

    // ── roteiro com adb (pacote com.android.shell) ───────────────────

    private fun shellE2E() = assumeTrue(InstrumentationRegistry.getArguments().getString("shellE2E") == "true")

    @Test fun shell1_selectShellAsBankApp() = runBlocking {
        shellE2E()
        val prefs = FinaiPreferences(context)
        prefs.setCapturePackages(setOf("com.android.shell"))
        prefs.setCaptureEnabled(true)
        FinaiDatabase.get(context).sugestaoLancamentoDao().getReal().filter { it.pacote == "com.android.shell" }
            .forEach { FinaiDatabase.get(context).sugestaoLancamentoDao().update(it.copy(estado = "ignorada", resolvidaEm = 0)) }
        repo.purge(System.currentTimeMillis())
    }

    @Test fun shell2_verifyCaptureFromSelectedApp() = runBlocking {
        shellE2E()
        val fromShell = repo.suggestions.first().filter { it.pacote == "com.android.shell" && it.estado == "pendente" }
        assertEquals("compra + reenvio + atualização = uma sugestão", 1, fromShell.size)
        assertEquals(4290L, fromShell.single().valorCentavos)
        assertEquals(MovementKind.CreditPurchase.name, fromShell.single().tipo)
        assertEquals("notificacao", fromShell.single().origem)
    }

    @Test fun shell3_unselectShell() = runBlocking {
        shellE2E()
        FinaiPreferences(context).setCapturePackages(emptySet())
    }

    @Test fun shell4_verifyUnselectedAppIsDiscarded() = runBlocking {
        shellE2E()
        val fromShell = repo.suggestions.first().filter { it.pacote == "com.android.shell" && it.estado == "pendente" }
        assertEquals("app fora da lista não gera sugestão", 1, fromShell.size)
        assertTrue(fromShell.none { it.valorCentavos == 1850L })
    }
}
