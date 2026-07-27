package de.wegefrei.app.feature.photocapture.impl

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterCarBrandsTest {

    @Test
    fun `blank query returns all brands`() {
        val result = filterCarBrands("")

        assertEquals(25, result.size)
    }

    @Test
    fun `substring match is case-insensitive and returns multiple matches`() {
        val result = filterCarBrands("Vol")

        assertEquals(listOf("Volkswagen", "Volvo"), result)
    }

    @Test
    fun `matching is case-insensitive`() {
        val result = filterCarBrands("BMW")

        assertEquals(listOf("BMW"), result)
    }

    @Test
    fun `matches substrings not just prefixes`() {
        val result = filterCarBrands("at")

        assertEquals(listOf("Seat", "Fiat"), result)
    }

    @Test
    fun `query matching nothing returns empty list`() {
        val result = filterCarBrands("zzz")

        assertEquals(emptyList<String>(), result)
    }
}
