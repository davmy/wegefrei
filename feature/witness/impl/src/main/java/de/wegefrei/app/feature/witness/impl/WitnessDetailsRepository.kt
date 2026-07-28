package de.wegefrei.app.feature.witness.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.witnessDetailsDataStore by preferencesDataStore(name = "witness_details")

private val NAME_KEY = stringPreferencesKey("name")
private val ADDRESS_KEY = stringPreferencesKey("address")
private val EMAIL_KEY = stringPreferencesKey("email")

internal interface WitnessDetailsRepository {
    val name: Flow<String>
    val address: Flow<String>
    val email: Flow<String>

    suspend fun saveName(value: String)

    suspend fun saveAddress(value: String)

    suspend fun saveEmail(value: String)
}

internal class DataStoreWitnessDetailsRepository(
    context: Context,
) : WitnessDetailsRepository {
    private val appContext = context.applicationContext

    override val name: Flow<String> = appContext.witnessDetailsDataStore.data.map { it[NAME_KEY] ?: "" }
    override val address: Flow<String> = appContext.witnessDetailsDataStore.data.map { it[ADDRESS_KEY] ?: "" }
    override val email: Flow<String> = appContext.witnessDetailsDataStore.data.map { it[EMAIL_KEY] ?: "" }

    override suspend fun saveName(value: String) {
        appContext.witnessDetailsDataStore.edit { it[NAME_KEY] = value }
    }

    override suspend fun saveAddress(value: String) {
        appContext.witnessDetailsDataStore.edit { it[ADDRESS_KEY] = value }
    }

    override suspend fun saveEmail(value: String) {
        appContext.witnessDetailsDataStore.edit { it[EMAIL_KEY] = value }
    }
}

data class WitnessDetails(
    val name: String,
    val address: String,
    val email: String,
)

suspend fun readWitnessDetails(context: Context): WitnessDetails {
    val repository = DataStoreWitnessDetailsRepository(context)
    return WitnessDetails(
        name = repository.name.first(),
        address = repository.address.first(),
        email = repository.email.first(),
    )
}
