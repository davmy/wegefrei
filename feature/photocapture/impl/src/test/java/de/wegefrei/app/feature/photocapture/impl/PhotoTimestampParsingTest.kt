package de.wegefrei.app.feature.photocapture.impl

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoTimestampParsingTest {

    @Test
    fun `parseExifDateTime parses a valid timestamp and truncates seconds`() {
        val result = parseExifDateTime("2026:07:26 10:15:30")

        assertEquals(LocalDateTime.of(2026, 7, 26, 10, 15, 0, 0), result)
    }

    @Test
    fun `parseExifDateTime returns null for an empty string`() {
        val result = parseExifDateTime("")

        assertNull(result)
    }

    @Test
    fun `parseExifDateTime returns null for a blank placeholder value`() {
        val result = parseExifDateTime("    :  :     :  :  ")

        assertNull(result)
    }

    @Test
    fun `parseExifDateTime returns null for a malformed value`() {
        val result = parseExifDateTime("2026-07-26T10:15:30")

        assertNull(result)
    }

    @Test
    fun `parseExifDateTime returns null for a null value`() {
        val result = parseExifDateTime(null)

        assertNull(result)
    }
}
