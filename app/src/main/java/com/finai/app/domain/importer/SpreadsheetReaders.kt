package com.finai.app.domain.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Leitores de planilha sem nenhuma dependência externa — Kotlin puro + JDK, o
 * que mantém a leitura 100% no aparelho e testável na JVM.
 *
 * - [CsvReader]: .csv/.tsv/.txt, detectando sozinho se o separador é `;` (o
 *   padrão do Excel em pt-BR), `,` ou tab, com aspas e aspas escapadas.
 * - [XlsxReader]: .xlsx de verdade (é um ZIP de XML), lido com `java.util.zip`
 *   + varredura de tags. Não usa Apache POI de propósito: POI passa de 10 MB no
 *   APK e o subconjunto necessário aqui (uma aba, células de texto e número) é
 *   pequeno. .xls binário antigo **não** é suportado — o usuário precisa salvar
 *   como .xlsx ou .csv.
 */
object CsvReader {

    private val CANDIDATE_DELIMITERS = listOf(';', ',', '\t', '|')

    fun read(text: String): List<List<String>> {
        val content = text.removePrefix("﻿")
        if (content.isBlank()) return emptyList()
        val delimiter = detectDelimiter(content)
        return splitRows(content, delimiter)
    }

    private fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().take(10).joinToString("\n")
        return CANDIDATE_DELIMITERS.maxByOrNull { candidate -> sample.count { it == candidate } }
            ?.takeIf { candidate -> sample.count { it == candidate } > 0 }
            ?: ';'
    }

    /** Parser de CSV com aspas (`"a;b"` é uma célula só; `""` dentro de aspas é uma aspa literal). */
    private fun splitRows(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endCell() {
            row.add(cell.toString().trim())
            cell.setLength(0)
        }

        fun endRow() {
            endCell()
            if (row.any { it.isNotBlank() }) rows.add(row.toList())
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> endCell()
                !inQuotes && (c == '\n') -> endRow()
                !inQuotes && c == '\r' -> Unit
                else -> cell.append(c)
            }
            i++
        }
        endRow()
        return rows
    }
}

object XlsxReader {

    private val SHEET_ENTRY = Regex("""xl/worksheets/sheet(\d+)\.xml""")
    private val ROW_BLOCK = Regex("""<row[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val CELL_BLOCK = Regex("""<c([^>]*)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL)
    private val VALUE_TAG = Regex("""<v[^>]*>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
    private val TEXT_TAG = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
    private val SHARED_ITEM = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
    private val REF_ATTR = Regex("""r="([A-Z]+)\d+"""")
    private val TYPE_ATTR = Regex("""t="([^"]+)"""")

    /** Lê a primeira aba da planilha como linhas de células já em texto. */
    fun read(input: InputStream): List<List<String>> {
        val files = readZipEntries(input)
        val sheetName = files.keys
            .mapNotNull { name -> SHEET_ENTRY.matchEntire(name)?.let { it.groupValues[1].toInt() to name } }
            .minByOrNull { it.first }?.second
            ?: return emptyList()

        val shared = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(files.getValue(sheetName), shared)
    }

    private fun readZipEntries(input: InputStream): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && (entry.name.endsWith(".xml"))) {
                    val buffer = ByteArrayOutputStream()
                    zip.copyTo(buffer)
                    result[entry.name] = buffer.toString(Charsets.UTF_8.name())
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun parseSharedStrings(xml: String): List<String> =
        SHARED_ITEM.findAll(xml).map { item ->
            TEXT_TAG.findAll(item.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
        }.toList()

    private fun parseSheet(xml: String, shared: List<String>): List<List<String>> =
        ROW_BLOCK.findAll(xml).map { rowMatch ->
            val cells = sortedMapOf<Int, String>()
            CELL_BLOCK.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                val attrs = cellMatch.groupValues[1]
                val body = cellMatch.groupValues[2]
                val column = REF_ATTR.find(attrs)?.groupValues?.get(1)?.let(::columnIndex) ?: cells.size
                cells[column] = cellValue(TYPE_ATTR.find(attrs)?.groupValues?.get(1), body, shared)
            }
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            (0 until width).map { cells[it].orEmpty() }
        }.filter { row -> row.any { it.isNotBlank() } }.toList()

    private fun cellValue(type: String?, body: String, shared: List<String>): String = when (type) {
        "s" -> VALUE_TAG.find(body)?.groupValues?.get(1)?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
        "inlineStr" -> TEXT_TAG.findAll(body).joinToString("") { unescape(it.groupValues[1]) }
        else -> VALUE_TAG.find(body)?.groupValues?.get(1)?.let(::unescape).orEmpty()
    }

    /** "A" -> 0, "B" -> 1, ..., "AA" -> 26. */
    private fun columnIndex(letters: String): Int =
        letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1

    private fun unescape(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")
}
