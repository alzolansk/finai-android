package com.finai.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contas do cabeçalho 4a: transição dos títulos de 10 a 40 dp e vidro acima de 8 dp. */
class PageHeaderMotionTest {

    @Test
    fun titleTransitionRunsFromTenToFortyDp() {
        assertEquals(0f, PageHeaderMotion.progress(0f), 0f)
        assertEquals(0f, PageHeaderMotion.progress(10f), 0f)
        assertEquals(0.5f, PageHeaderMotion.progress(25f), 1e-6f)
        assertEquals(1f, PageHeaderMotion.progress(40f), 0f)
        assertEquals(1f, PageHeaderMotion.progress(400f), 0f)
        // Lista passou do título grande: headerScrollPx devolve Float.MAX_VALUE.
        assertEquals(1f, PageHeaderMotion.progress(Float.MAX_VALUE), 0f)
    }

    @Test
    fun progressFollowsScrollBothWays() {
        // Sem estado interno: voltar a rolagem devolve exatamente o mesmo p.
        val down = listOf(12f, 20f, 31f, 39f).map(PageHeaderMotion::progress)
        val up = listOf(39f, 31f, 20f, 12f).map(PageHeaderMotion::progress)
        assertEquals(down, up.reversed())
        assertTrue(down.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun titlesCrossFadeWithOppositeShift() {
        val p = PageHeaderMotion.progress(25f)
        assertEquals(0.5f, PageHeaderMotion.bigTitleAlpha(p), 1e-6f)
        assertEquals(0.5f, PageHeaderMotion.compactTitleAlpha(p), 1e-6f)
        assertEquals(-4f, PageHeaderMotion.bigTitleShiftDp(p), 1e-6f)
        assertEquals(4f, PageHeaderMotion.compactTitleShiftDp(p), 1e-6f)
        assertEquals(0f, PageHeaderMotion.bigTitleShiftDp(0f), 0f)
        assertEquals(8f, PageHeaderMotion.compactTitleShiftDp(0f), 0f)
        assertEquals(-8f, PageHeaderMotion.bigTitleShiftDp(1f), 0f)
        assertEquals(0f, PageHeaderMotion.compactTitleShiftDp(1f), 0f)
    }

    @Test
    fun glassTurnsOnAfterEightDp() {
        assertFalse(PageHeaderMotion.isScrolled(0f))
        assertFalse(PageHeaderMotion.isScrolled(8f))
        assertTrue(PageHeaderMotion.isScrolled(8.5f))
        assertTrue(PageHeaderMotion.isScrolled(Float.MAX_VALUE))
    }
}
