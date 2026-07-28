package de.wegefrei.app.feature.witness.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeWitnessDetailsRepository(
    initialName: String = "",
    initialAddress: String = "",
    initialEmail: String = "",
) : WitnessDetailsRepository {
    private val nameFlow = MutableStateFlow(initialName)
    private val addressFlow = MutableStateFlow(initialAddress)
    private val emailFlow = MutableStateFlow(initialEmail)

    override val name: Flow<String> = nameFlow
    override val address: Flow<String> = addressFlow
    override val email: Flow<String> = emailFlow

    var savedName: String? = null
        private set
    var savedAddress: String? = null
        private set
    var savedEmail: String? = null
        private set

    override suspend fun saveName(value: String) {
        savedName = value
        nameFlow.value = value
    }

    override suspend fun saveAddress(value: String) {
        savedAddress = value
        addressFlow.value = value
    }

    override suspend fun saveEmail(value: String) {
        savedEmail = value
        emailFlow.value = value
    }
}
