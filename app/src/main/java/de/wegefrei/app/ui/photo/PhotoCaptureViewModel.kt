package de.wegefrei.app.ui.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhotoCaptureViewModel : ViewModel() {

    private val _selectedPhotoUri = MutableStateFlow<Uri?>(null)
    val selectedPhotoUri: StateFlow<Uri?> = _selectedPhotoUri.asStateFlow()

    fun onImagePicked(uri: Uri) {
        _selectedPhotoUri.value = uri
    }

    fun onPhotoCaptured(uri: Uri) {
        _selectedPhotoUri.value = uri
    }
}
