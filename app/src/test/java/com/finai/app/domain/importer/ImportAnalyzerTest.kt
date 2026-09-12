package com.finai.app.domain.importer

import com.finai.app.data.ai.AiFailureKind
import com.finai.app.data.ai.AiProvider
import com.finai.app.data.ai.AiRequest
import com.finai.app.data.ai.AiResponse
import com.finai.app.data.ai.AiTask
import com.finai.app.data.ai.ImportAiAssistant
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O comportamento que a Fase 4 promete de ponta a ponta (planning.md §10):
 * uma fatura é lida, classificada e revisável, com duplicatas sinalizadas — e
 * continua assim **com ou sem IA**, porque a IA só entra no que a regra local
 * não resolveu. Usa fakes de [AiProvider]: nenhum teste aqui toca rede,
 * Context, Room de verdade ou arquivo.
 */
class ImportAnalyzerTest {

    private val reference = LocalDate.of(2026, 3, 25)

    /** Fatura com um item conhecido, um ambíguo, um estorno e uma duplicata do histórico. */
    private val fatura = listOf(
        "12/03  SUPERMERCADO EXTRA LOJA 2233        189,90",
        "14/03  NETFLIX.COM                          55,90",
        "16/03  ZZ COMERCIO 4477 SAO PAULO           74,30",
        "18/03  ESTORNO COMPRA DUPLICADA             89,90-",
    )

    private val historico = listOf(
        TransacaoEntity(
            id = 1,
            data = LocalDate.of(2026, 3, 12).toEpochMillis(),
            descricao = "SUPERMERCADO EXTRA 2233",
            valorCentavos = 18_990L,
            categoria = "Alimentação",
            contaOrigem = "manual",
            recorrente = false,
            origem = "manual",
        ),
    )

    private class FakeProvider(private val responses: Map<AiTask, AiResponse>) : AiProvider {
        val seenPrompts = mutableListOf<String>()
        var callCount = 0
            private set

        override suspend fun generate(request: AiRequest): AiResponse {
            callCount++
            seenPrompts += request.prompt
            return responses[request.task] ?: AiResponse.Unavailable("sem resposta", AiFailureKind.UNKNOWN)
        }
    }

    private fun parse() = StatementTextParser.parse(fatura, reference)

    private suspend fun analyze(provider: AiProvider?) = ImportAnalyzer(provider?.let(::ImportAiAssistant))
        .analyze(parse(), historico, "fatura-marco.pdf", DocumentKind.PDF, usedOcr = true)

    @Test
    fun `sem IA o fluxo inteiro continua util`() = runTest {
        val preview = analyze(provider = null)

        assertEquals(4, preview.items.size)
        assertFalse(preview.aiUsed)
        assertNull(preview.aiNote)
        // Regra local resolveu o que dava:
        assertEquals("Alimentação", preview.items[0].categoria)
        assertEquals("Assinaturas", preview.items[1].categoria)
        assertEquals(RecurrenceVerdict.LIKELY, preview.items[1].recurrence)
        // E o ambíguo ficou honestamente marcado para revisão em vez de chutar:
        val ambiguo = preview.items[2]
        assertEquals("Outros", ambiguo.categoria)
        assertEquals(CategorySource.FALLBACK, ambiguo.categorySource)
        assertTrue(ambiguo.needsCategoryReview)
    }

    @Test
    fun `com IA o item ambiguo recebe categoria e some da revisao`() = runTest {
        // Dois itens ambíguos (a loja desconhecida e o estorno) numa chamada só.
        val provider = FakeProvider(mapOf(AiTask.IMPORT_CATEGORY to AiResponse.Success("1 | Lazer\n2 | Outros")))

        val preview = analyze(provider)
        val ambiguo = preview.items[2]

        assertTrue(preview.aiUsed)
        assertEquals("Lazer", ambiguo.categoria)
        assertEquals(CategorySource.AI, ambiguo.categorySource)
        assertFalse(ambiguo.needsCategoryReview)
        assertEquals("nenhum item deve sobrar para revisão manual", 0, preview.reviewCount)
        assertEquals("uma chamada só para os dois ambíguos", 1, provider.callCount)
    }

