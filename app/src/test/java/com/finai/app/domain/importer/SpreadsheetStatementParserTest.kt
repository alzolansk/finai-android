package com.finai.app.domain.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Caminho de planilha: .csv exportado por banco e .xlsx de verdade (ZIP de XML). */
class SpreadsheetStatementParserTest {

    private val reference = LocalDate.of(2026, 3, 25)

    @Test
    fun `csv com ponto e virgula e cabecalho em portugues`() {
        val csv = """
            Data;Descrição;Valor;Categoria
            12/03/2026;SUPERMERCADO EXTRA;189,90;Alimentação
            14/03/2026;NETFLIX.COM;55,90;
            15/03/2026;MAGAZINE LUIZA 03/10;129,90;
        """.trimIndent()

        val result = SpreadsheetStatementParser.parse(CsvReader.read(csv), reference)

        assertEquals(3, result.entries.size)
        assertEquals(LocalDate.of(2026, 3, 12), result.entries[0].date)
        assertEquals(18_990L, result.entries[0].amountCents)
        assertEquals("Alimentação", result.entries[0].categoryHint)
        assertEquals(Installment(3, 10), result.entries[2].installment)
    }

    @Test
    fun `csv com virgula e aspas preserva a descricao inteira`() {
        val csv = """
            date,description,amount
            2026-03-12,"MERCADO SAO JOAO, LTDA",189.90
            2026-03-14,UBER TRIP,23.45
        """.trimIndent()

        val result = SpreadsheetStatementParser.parse(CsvReader.read(csv), reference)

        assertEquals(2, result.entries.size)
        assertEquals("MERCADO SAO JOAO, LTDA", result.entries[0].description)
        assertEquals(18_990L, result.entries[0].amountCents)
    }

    @Test
    fun `planilha de banco com gasto negativo tem o sinal invertido para a convencao do app`() {
        val csv = """
            Data;Histórico;Valor
            12/03/2026;COMPRA SUPERMERCADO;-189,90
            14/03/2026;COMPRA FARMACIA;-55,90
            20/03/2026;SALARIO;3500,00
        """.trimIndent()

        val result = SpreadsheetStatementParser.parse(CsvReader.read(csv), reference)

        // Maioria negativa => arquivo usa "gasto negativo"; o app usa gasto positivo.
        assertEquals(18_990L, result.entries[0].amountCents)
        assertEquals(5_590L, result.entries[1].amountCents)
        assertEquals(-350_000L, result.entries[2].amountCents)
    }

    @Test
    fun `colunas separadas de debito e credito`() {
        val csv = """
            Data;Descrição;Débito;Crédito
            12/03/2026;SUPERMERCADO;189,90;
            18/03/2026;ESTORNO COMPRA;;89,90
        """.trimIndent()

        val result = SpreadsheetStatementParser.parse(CsvReader.read(csv), reference)

        assertEquals(18_990L, result.entries[0].amountCents)
        assertEquals(-8_990L, result.entries[1].amountCents)
    }

    @Test
    fun `planilha sem cabecalho cai na heuristica por linha`() {
        val csv = """
            12/03/2026;SUPERMERCADO EXTRA;189,90
            14/03/2026;NETFLIX.COM;55,90
        """.trimIndent()

        val result = SpreadsheetStatementParser.parse(CsvReader.read(csv), reference)

        assertEquals(2, result.entries.size)
        assertEquals("SUPERMERCADO EXTRA", result.entries[0].description)
    }

    @Test
    fun `linha sem data nao vira lancamento`() {
        val csv = """
            Data;Descrição;Valor
            12/03/2026;SUPERMERCADO;189,90
            ;SALDO ANTERIOR;1.000,00
        """.trimIndent()

        val result = SpreadsheetStatementParser.parse(CsvReader.read(csv), reference)

        assertEquals(1, result.entries.size)
        assertEquals(1, result.ignoredLines)
    }

    @Test
    fun `xlsx real e lido com strings compartilhadas e data em numero de serie`() {
        val bytes = buildXlsx(
            sharedStrings = listOf("Data", "Descrição", "Valor", "SUPERMERCADO EXTRA", "NETFLIX.COM"),
            rows = listOf(
                listOf(Cell.Shared(0), Cell.Shared(1), Cell.Shared(2)),
                // 46093 = 12/03/2026 no calendário serial do Excel
                listOf(Cell.Number("46093"), Cell.Shared(3), Cell.Number("189.9")),
                listOf(Cell.Number("46095"), Cell.Shared(4), Cell.Number("55.9")),
            ),
        )

        val rows = XlsxReader.read(ByteArrayInputStream(bytes))
        val result = SpreadsheetStatementParser.parse(rows, reference)

        assertEquals(2, result.entries.size)
        assertEquals(LocalDate.of(2026, 3, 12), result.entries[0].date)
        assertEquals("SUPERMERCADO EXTRA", result.entries[0].description)
        assertEquals(18_990L, result.entries[0].amountCents)
        assertTrue(result.entries[1].description.contains("NETFLIX"))
    }

    // ── helper: monta um .xlsx mínimo mas válido para o leitor ──────────

    private sealed interface Cell {
        data class Shared(val index: Int) : Cell
        data class Number(val value: String) : Cell
    }

    private fun buildXlsx(sharedStrings: List<String>, rows: List<List<Cell>>): ByteArray {
        val sharedXml = buildString {
            append("""<?xml version="1.0"?><sst xmlns="x">""")
            sharedStrings.forEach { append("<si><t>").append(it.replace("&", "&amp;")).append("</t></si>") }
            append("</sst>")
        }
        val sheetXml = buildString {
            append("""<?xml version="1.0"?><worksheet xmlns="x"><sheetData>""")
            rows.forEachIndexed { rowIndex, row ->
                append("""<row r="${rowIndex + 1}">""")
                row.forEachIndexed { columnIndex, cell ->
                    val ref = "${('A' + columnIndex)}${rowIndex + 1}"
                    when (cell) {
                        is Cell.Shared -> append("""<c r="$ref" t="s"><v>${cell.index}</v></c>""")
                        is Cell.Number -> append("""<c r="$ref"><v>${cell.value}</v></c>""")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val output = ByteArrayOutputStream()
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
