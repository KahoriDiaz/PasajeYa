package com.pasajeya.app.feature.voice

import com.pasajeya.app.feature.voice.domain.VoiceAnnouncementFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceAnnouncementFormatterTest {

    private val formatter = VoiceAnnouncementFormatter()

    @Test
    fun `uses only the first name and the plural age label`() {
        assertEquals("Ana, 21 años", formatter.format("ana maría", 21))
    }

    @Test
    fun `capitalizes an accented name`() {
        assertEquals("José, 21 años", formatter.format("JOSÉ", 21))
    }

    @Test
    fun `removes numbers and symbols from the name`() {
        assertEquals("Ana, 21 años", formatter.format("a1n@a", 21))
    }

    @Test
    fun `keeps the enye`() {
        assertEquals("Ñaño, 21 años", formatter.format("ñaño", 21))
    }

    @Test
    fun `uses the singular label for one year`() {
        assertEquals("Ana, 1 año", formatter.format("Ana", 1))
    }

    @Test
    fun `uses the plural label for zero years`() {
        assertEquals("Ana, 0 años", formatter.format("Ana", 0))
    }

    @Test
    fun `truncates a long name to twenty characters`() {
        val text = formatter.format("abcdefghijklmnopqrstuvwxyz", 21)

        assertEquals("Abcdefghijklmnopqrst, 21 años", text)
    }

    @Test
    fun `returns null when the name is empty`() {
        assertNull(formatter.format("   ", 21))
    }

    @Test
    fun `returns null when the name has no letters`() {
        assertNull(formatter.format("1234 @@@", 21))
    }
}