    @Test
    fun `so o item ambiguo vai para a IA e sem o arquivo original`() = runTest {
        val provider = FakeProvider(mapOf(AiTask.IMPORT_CATEGORY to AiResponse.Success("1 | Lazer\n2 | Outros")))

        analyze(provider)
        val prompt = provider.seenPrompts.single()

        assertTrue("o ambíguo precisa estar no prompt", prompt.contains("zz comercio"))
        assertFalse("item já classificado por regra não pode gastar cota", prompt.contains("netflix", ignoreCase = true))
        assertFalse(prompt.contains("supermercado", ignoreCase = true))
        // Nada de arquivo, caminho, número de cartão ou código de loja:
        assertFalse(prompt.contains("fatura-marco.pdf"))
        assertFalse(prompt.contains("4477"))
    }

    @Test
    fun `duplicata do historico entra sinalizada e desmarcada`() = runTest {
        val preview = analyze(provider = null)
        val duplicada = preview.items.first { it.entry.description.contains("SUPERMERCADO") }

        assertEquals(DuplicateVerdict.LIKELY, duplicada.duplicate)
        assertNotNull(duplicada.duplicateReason)
        assertFalse("duplicata não pode entrar marcada para salvar", duplicada.selected)
        assertEquals(1, preview.duplicateCount)
        assertEquals(3, preview.selectedItems.size)
    }

    @Test
    fun `sem conexao a importacao termina com os dados locais e explica a falta da IA`() = runTest {
        val offline = object : AiProvider {
            override suspend fun generate(request: AiRequest) = AiResponse.Unavailable(
                "Sem conexão com o provedor de IA.",
                AiFailureKind.NETWORK_ERROR,
            )
        }

        val preview = analyze(offline)

        assertEquals(4, preview.items.size)
        assertFalse(preview.aiUsed)
        assertEquals("Sem conexão com o provedor de IA.", preview.aiNote)
        assertEquals("Alimentação", preview.items[0].categoria)
        assertTrue(preview.items[2].needsCategoryReview)
        assertEquals(DuplicateVerdict.LIKELY, preview.items[0].duplicate)
    }

    @Test
    fun `excecao da camada de IA nao derruba a importacao`() = runTest {
        val explodindo = object : AiProvider {
            override suspend fun generate(request: AiRequest): AiResponse = throw IOException("socket fechado")
        }

        val preview = analyze(explodindo)

        assertEquals(4, preview.items.size)
        assertFalse(preview.aiUsed)
        assertTrue(preview.aiNote!!.contains("socket fechado"))
    }

    @Test
    fun `sem nada ambiguo a IA nem e chamada`() = runTest {
        val provider = FakeProvider(mapOf(AiTask.IMPORT_CATEGORY to AiResponse.Success("1 | Lazer")))
        val semAmbiguidade = StatementTextParser.parse(
            listOf("14/03  NETFLIX.COM  55,90", "16/03  UBER TRIP  23,45"),
            reference,
        )

        val preview = ImportAnalyzer(ImportAiAssistant(provider))
            .analyze(semAmbiguidade, emptyList(), "planilha.csv", DocumentKind.SPREADSHEET, usedOcr = false)

        assertEquals(0, provider.callCount)
        assertFalse(preview.aiUsed)
        assertEquals(0, preview.reviewCount)
    }

    @Test
    fun `IA pode desfazer uma suspeita de duplicata ambigua`() = runTest {
        val historicoProximo = listOf(
            TransacaoEntity(
                id = 2,
                data = LocalDate.of(2026, 3, 10).toEpochMillis(),
                descricao = "PADARIA CENTRAL",
                valorCentavos = 2_500L,
                categoria = "Alimentação",
                contaOrigem = "manual",
                recorrente = false,
                origem = "manual",
            ),
        )
        val provider = FakeProvider(mapOf(AiTask.IMPORT_DUPLICATE to AiResponse.Success("1 | nao")))
        val parsed = StatementTextParser.parse(listOf("12/03  PADARIA CENTRAL LOJA 2   25,00"), reference)

        val preview = ImportAnalyzer(ImportAiAssistant(provider))
            .analyze(parsed, historicoProximo, "fatura.csv", DocumentKind.SPREADSHEET, usedOcr = false)

        assertEquals(DuplicateVerdict.NONE, preview.items.single().duplicate)
        assertTrue(preview.items.single().selected)
    }
}
