package de.wegefrei.app.ui.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val MAX_PHOTOS = 5

class PhotoCaptureViewModel : ViewModel() {

    private val _photoUris = MutableStateFlow<List<Uri>>(emptyList())
    val photoUris: StateFlow<List<Uri>> = _photoUris.asStateFlow()

    fun onImagesPicked(uris: List<Uri>) {
        _photoUris.value = (_photoUris.value + uris).take(MAX_PHOTOS)
    }

    fun onPhotoCaptured(uri: Uri) {
        if (_photoUris.value.size < MAX_PHOTOS) {
            _photoUris.value = _photoUris.value + uri
        }
    }

    fun onPhotoRemoved(uri: Uri) {
        _photoUris.value = _photoUris.value - uri
    }
}
