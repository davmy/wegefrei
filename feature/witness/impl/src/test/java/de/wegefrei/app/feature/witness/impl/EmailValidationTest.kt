package de.wegefrei.app.feature.witness.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailValidationTest {
    @Test
    fun `isValidEmail accepts a plausible email address`() {
        assertTrue(isValidEmail("max@example.com"))
    }

    @Test
    fun `isValidEmail rejects a value without an at sign`() {
        assertFalse(isValidEmail("max.example.com"))
    }

    @Test
    fun `isValidEmail rejects a value without a domain`() {
        assertFalse(isValidEmail("max@example"))
    }

    @Test
    fun `isValidEmail rejects a blank value`() {
        assertFalse(isValidEmail(""))
    }
}
