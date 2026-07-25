package de.wegefrei.app.ui.photo

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

@Composable
fun PhotoCaptureRoute(
    viewModel: PhotoCaptureViewModel = viewModel(),
) {
    val selectedPhotoUri by viewModel.selectedPhotoUri.collectAsState()
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
            selectedPhotoUri = selectedPhotoUri,
            onImagePicked = viewModel::onImagePicked,
            onTakePhotoRequested = { showCamera = true },
        )
    }
}

@Composable
fun PhotoCaptureScreen(
    selectedPhotoUri: Uri?,
    onImagePicked: (Uri) -> Unit,
    onTakePhotoRequested: () -> Unit,
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onImagePicked) },
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
                text = "Foto des Falschparkers",
                style = MaterialTheme.typography.titleLarge,
            )

            if (selectedPhotoUri != null) {
                AsyncImage(
                    model = selectedPhotoUri,
                    contentDescription = "Ausgewähltes Foto",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Aus Galerie wählen")
            }

            Button(
                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Foto aufnehmen")
            }
        }
    }
}
