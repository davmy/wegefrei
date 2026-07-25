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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

@Composable
internal fun PhotoCaptureRoot(
    viewModel: PhotoCaptureViewModel = viewModel(),
) {
    val photoUris by viewModel.photoUris.collectAsState()
    var showCamera by remember { mutableStateOf(false) }

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
        )
    }
}

@Composable
internal fun PhotoCaptureScreen(
    photoUris: List<Uri>,
    onImagesPicked: (List<Uri>) -> Unit,
    onTakePhotoRequested: () -> Unit,
    onPhotoRemoved: (Uri) -> Unit,
) {
    val remainingSlots = MAX_PHOTOS - photoUris.size
    val canAddMore = remainingSlots > 0

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
                    items(items = photoUris, key = { it.toString() }) { uri ->
                        PhotoThumbnail(uri = uri, onRemove = { onPhotoRemoved(uri) })
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
        }
    }
}

@Composable
private fun PhotoThumbnail(
    uri: Uri,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(96.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = "Foto",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
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
