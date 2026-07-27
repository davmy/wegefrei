package de.wegefrei.app.feature.photocapture.impl

import android.net.Uri
import androidx.lifecycle.ViewModel
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val MAX_PHOTOS = 5

internal class PhotoCaptureViewModel : ViewModel() {

    private val _photoUris = MutableStateFlow<List<Uri>>(emptyList())
    val photoUris: StateFlow<List<Uri>> = _photoUris.asStateFlow()

    private val _addressText = MutableStateFlow("")
    val addressText: StateFlow<String> = _addressText.asStateFlow()

    private val _licensePlateText = MutableStateFlow("")
    val licensePlateText: StateFlow<String> = _licensePlateText.asStateFlow()

    private val _makeText = MutableStateFlow("")
    val makeText: StateFlow<String> = _makeText.asStateFlow()

    private val _colorText = MutableStateFlow("")
    val colorText: StateFlow<String> = _colorText.asStateFlow()

    private val _violationText = MutableStateFlow("")
    val violationText: StateFlow<String> = _violationText.asStateFlow()

    private val _obstructionText = MutableStateFlow("")
    val obstructionText: StateFlow<String> = _obstructionText.asStateFlow()

    private val _incidentDateTime = MutableStateFlow(LocalDateTime.now().withSecond(0).withNano(0))
    val incidentDateTime: StateFlow<LocalDateTime> = _incidentDateTime.asStateFlow()

    private var hasUserEditedIncidentDateTime = false

    private var hasUserEditedAddress = false

    fun onImagesPicked(uris: List<Uri>) {
        val newUris = uris.filter { it !in _photoUris.value }.distinct()
        _photoUris.value = (_photoUris.value + newUris).take(MAX_PHOTOS)
    }

    fun onPhotoCaptured(uri: Uri) {
        if (_photoUris.value.size < MAX_PHOTOS && uri !in _photoUris.value) {
            _photoUris.value = _photoUris.value + uri
        }
    }

    fun onPhotoRemoved(index: Int) {
        _photoUris.value = _photoUris.value.toMutableList().apply { removeAt(index) }
    }

    fun onAddressTextChanged(text: String) {
        hasUserEditedAddress = true
        _addressText.value = text
    }

    fun onAddressAutoDetected(text: String) {
        if (!hasUserEditedAddress) {
            _addressText.value = text
        }
    }

    fun onCurrentLocationAddressReceived(text: String) {
        hasUserEditedAddress = true
        _addressText.value = text
    }

    fun onLicensePlateTextChanged(text: String) {
        _licensePlateText.value = text
    }

    fun onMakeTextChanged(text: String) {
        _makeText.value = text
    }

    fun onColorTextChanged(text: String) {
        _colorText.value = text
    }

    fun onViolationTextChanged(text: String) {
        _violationText.value = text
    }

    fun onObstructionTextChanged(text: String) {
        _obstructionText.value = text
    }

    fun onIncidentDateTimeChanged(dateTime: LocalDateTime) {
        hasUserEditedIncidentDateTime = true
        _incidentDateTime.value = dateTime.withSecond(0).withNano(0)
    }

    fun onPhotoTimestampsExtracted(timestamps: List<LocalDateTime>) {
        if (hasUserEditedIncidentDateTime) return
        _incidentDateTime.value = (timestamps.minOrNull() ?: LocalDateTime.now()).withSecond(0).withNano(0)
    }
}
