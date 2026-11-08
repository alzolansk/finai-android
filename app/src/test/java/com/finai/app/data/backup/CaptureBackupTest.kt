package com.finai.app.data.backup

import com.finai.app.data.local.entity.SugestaoLancamentoEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sugestões de lançamento por notificação no backup: só reais, sem texto original, e arquivo antigo continua abrindo. */
class CaptureBackupTest {
    private fun suggestion(id: Long, origem: String) = SugestaoLancamentoEntity(
        id = id, origem = origem, pacote = "com.nu.production", appNome = "Nubank", capturadoEm = 1_790_000_000_000,
        atualizadoEm = 1_790_000_000_000, chaveSistema = "0|com.nu.production|7|null|10", assinatura = "abc", tipo = "CreditPurchase",
        valorCentavos = 4290, valoresEncontrados = "", descricao = "Mercado", categoria = "Alimentação", data = 1_789_900_000_000,
        dataExplicita = false, parcelas = null, valorParcelaCentavos = null, cartaoFinal = "1234", contaSugerida = null,
        estado = "confirmada", possivelRepeticao = false, avisadaEm = 1, resolvidaEm = 2, lancamentoIds = "8",
        textoOriginal = "Compra no crédito de R$ 42,90 aprovada no Mercado.", testeId = null,
    )

    private val base = FinaiBackup(15, 1, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test fun realSuggestionsTravelWithoutOriginalText_testsStay() {
        val backup = base.copy(
            sugestoes = listOf(suggestion(1, "notificacao"), suggestion(2, "teste"), suggestion(3, "simulacao")),
            capturaApps = setOf("com.nu.production"),
        )
        val json = BackupCodec.encode(backup)
        assertFalse("texto original nunca vai para o arquivo", json.contains("aprovada no Mercado"))
        val decoded = BackupCodec.decode(json, currentSchema = 15)
        assertEquals(listOf(1L), decoded.sugestoes.map { it.id })
        val s = decoded.sugestoes.single()
        assertNull(s.textoOriginal)
        assertEquals("8", s.lancamentoIds)
        assertEquals("confirmada", s.estado)
        assertEquals(setOf("com.nu.production"), decoded.capturaApps)
    }

    @Test fun backupFromBeforeVersion15OpensWithEmptySuggestions() {
        val old = JSONObject(BackupCodec.encode(base.copy(schemaVersion = 14))).apply {
            remove("sugestoes"); remove("capturaApps")
        }.toString()
        val decoded = BackupCodec.decode(old, currentSchema = 15)
        assertTrue(decoded.sugestoes.isEmpty())
        assertTrue(decoded.capturaApps.isEmpty())
    }
}
