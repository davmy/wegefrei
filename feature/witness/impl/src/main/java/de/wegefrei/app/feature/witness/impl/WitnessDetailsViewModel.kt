package de.wegefrei.app.feature.witness.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    // Not persisted or prefilled — the responsible Ordnungsamt differs per report, unlike
    // the reporter's own name/address/email above.
    private val _ordnungsamtEmail = MutableStateFlow("")
    val ordnungsamtEmail: StateFlow<String> = _ordnungsamtEmail.asStateFlow()

    init {
        viewModelScope.launch { _name.value = repository.name.first() }
        viewModelScope.launch { _address.value = repository.address.first() }
        viewModelScope.launch { _email.value = repository.email.first() }
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

    fun onOrdnungsamtEmailChanged(value: String) {
        _ordnungsamtEmail.value = value
    }
}
