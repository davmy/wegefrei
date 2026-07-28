package de.wegefrei.app.feature.witness.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WitnessDetailsCompletionTest {
    @Test
    fun `isWitnessDetailsRecordComplete is true when all fields are present and email is valid`() {
        assertTrue(isWitnessDetailsRecordComplete("Max Mustermann", "Musterstraße 1", "max@example.com"))
    }

    @Test
    fun `isWitnessDetailsRecordComplete is false when name is blank`() {
        assertFalse(isWitnessDetailsRecordComplete("", "Musterstraße 1", "max@example.com"))
    }

    @Test
    fun `isWitnessDetailsRecordComplete is false when address is blank`() {
        assertFalse(isWitnessDetailsRecordComplete("Max Mustermann", "", "max@example.com"))
    }

    @Test
    fun `isWitnessDetailsRecordComplete is false when email is blank`() {
        assertFalse(isWitnessDetailsRecordComplete("Max Mustermann", "Musterstraße 1", ""))
    }

    @Test
    fun `isWitnessDetailsRecordComplete is false when email is present but invalid`() {
        assertFalse(isWitnessDetailsRecordComplete("Max Mustermann", "Musterstraße 1", "not-an-email"))
    }
}
