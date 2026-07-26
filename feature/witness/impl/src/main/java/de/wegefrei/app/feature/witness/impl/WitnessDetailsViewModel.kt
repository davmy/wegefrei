package de.wegefrei.app.feature.witness.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class WitnessDetailsViewModel(
    private val repository: WitnessDetailsRepository,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    init {
        viewModelScope.launch { repository.name.collect { _name.value = it } }
        viewModelScope.launch { repository.address.collect { _address.value = it } }
        viewModelScope.launch { repository.email.collect { _email.value = it } }
    }

    fun onNameChanged(value: String) {
        _name.value = value
        viewModelScope.launch { repository.saveName(value) }
    }

    fun onAddressChanged(value: String) {
        _address.value = value
        viewModelScope.launch { repository.saveAddress(value) }
    }

    fun onEmailChanged(value: String) {
        _email.value = value
        viewModelScope.launch { repository.saveEmail(value) }
    }
}
