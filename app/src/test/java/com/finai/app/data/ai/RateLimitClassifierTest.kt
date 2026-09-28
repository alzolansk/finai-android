package com.finai.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/** Um 429 só tira o provedor do dia quando a mensagem fala da cota diária. */
class RateLimitClassifierTest {

    @Test fun openRouterUpstreamQueueIsOnlyBusy() {
        val body = """{"error":{"message":"Provider returned error","code":429,"metadata":{"raw":"google/gemma-4-31b-it:free is temporarily rate-limited upstream. Please retry shortly."}}}"""
        assertEquals(AiFailureKind.BUSY, RateLimitClassifier.kindFor429(body))
    }

    @Test fun openRouterDailyFreeLimitExhaustsTheDay() {
        val body = """{"error":{"message":"Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model requests per day","code":429}}"""
        assertEquals(AiFailureKind.RATE_LIMITED, RateLimitClassifier.kindFor429(body))
    }

    @Test fun groqPerMinuteIsBusyAndPerDayIsExhausted() {
        val tpm = """{"error":{"message":"Rate limit reached for model `openai/gpt-oss-120b` on tokens per minute (TPM): Limit 8000, Used 7900. Please try again in 3s.","type":"tokens","code":"rate_limit_exceeded"}}"""
        val rpd = """{"error":{"message":"Rate limit reached for model `openai/gpt-oss-120b` on requests per day (RPD): Limit 1000, Used 1000.","code":"rate_limit_exceeded"}}"""
        assertEquals(AiFailureKind.BUSY, RateLimitClassifier.kindFor429(tpm))
        assertEquals(AiFailureKind.RATE_LIMITED, RateLimitClassifier.kindFor429(rpd))
    }

    @Test fun withoutBodyItIsTreatedAsMomentary() {
        assertEquals(AiFailureKind.BUSY, RateLimitClassifier.kindFor429(null))
        assertEquals(AiFailureKind.BUSY, RateLimitClassifier.kindFor429("Too Many Requests"))
    }
}
