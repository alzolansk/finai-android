package com.finai.app.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.finai.app.domain.importer.CsvReader
import com.finai.app.domain.importer.XlsxReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Texto cru extraído de um documento, antes de qualquer parsing.
 * [rows] só vem preenchido para planilha (onde a estrutura de colunas existe
 * de verdade); para PDF e imagem o que existe é [lines].
 */
data class ExtractedText(
    val lines: List<String>,
    val rows: List<List<String>>? = null,
    val usedOcr: Boolean = false,
    val pageCount: Int = 1,
)

/**
 * Extração de texto de um documento escolhido pelo usuário. Todas as
 * implementações rodam **inteiramente no aparelho** (planning.md §4): ML Kit
 * com modelo latino empacotado no APK para OCR, `PdfRenderer` do próprio
 * Android para PDF, leitores próprios para planilha. Nenhuma delas faz rede.
 */
interface DocumentTextExtractor {
    suspend fun extract(uri: Uri): ExtractedText
}

/**
 * OCR de foto de fatura. `InputImage.fromFilePath` já resolve decodificação e
 * rotação EXIF, então uma foto tirada de lado continua legível.
 */
class ImageTextExtractor(private val context: Context) : DocumentTextExtractor {
    override suspend fun extract(uri: Uri): ExtractedText {
        val image = InputImage.fromFilePath(context, uri)
        val text = OcrEngine.recognize(image)
        return ExtractedText(lines = OcrEngine.toReadingOrderLines(text), usedOcr = true)
    }
}

/**
 * PDF (digital ou escaneado) via rasterização + OCR.
 *
 * O Android não expõe API para ler a camada de texto de um PDF — só
 * [PdfRenderer], que desenha a página. Rasterizar e passar no OCR resolve os
 * dois casos com um caminho só (o PDF escaneado não tem camada de texto de
 * qualquer forma) e sem trazer uma biblioteca de PDF de vários MB para dentro
 * do APK. O custo é ser mais lento e depender da qualidade do render — daí
 * [RENDER_SCALE] alto e o teto de [MAX_PAGES] páginas por importação.
 */
class PdfTextExtractor(private val context: Context) : DocumentTextExtractor {

    override suspend fun extract(uri: Uri): ExtractedText {
        val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Não foi possível abrir o PDF selecionado.")

        val lines = mutableListOf<String>()
        var pages: Int
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                pages = renderer.pageCount
                repeat(minOf(renderer.pageCount, MAX_PAGES)) { index ->
                    renderer.openPage(index).use { page ->
                        val width = (page.width * RENDER_SCALE).toInt().coerceAtMost(MAX_RENDER_WIDTH)
                        val height = (page.height.toFloat() / page.width * width).toInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE) // PDF sem fundo vira preto no OCR
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val text = OcrEngine.recognize(InputImage.fromBitmap(bitmap, 0))
                        lines += OcrEngine.toReadingOrderLines(text)
                        bitmap.recycle()
                    }
                }
            }
        }
        return ExtractedText(lines = lines, usedOcr = true, pageCount = pages)
    }

    companion object {
        /** PDF de fatura costuma vir em 72 dpi; 3x aproxima de 216 dpi, onde o OCR acerta o valor. */
        private const val RENDER_SCALE = 3f
        private const val MAX_RENDER_WIDTH = 2400
        private const val MAX_PAGES = 20
    }
}

/** Planilha: .xlsx lido como ZIP de XML, .csv/.tsv/.txt como texto delimitado. Nada de OCR. */
class SpreadsheetTextExtractor(private val context: Context) : DocumentTextExtractor {

    override suspend fun extract(uri: Uri): ExtractedText {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Não foi possível abrir a planilha selecionada.")

        val rows = if (isZip(bytes)) {
            XlsxReader.read(ByteArrayInputStream(bytes))
        } else {
            CsvReader.read(decodeText(bytes))
        }
        return ExtractedText(lines = rows.map { it.joinToString(" ") }, rows = rows, usedOcr = false)
    }

    private fun isZip(bytes: ByteArray) =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** CSV exportado por banco brasileiro ainda costuma vir em ISO-8859-1; só cai nele se não for UTF-8 válido. */
    private fun decodeText(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: Exception) {
        String(bytes, StandardCharsets.ISO_8859_1)
    }
}

/**
 * ML Kit Text Recognition v2 (modelo latino empacotado). Um reconhecedor só
 * para o app inteiro — criar um por página vaza memória nativa.
 */
internal object OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(image: InputImage): Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
            .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }

    /**
     * Reconstrói as linhas na ordem de leitura. O ML Kit devolve blocos, e numa
     * tabela de fatura cada coluna (data, descrição, valor) costuma virar um
     * bloco separado — juntar por proximidade vertical e ordenar por x é o que
     * traz "12/03  MERCADO ...  189,90" de volta para a mesma linha, que é o
     * formato que [com.finai.app.domain.importer.StatementTextParser] espera.
     */
    fun toReadingOrderLines(text: Text): List<String> {
        data class Fragment(val top: Int, val bottom: Int, val left: Int, val value: String)

        val fragments = text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                Fragment(box.top, box.bottom, box.left, line.text.trim())
            }
            .filter { it.value.isNotBlank() }
            .sortedBy { it.top }

        if (fragments.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<Fragment>>()
        fragments.forEach { fragment ->
            val height = (fragment.bottom - fragment.top).coerceAtLeast(1)
            val row = rows.lastOrNull()
            val rowCenter = row?.let { r -> r.sumOf { (it.top + it.bottom) / 2 } / r.size }
            val center = (fragment.top + fragment.bottom) / 2
            if (row != null && rowCenter != null && Math.abs(center - rowCenter) <= height * ROW_TOLERANCE) {
                row += fragment
            } else {
                rows += mutableListOf(fragment)
            }
        }

        return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.value } }
    }

    /** Metade da altura da linha: tolerância suficiente para colunas desalinhadas sem juntar duas linhas. */
    private const val ROW_TOLERANCE = 0.6
}
