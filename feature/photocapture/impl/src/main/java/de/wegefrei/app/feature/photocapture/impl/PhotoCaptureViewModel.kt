package de.wegefrei.app.feature.photocapture.impl

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val MAX_PHOTOS = 5

internal class PhotoCaptureViewModel : ViewModel() {

    private val _photoUris = MutableStateFlow<List<Uri>>(emptyList())
    val photoUris: StateFlow<List<Uri>> = _photoUris.asStateFlow()

    private val _addressText = MutableStateFlow("")
    val addressText: StateFlow<String> = _addressText.asStateFlow()

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
}
