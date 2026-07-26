package de.wegefrei.app.feature.photocapture.impl

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

// Lists can contain the same photo more than once (picked or captured twice), so the
// index must be part of the key — using uri.toString() alone crashes LazyRow with a
// duplicate-key error.
internal fun photoThumbnailKey(index: Int, uri: Uri): String = "$index-$uri"

@Composable
internal fun PhotoCaptureRoot(
    viewModel: PhotoCaptureViewModel = viewModel(),
) {
    val context = LocalContext.current
    val photoUris by viewModel.photoUris.collectAsState()
    val addressText by viewModel.addressText.collectAsState()
    var showCamera by remember { mutableStateOf(false) }
    var isLookingUpAddressFromPhoto by remember { mutableStateOf(false) }
    var isLookingUpAddressFromLocation by remember { mutableStateOf(false) }

    val locationExtractor = remember { ExifPhotoLocationExtractor(context) }
    val addressLookupService = remember { NominatimAddressLookupService() }
    val currentLocationProvider = remember { AndroidCurrentLocationProvider(context) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(photoUris.firstOrNull()) {
        val firstUri = photoUris.firstOrNull() ?: return@LaunchedEffect
        isLookingUpAddressFromPhoto = true
        val latLng = locationExtractor.extractLocation(firstUri)
        if (latLng != null) {
            val address = addressLookupService.reverseGeocode(latLng.latitude, latLng.longitude)
            if (address != null) {
                viewModel.onAddressAutoDetected(address)
            }
        }
        isLookingUpAddressFromPhoto = false
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                coroutineScope.launch {
                    isLookingUpAddressFromLocation = true
                    val latLng = currentLocationProvider.getCurrentLocation()
                    if (latLng != null) {
                        val address = addressLookupService.reverseGeocode(latLng.latitude, latLng.longitude)
                        if (address != null) {
                            viewModel.onCurrentLocationAddressReceived(address)
                        }
                    }
                    isLookingUpAddressFromLocation = false
                }
            }
        },
    )

    if (showCamera) {
        CameraCaptureScreen(
            onPhotoCaptured = { uri ->
                viewModel.onPhotoCaptured(uri)
                showCamera = false
            },
        )
    } else {
        PhotoCaptureScreen(
            photoUris = photoUris,
            onImagesPicked = viewModel::onImagesPicked,
            onTakePhotoRequested = { showCamera = true },
            onPhotoRemoved = viewModel::onPhotoRemoved,
            addressText = addressText,
            onAddressTextChanged = viewModel::onAddressTextChanged,
            isLookingUpAddress = isLookingUpAddressFromPhoto || isLookingUpAddressFromLocation,
            onUseCurrentLocationRequested = {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
        )
    }
}

@Composable
internal fun PhotoCaptureScreen(
    photoUris: List<Uri>,
    onImagesPicked: (List<Uri>) -> Unit,
    onTakePhotoRequested: () -> Unit,
    onPhotoRemoved: (Int) -> Unit,
    addressText: String,
    onAddressTextChanged: (String) -> Unit,
    isLookingUpAddress: Boolean,
    onUseCurrentLocationRequested: () -> Unit,
) {
    val remainingSlots = MAX_PHOTOS - photoUris.size
    val canAddMore = remainingSlots > 0
    var previewUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = remainingSlots.coerceAtLeast(2)),
        onResult = onImagesPicked,
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> if (granted) onTakePhotoRequested() },
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Fotos des Falschparkers",
                style = MaterialTheme.typography.titleLarge,
            )

            Text(text = "${photoUris.size} / $MAX_PHOTOS Fotos")

            if (photoUris.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(
                        items = photoUris,
                        key = { index, uri -> photoThumbnailKey(index, uri) },
                    ) { index, uri ->
                        PhotoThumbnail(
                            uri = uri,
                            onClick = { previewUri = uri },
                            onRemove = { onPhotoRemoved(index) },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = canAddMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Aus Galerie wählen")
            }

            Button(
                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                enabled = canAddMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Foto aufnehmen")
            }

            Text(
                text = "Adresse",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = addressText,
                onValueChange = onAddressTextChanged,
                label = { Text(text = "Adresse") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (isLookingUpAddress) {
                CircularProgressIndicator()
            }

            Button(
                onClick = onUseCurrentLocationRequested,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Aktuellen Standort verwenden")
            }
        }
    }

    val previewedUri = previewUri
    if (previewedUri != null) {
        PhotoPreviewDialog(uri = previewedUri, onDismiss = { previewUri = null })
    }
}

@Composable
private fun PhotoThumbnail(
    uri: Uri,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(96.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = "Foto",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                color = Color.White,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun PhotoPreviewDialog(
    uri: Uri,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Fotovorschau",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}
