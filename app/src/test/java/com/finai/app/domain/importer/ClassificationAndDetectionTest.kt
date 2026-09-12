package com.finai.app.domain.importer

import com.finai.app.data.local.entity.TransacaoEntity
import com.finai.app.domain.toEpochMillis
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regra local de categoria, duplicata e recorrência — tudo o que roda sem IA e sem rede. */
class ClassificationAndDetectionTest {

    private fun transacao(
        date: LocalDate,
        descricao: String,
        cents: Long,
        categoria: String = "Outros",
    ) = TransacaoEntity(
        id = 0,
        data = date.toEpochMillis(),
        descricao = descricao,
        valorCentavos = cents,
        categoria = categoria,
        contaOrigem = "fatura",
        recorrente = false,
        origem = "importado",
    )

    private fun entry(date: LocalDate, descricao: String, cents: Long, installment: Installment? = null) =
        ParsedEntry(date = date, description = descricao, amountCents = cents, installment = installment)

    // ── categoria ────────────────────────────────────────────────────────

    @Test
    fun `classifica estabelecimentos conhecidos sem IA`() {
        assertEquals("Alimentação", MerchantClassifier.classify("SUPERMERCADO EXTRA 2233").categoria)
        assertEquals("Alimentação", MerchantClassifier.classify("IFOOD *PIZZARIA").categoria)
        assertEquals("Transporte", MerchantClassifier.classify("UBER *TRIP SAO PAULO").categoria)
        assertEquals("Transporte", MerchantClassifier.classify("POSTO IPIRANGA LTDA").categoria)
        assertEquals("Assinaturas", MerchantClassifier.classify("NETFLIX.COM").categoria)
        assertEquals("Saúde", MerchantClassifier.classify("DROGARIA SAO PAULO").categoria)
        assertEquals("Moradia", MerchantClassifier.classify("ENEL DISTRIBUICAO SP").categoria)
        assertEquals("Lazer", MerchantClassifier.classify("CINEMARK SHOPPING").categoria)
    }

    @Test
    fun `estabelecimento desconhecido fica marcado para revisao`() {
        val suggestion = MerchantClassifier.classify("PGTO LOJA 4477 ZZ COMERCIO")

        assertEquals("Outros", suggestion.categoria)
        assertEquals(CategorySource.FALLBACK, suggestion.source)
        assertTrue(suggestion.needsReview)
    }

    @Test
    fun `normaliza estabelecimento removendo codigo de loja e adquirente`() {
        assertEquals("netflix.com", MerchantClassifier.normalizeMerchant("PAG*NETFLIX.COM 445566"))
        assertEquals("mercado sao joao", MerchantClassifier.normalizeMerchant("MERCADO SAO JOAO LTDA"))
    }

    @Test
    fun `so aceita nome de categoria que existe no app`() {
        assertEquals("Alimentação", MerchantClassifier.normalizeCategoryName("alimentacao"))
        assertEquals("Transporte", MerchantClassifier.normalizeCategoryName("TRANSPORTE"))
        assertEquals(null, MerchantClassifier.normalizeCategoryName("Viagem internacional"))
    }

    // ── duplicata ────────────────────────────────────────────────────────

    @Test
    fun `mesma data mesmo valor e mesmo estabelecimento e duplicata provavel`() {
        val existing = listOf(transacao(LocalDate.of(2026, 3, 12), "SUPERMERCADO EXTRA 2233", 18_990L))
        val verdict = DuplicateDetector.check(
            entry(LocalDate.of(2026, 3, 12), "SUPERMERCADO EXTRA LOJA 2233", 18_990L),
            existing,
            emptyList(),
        )

        assertEquals(DuplicateVerdict.LIKELY, verdict.verdict)
    }

    @Test
    fun `valor igual em dia proximo com descricao parecida fica ambiguo`() {
        val existing = listOf(transacao(LocalDate.of(2026, 3, 12), "PADARIA CENTRAL", 2_500L))
        val verdict = DuplicateDetector.check(
            entry(LocalDate.of(2026, 3, 14), "PADARIA CENTRAL LOJA 2", 2_500L),
            existing,
            emptyList(),
        )

        assertEquals(DuplicateVerdict.POSSIBLE, verdict.verdict)
    }

    @Test
    fun `mesmo arquivo listando a compra duas vezes marca a segunda`() {
        val primeiro = entry(LocalDate.of(2026, 3, 12), "SUPERMERCADO EXTRA", 18_990L)
        val verdict = DuplicateDetector.check(
            entry(LocalDate.of(2026, 3, 12), "SUPERMERCADO EXTRA", 18_990L),
            emptyList(),
            listOf(primeiro),
        )

        assertEquals(DuplicateVerdict.LIKELY, verdict.verdict)
    }

    @Test
    fun `compras diferentes no mesmo dia nao sao duplicata`() {
        val existing = listOf(transacao(LocalDate.of(2026, 3, 12), "UBER TRIP", 2_345L))
        val verdict = DuplicateDetector.check(
            entry(LocalDate.of(2026, 3, 12), "SUPERMERCADO EXTRA", 18_990L),
            existing,
            emptyList(),
        )

        assertEquals(DuplicateVerdict.NONE, verdict.verdict)
    }

    // ── recorrência ──────────────────────────────────────────────────────

    @Test
    fun `assinatura conhecida e recorrencia provavel na primeira vez`() {
        val verdict = RecurrenceDetector.check(entry(LocalDate.of(2026, 3, 14), "NETFLIX.COM", 5_590L), emptyList(), emptyList())

        assertEquals(RecurrenceVerdict.LIKELY, verdict.verdict)
    }

    @Test
    fun `mesma cobranca em dois meses anteriores vira recorrencia provavel`() {
        val existing = listOf(
            transacao(LocalDate.of(2026, 1, 10), "ACADEMIA VIDA ATIVA", 12_000L),
            transacao(LocalDate.of(2026, 2, 10), "ACADEMIA VIDA ATIVA", 12_000L),
        )
        val verdict = RecurrenceDetector.check(
            entry(LocalDate.of(2026, 3, 10), "ACADEMIA VIDA ATIVA", 12_000L),
            existing,
            emptyList(),
        )

        assertEquals(RecurrenceVerdict.LIKELY, verdict.verdict)
    }

    @Test
    fun `um mes anterior so levanta suspeita`() {
        val existing = listOf(transacao(LocalDate.of(2026, 2, 10), "ESCOLA DE IDIOMAS ZR", 33_000L))
        val verdict = RecurrenceDetector.check(
            entry(LocalDate.of(2026, 3, 10), "ESCOLA DE IDIOMAS ZR", 33_000L),
            existing,
            emptyList(),
        )

        assertEquals(RecurrenceVerdict.POSSIBLE, verdict.verdict)
    }

    @Test
    fun `parcela nao e assinatura`() {
        val verdict = RecurrenceDetector.check(
            entry(LocalDate.of(2026, 3, 15), "MAGAZINE LUIZA", 12_990L, Installment(3, 10)),
            emptyList(),
            emptyList(),
        )

        assertEquals(RecurrenceVerdict.NONE, verdict.verdict)
        assertFalse(verdict.verdict != RecurrenceVerdict.NONE)
    }
}
