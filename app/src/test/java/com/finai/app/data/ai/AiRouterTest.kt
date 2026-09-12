package com.finai.app.data.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the Fase 3 fallback/quota/cache contract from planning.md §7.3
 * and §10's acceptance criterion for this phase ("ao simular esgotamento de
 * cota do provedor principal... o app troca automaticamente para o próximo
 * provedor... e mostra a mensagem de degradação graciosa apenas quando todos
 * estiverem esgotados") — with fakes standing in for the network and
 * DataStore, never a real provider or Context.
 */
class AiRouterTest {

    private fun request(tag: String) = AiRequest(AiTask.CHAT, "system", "prompt-$tag")

    private class FakeProvider(vararg responses: AiResponse) : AiProvider {
        private val queue = ArrayDeque(responses.toList())
        var callCount = 0
            private set

        override suspend fun generate(request: AiRequest): AiResponse {
            callCount++
            return queue.removeFirst()
        }
    }

    private class FakeUsageTracker : UsageTracker {
        val attempts = mutableMapOf<ProviderId, Int>()
        private val exhausted = mutableSetOf<ProviderId>()

        override suspend fun recordAttempt(provider: ProviderId) {
            attempts[provider] = (attempts[provider] ?: 0) + 1
        }

        override suspend fun markExhaustedToday(provider: ProviderId) {
            exhausted += provider
        }

        override suspend fun isExhaustedToday(provider: ProviderId) = provider in exhausted
    }

    @Test
    fun `primary provider success short-circuits the rest`() = runTest {
        val gemini = FakeProvider(AiResponse.Success("resposta do gemini"))
        val groq = FakeProvider(AiResponse.Success("nunca deveria ser chamado"))
        val router = AiRouter(FakeUsageTracker(), listOf(ProviderId.GEMINI to gemini, ProviderId.GROQ to groq))

        val result = router.generate(request("primary-success"))

        assertEquals(AiResponse.Success("resposta do gemini"), result)
        assertEquals(0, groq.callCount)
    }

    @Test
    fun `429 from primary falls back to next provider and marks it exhausted`() = runTest {
        val usage = FakeUsageTracker()
        val gemini = FakeProvider(AiResponse.Unavailable("cota estourada", AiFailureKind.RATE_LIMITED))
        val groq = FakeProvider(AiResponse.Success("resposta do groq"))
        val router = AiRouter(usage, listOf(ProviderId.GEMINI to gemini, ProviderId.GROQ to groq))

        val result = router.generate(request("fallback"))

        assertEquals(AiResponse.Success("resposta do groq"), result)
        assertTrue(usage.isExhaustedToday(ProviderId.GEMINI))
    }

    @Test
    fun `exhausted provider is skipped without another network call`() = runTest {
        val usage = FakeUsageTracker()
        usage.markExhaustedToday(ProviderId.GEMINI)
        val gemini = FakeProvider(AiResponse.Success("não deveria ser chamado de novo"))
        val groq = FakeProvider(AiResponse.Success("resposta do groq"))
        val router = AiRouter(usage, listOf(ProviderId.GEMINI to gemini, ProviderId.GROQ to groq))

        router.generate(request("skip-exhausted"))

        assertEquals(0, gemini.callCount)
    }

    @Test
    fun `graceful degradation when every configured provider fails`() = runTest {
        val gemini = FakeProvider(AiResponse.Unavailable("sem cota", AiFailureKind.RATE_LIMITED))
        val groq = FakeProvider(AiResponse.Unavailable("timeout", AiFailureKind.TIMEOUT))
        val router = AiRouter(FakeUsageTracker(), listOf(ProviderId.GEMINI to gemini, ProviderId.GROQ to groq))

        val result = router.generate(request("all-fail")) as AiResponse.Unavailable

        assertTrue(result.reason.contains("indisponíveis ou sem cota"))
    }

    @Test
    fun `no provider configured reports a distinct message`() = runTest {
        val gemini = FakeProvider(AiResponse.Unavailable("sem chave", AiFailureKind.NO_KEY))
        val groq = FakeProvider(AiResponse.Unavailable("sem chave", AiFailureKind.NO_KEY))
        val router = AiRouter(FakeUsageTracker(), listOf(ProviderId.GEMINI to gemini, ProviderId.GROQ to groq))

        val result = router.generate(request("no-keys")) as AiResponse.Unavailable

        assertEquals(AiFailureKind.NO_KEY, result.kind)
        assertTrue(result.reason.contains("Nenhum provedor"))
    }

    /**
     * Fase 6. A ordem de fallback termina nos provedores que o usuário
     * normalmente não configura, então usar "o motivo do último que falhou"
     * fazia a mensagem dizer "Nenhuma chave do Cerebras configurada" mesmo
     * quando o problema real era a chave do Gemini estar inválida — escondendo
     * justamente a única falha em que ele pode agir.
     */
    @Test
    fun `mensagem final cita a falha acionável, não a do último provedor da fila`() = runTest {
        val gemini = FakeProvider(AiResponse.Unavailable("Chave do Gemini inválida.", AiFailureKind.AUTH_ERROR))
        val cerebras = FakeProvider(AiResponse.Unavailable("Nenhuma chave do Cerebras configurada.", AiFailureKind.NO_KEY))
        val router = AiRouter(
            FakeUsageTracker(),
            listOf(ProviderId.GEMINI to gemini, ProviderId.CEREBRAS to cerebras),
        )

        val result = router.generate(request("reason-rank")) as AiResponse.Unavailable

        assertTrue(result.reason.contains("Chave do Gemini inválida."))
        assertTrue(!result.reason.contains("Cerebras"))
    }

    /** Cota estourada ganha de erro de rede: é o motivo que explica o dia inteiro sem IA. */
    @Test
    fun `cota estourada prevalece sobre falha de rede na mensagem final`() = runTest {
        val gemini = FakeProvider(AiResponse.Unavailable("Sem conexão.", AiFailureKind.NETWORK_ERROR))
        val groq = FakeProvider(AiResponse.Unavailable("Cota gratuita do Groq esgotada por hoje.", AiFailureKind.RATE_LIMITED))
        val router = AiRouter(FakeUsageTracker(), listOf(ProviderId.GEMINI to gemini, ProviderId.GROQ to groq))

        val result = router.generate(request("rank-rate-limit")) as AiResponse.Unavailable

        assertTrue(result.reason.contains("Cota gratuita do Groq esgotada"))
    }

    @Test
    fun `repeating the same request the same day is served from cache`() = runTest {
        val gemini = FakeProvider(AiResponse.Success("resposta cacheada"))
        val router = AiRouter(FakeUsageTracker(), listOf(ProviderId.GEMINI to gemini))
        val req = request("cache-hit")

        router.generate(req)
        val second = router.generate(req)

        assertEquals(AiResponse.Success("resposta cacheada"), second)
        assertEquals(1, gemini.callCount)
    }
}
