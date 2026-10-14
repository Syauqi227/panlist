package com.metrolist.desktop

import com.metrolist.desktop.ui.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testParseStandardLrc() {
        val sampleLrc = """
            [00:12.34] Pertama kali ku melihatmu
            [00:15.50] Rasa di dada mulai bergetar
            [01:02.100] Reff: Dan ku tahu ini cinta
            [02:00.00] Outro lagu
        """.trimIndent()

        val entries = LrcParser.parse(sampleLrc)
        assertEquals(4, entries.size)

        assertEquals(12340L, entries[0].timeMs)
        assertEquals("Pertama kali ku melihatmu", entries[0].text)

        assertEquals(15500L, entries[1].timeMs)
        assertEquals("Rasa di dada mulai bergetar", entries[1].text)

        assertEquals(62100L, entries[2].timeMs)
        assertEquals("Reff: Dan ku tahu ini cinta", entries[2].text)

        assertEquals(120000L, entries[3].timeMs)
        assertEquals("Outro lagu", entries[3].text)
    }

    @Test
    fun testParseEmptyOrInvalidLrc() {
        val plainText = """
            Ini hanya lirik biasa
            Tanpa penanda waktu sama sekali
        """.trimIndent()

        val entries = LrcParser.parse(plainText)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testParseSingleDigitSecondsFraction() {
        val lrc = "[00:05.3] Halo dunia"
        val entries = LrcParser.parse(lrc)
        assertEquals(1, entries.size)
        assertEquals(5300L, entries[0].timeMs)
        assertEquals("Halo dunia", entries[0].text)
    }

    @Test
    fun testStripTagsAndMetadata() {
        val lrc = """
            [00:00.00] Gym Class Heroes - Stereo Hearts
            [00:00.40] Written by: The Hit List
            [00:00.72] {agent:v1}My <00:00.80>heart's a <00:01.00>stereo{bg}
            [00:03.37] {agent:v2}It beats for you
        """.trimIndent()

        val entries = LrcParser.parse(lrc)
        assertEquals(2, entries.size)
        assertEquals(720L, entries[0].timeMs)
        assertEquals("My heart's a stereo", entries[0].text)
        assertEquals(3370L, entries[1].timeMs)
        assertEquals("It beats for you", entries[1].text)
    }
}
