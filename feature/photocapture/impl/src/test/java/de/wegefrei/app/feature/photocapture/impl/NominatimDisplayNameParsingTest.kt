package de.wegefrei.app.feature.photocapture.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NominatimDisplayNameParsingTest {

    @Test
    fun `parseNominatimDisplayName extracts display_name from a successful response`() {
        val json = """{"display_name":"Alexanderplatz, Mitte, Berlin, Deutschland"}"""

        val result = parseNominatimDisplayName(json)

        assertEquals("Alexanderplatz, Mitte, Berlin, Deutschland", result)
    }

    @Test
    fun `parseNominatimDisplayName returns null when display_name is missing`() {
        val json = """{"error":"Unable to geocode"}"""

        val result = parseNominatimDisplayName(json)

        assertNull(result)
    }

    @Test
    fun `parseNominatimDisplayName returns null for malformed json`() {
        val json = "not json"

        val result = parseNominatimDisplayName(json)

        assertNull(result)
    }
}
