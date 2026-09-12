package com.finai.app.data.importer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.importer.DuplicateVerdict
import com.finai.app.domain.importer.ImportAnalyzer
import com.finai.app.domain.importer.RecurrenceVerdict
import com.finai.app.domain.toEpochMillis
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O que só dá para verificar num Android de verdade: OCR do ML Kit numa foto,
 * rasterização de PDF pelo `PdfRenderer` e leitura de planilha via
 * `ContentResolver`. Roda com [ImportAnalyzer] **sem IA** (`null`), que é o
 * modo offline — se algum destes passar aqui, passou sem rede nenhuma.
 *
 * Execute com um emulador/aparelho conectado:
 * `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class StatementImporterInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val importer = StatementImporter(context, ImportAnalyzer(aiAssistant = null))
    private val reference = LocalDate.of(2026, 3, 25)

    private val linhasDaFatura = listOf(
        "12/03/2026   SUPERMERCADO EXTRA       189,90",
        "14/03/2026   NETFLIX.COM               55,90",
        "15/03/2026   MAGAZINE LUIZA 03/10     129,90",
        "18/03/2026   ESTORNO COMPRA            89,90-",
    )

    @Test
    fun importaFotoDeFaturaComOcrNoAparelho() = runBlocking {
        val uri = writeImage("fatura-foto.png")

        val outcome = importer.import(uri, emptyList(), reference)

        val preview = (outcome as ImportOutcome.Success).preview
        assertTrue("OCR deveria ter sido usado", preview.usedOcr)
        assertTrue("esperava pelo menos 3 lançamentos, veio ${preview.items.size}", preview.items.size >= 3)
        assertTrue(
            "esperava o supermercado classificado por regra local",
            preview.items.any { it.categoria == "Alimentação" },
        )
        assertTrue("esperava o valor de 189,90", preview.items.any { it.entry.amountCents == 18_990L })
    }

    @Test
    fun importaPdfDeFatura() = runBlocking {
        val uri = writePdf("fatura.pdf")

        val outcome = importer.import(uri, emptyList(), reference)

        val preview = (outcome as ImportOutcome.Success).preview
        assertTrue("esperava pelo menos 3 lançamentos, veio ${preview.items.size}", preview.items.size >= 3)
        assertTrue(
            "esperava a assinatura reconhecida como recorrente",
            preview.items.any { it.recurrence == RecurrenceVerdict.LIKELY },
        )
    }

    @Test
    fun importaPlanilhaCsv() = runBlocking {
        val csv = """
            Data;Descrição;Valor
            12/03/2026;SUPERMERCADO EXTRA;189,90
            14/03/2026;NETFLIX.COM;55,90
            15/03/2026;MAGAZINE LUIZA 03/10;129,90
        """.trimIndent()
        val uri = writeFile("fatura.csv", csv.toByteArray())

        val outcome = importer.import(uri, emptyList(), reference)

        val preview = (outcome as ImportOutcome.Success).preview
        assertFalse("planilha não precisa de OCR", preview.usedOcr)
        assertEquals(3, preview.items.size)
        assertEquals("Alimentação", preview.items[0].categoria)
        assertEquals("Assinaturas", preview.items[1].categoria)
        assertEquals(3, preview.items[2].entry.installment?.current)
    }

    @Test
    fun importaPlanilhaXlsx() = runBlocking {
        val uri = writeFile("fatura.xlsx", xlsxBytes())

        val outcome = importer.import(uri, emptyList(), reference)

        val preview = (outcome as ImportOutcome.Success).preview
        assertEquals(2, preview.items.size)
        assertEquals(18_990L, preview.items[0].entry.amountCents)
    }

    @Test
    fun duplicataDoHistoricoEntraSinalizadaEDesmarcada() = runBlocking {
        val uri = writeFile(
            "fatura-dup.csv",
            "Data;Descrição;Valor\n12/03/2026;SUPERMERCADO EXTRA;189,90\n".toByteArray(),
        )
        val existente = listOf(
            TransacaoEntity(
                id = 1,
                data = LocalDate.of(2026, 3, 12).toEpochMillis(),
                descricao = "SUPERMERCADO EXTRA",
                valorCentavos = 18_990L,
                categoria = "Alimentação",
                contaOrigem = "manual",
                recorrente = false,
                origem = "manual",
            ),
        )

        val outcome = importer.import(uri, existente, reference)

        val item = (outcome as ImportOutcome.Success).preview.items.single()
        assertEquals(DuplicateVerdict.LIKELY, item.duplicate)
        assertFalse(item.selected)
    }

    @Test
    fun formatoNaoSuportadoExplicaOQueFazer() = runBlocking {
        val uri = writeFile("fatura.xls", byteArrayOf(1, 2, 3, 4))

        val outcome = importer.import(uri, emptyList(), reference)

        val message = (outcome as ImportOutcome.Failure).message
        assertTrue(message.contains(".xlsx"))
    }

    // ── geração dos arquivos de teste ────────────────────────────────────

    private fun writeFile(name: String, bytes: ByteArray): Uri {
        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { it.write(bytes) }
        return Uri.fromFile(file)
    }

    private fun writeImage(name: String): Uri {
        val bitmap = Bitmap.createBitmap(1400, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 44f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("FATURA DO CARTAO - MARCO 2026", 40f, 90f, paint)
        linhasDaFatura.forEachIndexed { index, line ->
            canvas.drawText(line, 40f, 200f + index * 90f, paint)
        }

        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun writePdf(name: String): Uri {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawText("FATURA DO CARTAO - MARCO 2026", 40f, 60f, paint)
        linhasDaFatura.forEachIndexed { index, line ->
            page.canvas.drawText(line, 40f, 110f + index * 30f, paint)
        }
        document.finishPage(page)

        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return Uri.fromFile(file)
    }

    /** .xlsx mínimo (ZIP de XML) com duas linhas de lançamento. */
    private fun xlsxBytes(): ByteArray {
        val shared = listOf("Data", "Descricao", "Valor", "SUPERMERCADO EXTRA", "NETFLIX.COM")
        val sharedXml = buildString {
            append("""<?xml version="1.0"?><sst>""")
            shared.forEach { append("<si><t>").append(it).append("</t></si>") }
            append("</sst>")
        }
        val sheetXml = """
            <?xml version="1.0"?><worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
            <row r="2"><c r="A2"><v>46093</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>189.9</v></c></row>
            <row r="3"><c r="A3"><v>46095</v></c><c r="B3" t="s"><v>4</v></c><c r="C3"><v>55.9</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()

        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedXml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
