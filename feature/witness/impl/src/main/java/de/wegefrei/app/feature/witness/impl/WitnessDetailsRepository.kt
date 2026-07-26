package de.wegefrei.app.feature.witness.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.witnessDetailsDataStore by preferencesDataStore(name = "witness_details")

private val NAME_KEY = stringPreferencesKey("name")
private val ADDRESS_KEY = stringPreferencesKey("address")
private val EMAIL_KEY = stringPreferencesKey("email")

interface WitnessDetailsRepository {
    val name: Flow<String>
    val address: Flow<String>
    val email: Flow<String>
    suspend fun saveName(value: String)
    suspend fun saveAddress(value: String)
    suspend fun saveEmail(value: String)
}

internal class DataStoreWitnessDetailsRepository(
    private val context: Context,
) : WitnessDetailsRepository {

    override val name: Flow<String> = context.witnessDetailsDataStore.data.map { it[NAME_KEY] ?: "" }
    override val address: Flow<String> = context.witnessDetailsDataStore.data.map { it[ADDRESS_KEY] ?: "" }
    override val email: Flow<String> = context.witnessDetailsDataStore.data.map { it[EMAIL_KEY] ?: "" }

    override suspend fun saveName(value: String) {
        context.witnessDetailsDataStore.edit { it[NAME_KEY] = value }
    }

    override suspend fun saveAddress(value: String) {
        context.witnessDetailsDataStore.edit { it[ADDRESS_KEY] = value }
    }

    override suspend fun saveEmail(value: String) {
        context.witnessDetailsDataStore.edit { it[EMAIL_KEY] = value }
    }
}
