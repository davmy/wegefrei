package de.wegefrei.app.feature.photocapture.impl

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterOptionsTest {
    @Test
    fun `blank query returns all brands`() {
        val result = filterOptions("", germanCarBrands)

        assertEquals(25, result.size)
    }

    @Test
    fun `substring match is case-insensitive and returns multiple matches`() {
        val result = filterOptions("Vol", germanCarBrands)

        assertEquals(listOf("Volkswagen", "Volvo"), result)
    }

    @Test
    fun `matching is case-insensitive`() {
        val result = filterOptions("BMW", germanCarBrands)

        assertEquals(listOf("BMW"), result)
    }

    @Test
    fun `matches substrings not just prefixes`() {
        val result = filterOptions("at", germanCarBrands)

        assertEquals(listOf("Seat", "Fiat"), result)
    }

    @Test
    fun `query matching nothing returns empty list`() {
        val result = filterOptions("zzz", germanCarBrands)

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `blank query returns all colors`() {
        val result = filterOptions("", germanCarColors)

        assertEquals(14, result.size)
    }

    @Test
    fun `color substring match is case-insensitive`() {
        val result = filterOptions("blau", germanCarColors)

        assertEquals(listOf("Blau"), result)
    }

    @Test
    fun `blank query returns all violations`() {
        val result = filterOptions("", germanTrafficViolations)

        assertEquals(11, result.size)
    }

    @Test
    fun `violation substring match is case-insensitive`() {
        val result = filterOptions("radweg", germanTrafficViolations)

        assertEquals(
            listOf(
                "Parken auf Radweg (Zeichen 237)",
                "Parken auf Geh- und Radweg (Zeichen 240 / 241)",
                "Parken auf unbeschildertem Radweg",
            ),
            result,
        )
    }

    @Test
    fun `trafficViolationOptions returns Parken options unchanged`() {
        val result = trafficViolationOptions("Parken")

        assertEquals(germanTrafficViolations, result)
    }

    @Test
    fun `trafficViolationOptions replaces the leading Parken with Halten`() {
        val result = trafficViolationOptions("Halten")

        assertEquals(
            listOf(
                "Halten auf Gehweg",
                "Halten im absoluten Halteverbot",
                "Halten weniger als 5 Meter von Kreuzung",
                "Halten weniger als 5 Meter von Einmündung",
                "Halten auf Radweg (Zeichen 237)",
                "Halten auf Radfahrstreifen",
                "Halten auf Geh- und Radweg (Zeichen 240 / 241)",
                "Halten auf Sperrfläche",
                "Halten auf unbeschildertem Radweg",
                "Halten in verkehrsberuhigten Bereich (Zeichen 325.1)",
                "Halten in eingeschränktem Halteverbot (Zeichen 286)",
            ),
            result,
        )
    }

    @Test
    fun `filterOptionIndices matches against the committed option`() {
        val result =
            filterOptionIndices(
                query = "Vol",
                options = germanCarBrands,
                displayOptions = germanCarBrands,
            )

        assertEquals(listOf(germanCarBrands.indexOf("Volkswagen"), germanCarBrands.indexOf("Volvo")), result)
    }

    @Test
    fun `filterOptionIndices matches against the display option when it differs from the committed value`() {
        val options = listOf("Parken auf Gehweg", "Parken auf Radweg (Zeichen 237)")
        val displayOptions = listOf("Parking on the sidewalk", "Parking on a bike lane (sign 237)")

        val result = filterOptionIndices(query = "sidewalk", options = options, displayOptions = displayOptions)

        assertEquals(listOf(0), result)
    }

    @Test
    fun `filterOptionIndices returns no matches when the query matches neither option nor display text`() {
        val options = listOf("Parken auf Gehweg")
        val displayOptions = listOf("Parking on the sidewalk")

        val result = filterOptionIndices(query = "zzz", options = options, displayOptions = displayOptions)

        assertEquals(emptyList<Int>(), result)
    }
}
