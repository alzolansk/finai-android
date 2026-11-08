package com.finai.app.domain

import com.finai.app.data.model.Categorias
import com.finai.app.domain.importer.MerchantClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 0 (planning-v2): uma lista de categorias só, com as de sistema à parte. */
class CategoriasTest {
    @Test fun listHasTenCategoriesWithoutDuplicates() {
        assertEquals(10, Categorias.all.size)
        assertEquals(Categorias.all.size, Categorias.all.toSet().size)
        assertTrue("Assinaturas" in Categorias.all)
    }

    @Test fun systemCategoriesStayOutOfTheUserList() {
        assertTrue(Categorias.system.isNotEmpty())
        assertTrue(Categorias.system.none { it in Categorias.all })
        assertTrue(Categorias.isSystem(DebtSchedule.PAYMENT_CATEGORY))
        assertTrue(Categorias.isSystem(ADJUSTMENT_LABEL))
        assertFalse(Categorias.isSystem("Lazer"))
    }

    @Test fun entryFormAndImportReadTheSameList() {
        assertEquals(Categorias.all, TransactionEntry.categories)
    }

    @Test fun everyCategoryTheClassifierProducesExists() {
        listOf("ifood pedido", "uber viagem", "netflix", "drogasil", "cinema", "aluguel", "udemy curso", "barbearia centro", "loja xyz")
            .forEach { assertTrue(MerchantClassifier.classify(it).categoria in Categorias.all) }
        assertEquals("Educação", MerchantClassifier.classify("Udemy curso").categoria)
        assertEquals("Serviços", MerchantClassifier.classify("Barbearia Centro").categoria)
        assertNotNull(MerchantClassifier.normalizeCategoryName("educacao"))
        assertNotNull(MerchantClassifier.normalizeCategoryName("Serviços"))
    }
}
