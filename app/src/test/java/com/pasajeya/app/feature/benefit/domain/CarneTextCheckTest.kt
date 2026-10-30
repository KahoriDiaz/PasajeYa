package com.pasajeya.app.feature.benefit.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarneTextCheckTest {
    @Test
    fun `acepta el texto de la universidad con tildes y mayusculas`() {
        assertTrue(
            CarneTextCheck.looksLikeUniversityCard(
                "Universidad Católica de Santa María\nCARNÉ 20201234"
            )
        )
    }

    @Test
    fun `rechaza texto sin las palabras clave`() {
        assertFalse(
            CarneTextCheck.looksLikeUniversityCard("Universidad Nacional\nCARNE 20201234")
        )
    }

    @Test
    fun `rechaza texto vacio`() {
        assertFalse(CarneTextCheck.looksLikeUniversityCard(""))
    }
}
