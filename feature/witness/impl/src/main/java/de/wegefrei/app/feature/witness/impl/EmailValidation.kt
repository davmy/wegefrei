package de.wegefrei.app.feature.witness.impl

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

internal fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value)
