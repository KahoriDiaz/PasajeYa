package com.pasajeya.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigTest {
    @Test
    fun functionsRegionIsSouthAmericaEast1() {
        assertEquals("southamerica-east1", BuildConfig.FUNCTIONS_REGION)
    }
}
