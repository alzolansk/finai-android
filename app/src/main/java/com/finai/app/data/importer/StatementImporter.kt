package com.finai.app.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.finai.app.data.ai.AiRouter
import com.finai.app.data.ai.ImportAiAssistant
import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.importer.DocumentKind
import com.finai.app.domain.importer.ImportAnalyzer
import com.finai.app.domain.importer.ImportPreview
import com.finai.app.domain.importer.ParseResult
import com.finai.app.domain.importer.SpreadsheetStatementParser
import com.finai.app.domain.importer.StatementTextParser
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Etapas visíveis do processamento (planning.md §3.6) — a tela mostra em qual delas está. */
enum class ImportStage {
    /** Abrindo o arquivo escolhido. */
    READING,

    /** OCR (foto/PDF) ou leitura das células (planilha). */
    EXTRACTING,

    /** Parsing determinístico de data, descrição, valor e parcela. */
    PARSING,

    /** Categoria, duplicata e recorrência — regra local e, se preciso, IA. */
    CLASSIFYING,
}

sealed interface ImportOutcome {
    data class Success(val preview: ImportPreview) : ImportOutcome

    /** [message] é texto pt-BR pronto para a tela — sem stack trace. */
    data class Failure(val message: String) : ImportOutcome
}

/**
 * Orquestra a importação de uma fatura de ponta a ponta: resolve o tipo do
 * arquivo, extrai o texto no aparelho, faz o parsing determinístico e entrega
 * a revisão pronta. É a única classe da Fase 4 que conhece `Uri`,
 * `ContentResolver` e OCR — parsing e decisão ficam em `domain/importer`, e a
 * persistência fica com quem chama (o [com.finai.app.state.ImportViewModel]),
 * para que nada disso rode dentro de Composable ou dentro do ViewModel.
 *
 * O arquivo original nunca é copiado para fora do processo nem enviado para
 * rede: só o texto mínimo do que já foi extraído pode ir para a IA, e apenas
 * quando a regra local não resolveu (planning.md §4/§6).
 */
class StatementImporter(
    private val context: Context,
    private val analyzer: ImportAnalyzer,
) {

    constructor(context: Context) : this(context, ImportAnalyzer(ImportAiAssistant(AiRouter(context))))

    suspend fun import(
        uri: Uri,
        existing: List<TransacaoEntity>,
        reference: LocalDate = LocalDate.now(),
        onStage: (ImportStage) -> Unit = {},
    ): ImportOutcome = withContext(Dispatchers.IO) {
        onStage(ImportStage.READING)
        val name = displayName(uri)
        val kind = DocumentKinds.of(name, context.contentResolver.getType(uri))
        if (kind == DocumentKind.UNSUPPORTED) {
            return@withContext ImportOutcome.Failure(
                "Formato não suportado: \"$name\". Use PDF, imagem (JPG/PNG/HEIC), .csv ou .xlsx. " +
                    "Planilha .xls antiga precisa ser salva como .xlsx ou .csv.",
            )
        }

        try {
            onStage(ImportStage.EXTRACTING)
            val extracted = extractorFor(kind).extract(uri)

            onStage(ImportStage.PARSING)
            val parsed = parse(kind, extracted, reference)
            if (parsed.entries.isEmpty()) {
                return@withContext ImportOutcome.Failure(emptyResultMessage(kind, extracted))
            }

            onStage(ImportStage.CLASSIFYING)
            ImportOutcome.Success(
                analyzer.analyze(
                    parse = parsed,
                    existing = existing,
                    sourceName = name,
                    documentKind = kind,
                    usedOcr = extracted.usedOcr,
                ),
            )
        } catch (e: SecurityException) {
            ImportOutcome.Failure("Sem permissão para ler \"$name\". Escolha o arquivo de novo pelo seletor.")
        } catch (e: FileNotFoundException) {
            ImportOutcome.Failure("O arquivo \"$name\" não está mais acessível. Escolha de novo.")
        } catch (e: IOException) {
            ImportOutcome.Failure(
                "Não foi possível ler \"$name\". Se for um PDF protegido por senha, remova a senha e tente de novo. " +
                    "(${e.message ?: "erro de leitura"})",
            )
        } catch (e: OutOfMemoryError) {
            ImportOutcome.Failure("O arquivo é grande demais para processar neste aparelho. Tente importar menos páginas por vez.")
        } catch (e: Exception) {
            ImportOutcome.Failure("Falha ao processar \"$name\": ${e.message ?: e::class.java.simpleName}.")
        }
    }

    private fun extractorFor(kind: DocumentKind): DocumentTextExtractor = when (kind) {
        DocumentKind.PDF -> PdfTextExtractor(context)
        DocumentKind.IMAGE -> ImageTextExtractor(context)
        DocumentKind.SPREADSHEET -> SpreadsheetTextExtractor(context)
        DocumentKind.UNSUPPORTED -> error("tipo não suportado chega filtrado antes")
    }

    private fun parse(kind: DocumentKind, extracted: ExtractedText, reference: LocalDate): ParseResult {
        val rows = extracted.rows
        if (kind == DocumentKind.SPREADSHEET && rows != null) {
            val result = SpreadsheetStatementParser.parse(rows, reference)
            // Planilha sem estrutura reconhecível (ex.: um extrato colado numa
            // coluna só) ainda pode ser lida como texto corrido.
            if (result.entries.isNotEmpty()) return result
        }
        return StatementTextParser.parse(extracted.lines, reference)
    }

    private fun emptyResultMessage(kind: DocumentKind, extracted: ExtractedText) = when {
        extracted.lines.isEmpty() && kind != DocumentKind.SPREADSHEET ->
            "Não foi possível ler texto nesse arquivo. Se for uma foto, tire de novo com a fatura bem iluminada e reta."
        kind == DocumentKind.SPREADSHEET ->
            "A planilha foi lida (${extracted.rows?.size ?: 0} linha(s)), mas nenhuma linha tinha data e valor reconhecíveis. " +
                "Confira se existem colunas de data, descrição e valor."
        else ->
            "O texto foi extraído (${extracted.lines.size} linha(s)), mas nenhum lançamento com data e valor foi reconhecido."
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "documento"
    }
}

/** Descobre o tipo do documento por MIME e, quando o provider mente ou omite, pela extensão. */
object DocumentKinds {

    private val SPREADSHEET_EXTENSIONS = listOf(".csv", ".tsv", ".txt", ".xlsx", ".xlsm")
    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp")

    fun of(fileName: String, mimeType: String?): DocumentKind {
        val name = fileName.lowercase()
        val mime = mimeType?.lowercase().orEmpty()
        return when {
            mime == "application/pdf" || name.endsWith(".pdf") -> DocumentKind.PDF
            mime.startsWith("image/") || IMAGE_EXTENSIONS.any { name.endsWith(it) } -> DocumentKind.IMAGE
            mime.contains("spreadsheet") || mime.contains("csv") || mime == "text/plain" ||
                mime == "text/tab-separated-values" || SPREADSHEET_EXTENSIONS.any { name.endsWith(it) } -> DocumentKind.SPREADSHEET
            // .xls binário antigo não é suportado: exigiria uma biblioteca pesada
            // só para um formato que o próprio Excel já exporta como .xlsx/.csv.
            else -> DocumentKind.UNSUPPORTED
        }
    }
}
