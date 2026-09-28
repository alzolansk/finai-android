package com.finai.app.data.backup

import com.finai.app.data.local.entity.AssinaturaEntity
import com.finai.app.data.local.entity.ContaEntity
import com.finai.app.data.local.entity.DividaEntity
import com.finai.app.data.local.entity.FaturaCartaoEntity
import com.finai.app.data.local.entity.MensagemChatEntity
import com.finai.app.data.local.entity.ObjetivoEntity
import com.finai.app.data.local.entity.OrcamentoCategoriaEntity
import com.finai.app.data.local.entity.TransacaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val backup = FinaiBackup(
        schemaVersion = 10,
        exportedAt = 1_790_000_000_000,
        transacoes = listOf(
            TransacaoEntity(id = 7, data = 1_789_000_000_000, descricao = "Salário", valorCentavos = 500_000, categoria = "Outros",
                contaOrigem = "", recorrente = true, tipo = "Receita", origem = "manual", rendaPrincipal = true),
            TransacaoEntity(id = 8, data = 1_789_100_000_000, descricao = "Mercado", valorCentavos = 18_990, categoria = "Alimentação",
                contaOrigem = "Nubank", recorrente = false, origem = "importado", faturaId = 3),
        ),
        faturas = listOf(FaturaCartaoEntity(id = 3, contaId = 4, referencia = "Nubank", fechamento = null, vencimento = 1_789_500_000_000)),
        contas = listOf(ContaEntity(id = 4, nome = "Fatura Nubank", valorCentavos = 18_990, vencimento = 1_789_500_000_000, status = "pendente", tipo = "a_pagar", recorrente = false)),
        objetivos = listOf(ObjetivoEntity(id = 1, tipo = "Viagem", nome = "Portugal", valorAlvoCentavos = 1_000_000, valorGuardadoCentavos = 200_000, prazo = 1_800_000_000_000, prioridade = 1, descricao = "10 dias")),
        dividas = listOf(DividaEntity(id = 2, nome = "Empréstimo", valorOriginalCentavos = 600_000, valorAbertoCentavos = 200_000,
            taxaJurosMensalBasisPoints = 150, parcelasRestantes = 4, valorParcelaCentavos = 50_000, parcelasTotais = 12,
            proximoVencimento = 1_789_900_000_000, diaVencimento = 29)),
        orcamentos = listOf(OrcamentoCategoriaEntity("Lazer", 40_000, "2026-09")),
        assinaturas = listOf(AssinaturaEntity(id = 5, nome = "Streaming", valorCentavos = 3_990, ultimoUso = null, status = "ativa")),
        mensagens = listOf(MensagemChatEntity(id = 9, papel = "usuario", texto = "oi", timestamp = 1, conversaId = 2, contexto = null)),
    )

    @Test fun roundTripKeepsEveryRowAndId() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup), currentSchema = 10)
        assertEquals(backup, decoded)
    }

    @Test fun neverCarriesAiKeysOrUsage() {
        val json = BackupCodec.encode(backup)
        assertFalse(json.contains("uso_provedor", ignoreCase = true))
        assertFalse(json.contains("apiKey", ignoreCase = true))
        assertFalse(json.contains("notificacoes", ignoreCase = true))
    }

    @Test fun rejectsANewerSchemaAndForeignFiles() {
        val newer = BackupCodec.encode(backup.copy(schemaVersion = 11))
        val e = runCatching { BackupCodec.decode(newer, currentSchema = 10) }.exceptionOrNull()
        assertTrue(e is BackupFormatException && e.message!!.contains("mais nova"))
        assertTrue(runCatching { BackupCodec.decode("{\"a\":1}", 10) }.exceptionOrNull() is BackupFormatException)
        assertTrue(runCatching { BackupCodec.decode("não é json", 10) }.exceptionOrNull() is BackupFormatException)
    }

    @Test fun olderFilesWithoutNewFieldsStillOpen() {
        val old = BackupCodec.encode(backup.copy(schemaVersion = 9))
            .replace("\"rendaPrincipal\": true,", "").replace("\"diaVencimento\": 29,", "")
        val decoded = BackupCodec.decode(old, currentSchema = 10)
        assertFalse(decoded.transacoes.first().rendaPrincipal)
        assertEquals(null, decoded.dividas.first().diaVencimento)
    }

    @Test fun summaryNamesWhatTheFileBrings() {
        assertTrue(backup.summary().startsWith("2 lançamento(s), 1 conta(s)"))
    }
}
