package de.wegefrei.app.feature.witness.impl

import android.content.Context
import kotlinx.coroutines.flow.first

internal fun isWitnessDetailsRecordComplete(name: String, address: String, email: String): Boolean =
    name.isNotBlank() && address.isNotBlank() && email.isNotBlank() && isValidEmail(email)

suspend fun areWitnessDetailsComplete(context: Context): Boolean {
    val repository = DataStoreWitnessDetailsRepository(context.applicationContext)
    return isWitnessDetailsRecordComplete(
        name = repository.name.first(),
        address = repository.address.first(),
        email = repository.email.first(),
    )
}
