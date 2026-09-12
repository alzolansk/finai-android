package com.finai.app.data.importer

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.domain.importer.DuplicateVerdict
import com.finai.app.domain.importer.ImportAnalyzer
import com.finai.app.domain.importer.ImportPreview
import com.finai.app.domain.importer.RecurrenceVerdict
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Importa as faturas de exemplo de `/samples` — os mesmos arquivos que o
 * usuário pode copiar para o aparelho e testar pela tela. Todos passam pelo
 * [StatementImporter] completo, **sem IA** (analisador com `aiAssistant =
 * null`), então o que passa aqui passa offline.
 *
 * As quatro faturas têm o mesmo conteúdo em formatos diferentes: 6 lançamentos,
 * sendo um parcelado (03/10), um estorno, uma assinatura conhecida (Netflix) e
 * uma loja desconhecida que fica marcada para revisão.
 */
@RunWith(AndroidJUnit4::class)
class SampleStatementsInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val importer = StatementImporter(context, ImportAnalyzer(aiAssistant = null))
    private val reference = LocalDate.of(2026, 3, 25)

    @Test
    fun planilhaCsvDeExemplo() = runBlocking {
        val preview = import("fatura-exemplo.csv")

        assertEquals(6, preview.items.size)
        assertEquals("Alimentação", preview.items[0].categoria)
        assertEquals(18_990L, preview.items[0].entry.amountCents)
        assertEquals(3, preview.items[2].entry.installment?.current)
        assertEquals(10, preview.items[2].entry.installment?.total)
        assertTrue("estorno deveria ser negativo", preview.items[4].entry.amountCents < 0)
        assertTrue("loja desconhecida fica para revisão", preview.items[5].needsCategoryReview)
        assertEquals(RecurrenceVerdict.LIKELY, preview.items[1].recurrence)
    }

    @Test
    fun planilhaXlsxDeExemplo() = runBlocking {
        val preview = import("fatura-exemplo.xlsx")

        assertEquals(6, preview.items.size)
        assertEquals(18_990L, preview.items[0].entry.amountCents)
        assertEquals(LocalDate.of(2026, 3, 12), preview.items[0].entry.date)
    }

    @Test
    fun pdfDeExemplo() = runBlocking {
        val preview = import("fatura-exemplo.pdf")

        assertTrue("PDF passa por OCR no aparelho", preview.usedOcr)
        assertTrue("esperava ao menos 5 lançamentos, veio ${preview.items.size}", preview.items.size >= 5)
        assertTrue(preview.items.any { it.entry.amountCents == 18_990L })
        assertTrue(preview.items.any { it.categoria == "Assinaturas" })
    }

    @Test
    fun fotoDeExemplo() = runBlocking {
        val preview = import("fatura-exemplo.png")

        assertTrue(preview.usedOcr)
        assertTrue("esperava ao menos 5 lançamentos, veio ${preview.items.size}", preview.items.size >= 5)
        assertTrue(preview.items.any { it.entry.amountCents == 18_990L })
    }

    @Test
    fun importarDuasVezesSinalizaTodasAsDuplicatas() = runBlocking {
        val primeira = import("fatura-exemplo.csv")
        val jaSalvos = primeira.items.map { item ->
            com.finai.app.data.local.entity.TransacaoEntity(
                id = 0,
                data = item.entry.date!!.toEpochDay() * 86_400_000L,
                descricao = item.entry.description,
                valorCentavos = item.entry.amountCents,
                categoria = item.categoria,
                contaOrigem = "fatura-exemplo",
                recorrente = false,
                origem = "importado",
            )
        }

        val segunda = importer.import(uriFor("fatura-exemplo.csv"), jaSalvos, reference)
        val preview = (segunda as ImportOutcome.Success).preview

        assertEquals(6, preview.duplicateCount)
        assertTrue("nenhuma duplicata pode vir marcada", preview.selectedItems.none { it.duplicate == DuplicateVerdict.LIKELY })
    }

    private suspend fun import(asset: String): ImportPreview {
        val outcome = importer.import(uriFor(asset), emptyList(), reference)
        assertTrue("importação de $asset falhou: $outcome", outcome is ImportOutcome.Success)
        return (outcome as ImportOutcome.Success).preview
    }

    /** Copia o asset de teste para o cache do app e devolve um Uri de arquivo. */
    private fun uriFor(asset: String): Uri {
        val file = File(context.cacheDir, asset)
        instrumentation.context.assets.open(asset).use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(file)
    }
}
