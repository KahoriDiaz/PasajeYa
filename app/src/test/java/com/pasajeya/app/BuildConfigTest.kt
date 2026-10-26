package com.pasajeya.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun debugBuildUsesFirebaseEmulators() {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATORS)
    }

    @Test
    fun emulatorHostIsConfigured() {
        assertTrue(BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank())
    }

    @Test
    fun functionsRegionIsSouthAmericaEast1() {
        assertEquals("southamerica-east1", BuildConfig.FUNCTIONS_REGION)
    }
}
