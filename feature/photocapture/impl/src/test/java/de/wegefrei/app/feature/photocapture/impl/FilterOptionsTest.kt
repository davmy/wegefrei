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
}
