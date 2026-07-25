package de.wegefrei.app.feature.photocapture.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.wegefrei.app.feature.photocapture.api.PhotoCaptureRoute

fun NavGraphBuilder.photoCaptureScreen() {
    composable<PhotoCaptureRoute> {
        PhotoCaptureRoot()
    }
}
