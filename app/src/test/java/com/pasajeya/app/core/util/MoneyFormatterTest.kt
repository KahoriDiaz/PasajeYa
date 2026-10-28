package com.pasajeya.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun formatsCents() {
        val s0 = formatCents(0)
        val s5 = formatCents(5)
        val s105 = formatCents(105)
        val s150 = formatCents(150)
        val s1050 = formatCents(1050)
        assertEquals(true, s0.contains("0.00"))
        assertEquals(true, s5.contains("0.05"))
        assertEquals(true, s105.contains("1.05"))
        assertEquals(true, s150.contains("1.50"))
        assertEquals(true, s1050.contains("10.50"))
    }
}
