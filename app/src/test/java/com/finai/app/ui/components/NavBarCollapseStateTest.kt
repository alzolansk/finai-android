package com.finai.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras de "Recolher ao rolar" do playbook 3a, em dp (densidade 1). */
class NavBarCollapseStateTest {

    private fun state() = NavBarCollapseState(thresholdPx = 6f, collapseAfterPx = 60f, topZonePx = 30f)

    private fun NavBarCollapseState.scroll(totalDp: Float, step: Float = 1f) {
        var left = totalDp
        while (kotlin.math.abs(left) > 0f) {
            val d = if (left > 0) minOf(step, left) else maxOf(-step, left)
            onScrolled(d)
            left -= d
        }
    }

    @Test
    fun doesNotCollapseBeforeSixtyDp() {
        val s = state()
        s.scroll(59f)
        assertFalse(s.collapsed)
        s.scroll(8f)
        assertTrue(s.collapsed)
    }

    @Test
    fun scrollingUpMoreThanSixDpExpands() {
        val s = state()
        s.scroll(200f)
        assertTrue(s.collapsed)
        s.scroll(-6f)
        assertTrue("6 dp não basta, tem que passar", s.collapsed)
        s.scroll(-1f)
        assertFalse(s.collapsed)
    }

    @Test
    fun slowJitterNeverFlips() {
        val s = state()
        s.scroll(200f)
        assertTrue(s.collapsed)
        // Rolagem lenta e indecisa: o dedo vai e volta menos de 6 dp de cada vez.
        repeat(50) {
            s.scroll(-5f, step = 0.5f)
            s.scroll(4f, step = 0.5f)
        }
        assertTrue(s.collapsed)
    }

    @Test
    fun nearTheTopStaysExpanded() {
        val s = state()
        s.scroll(200f)
        assertTrue(s.collapsed)
        s.expand()
        s.scroll(-175f)
        // A 25 dp do topo: expandida, e descer um pouco não recolhe.
        s.scroll(4f)
        assertFalse(s.collapsed)
    }

    @Test
    fun overscrollAtTheTopDoesNotCount() {
        val s = state()
        s.scroll(-100f)
        s.scroll(40f)
        assertFalse("o topo é zero, não -100", s.collapsed)
    }

    @Test
    fun resetExpandsAndRestartsFromTop() {
        val s = state()
        s.scroll(300f)
        assertTrue(s.collapsed)
        s.reset()
        assertFalse(s.collapsed)
        s.scroll(30f)
        assertFalse(s.collapsed)
    }

    @Test
    fun disabledNeverCollapses() {
        val s = state()
        s.enabled = false
        s.scroll(500f)
        assertFalse(s.collapsed)
    }

    @Test
    fun disablingWhileCollapsedExpands() {
        val s = state()
        s.scroll(500f)
        assertTrue(s.collapsed)
        s.enabled = false
        assertFalse(s.collapsed)
    }
}
