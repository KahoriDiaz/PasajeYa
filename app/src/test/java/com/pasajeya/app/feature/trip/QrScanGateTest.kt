package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.trip.domain.QrScanGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScanGateTest {

    @Test
    fun `rejects blank values and repeated frames inside the window`() {
        var now = 1_000L
        val gate = QrScanGate(clock = { now })

        assertFalse(gate.accept("   "))
        assertTrue(gate.accept("qr-a"))
        assertFalse(gate.accept(" qr-a "))
        now += 3_001
        assertTrue(gate.accept("qr-a"))
        assertTrue(gate.accept("qr-b"))
    }
}
