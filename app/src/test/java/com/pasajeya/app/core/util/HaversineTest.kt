package com.pasajeya.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HaversineTest {
    @Test
    fun `un grado de latitud mide unos 111 km`() {
        val meters = Haversine.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, meters, 100.0)
    }

    @Test
    fun `la distancia entre el mismo punto es cero`() {
        assertEquals(0.0, Haversine.distanceMeters(-16.4, -71.5, -16.4, -71.5), 0.0001)
    }

    @Test
    fun `es simetrica`() {
        val ab = Haversine.distanceMeters(-16.39, -71.53, -16.41, -71.51)
        val ba = Haversine.distanceMeters(-16.41, -71.51, -16.39, -71.53)
        assertEquals(ab, ba, 0.0001)
    }
}
