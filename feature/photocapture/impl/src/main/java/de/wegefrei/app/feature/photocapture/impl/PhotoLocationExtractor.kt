package de.wegefrei.app.feature.photocapture.impl

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface PhotoLocationExtractor {
    suspend fun extractLocation(uri: Uri): LatLng?
}

internal class ExifPhotoLocationExtractor(
    private val context: Context,
) : PhotoLocationExtractor {
    override suspend fun extractLocation(uri: Uri): LatLng? =
        withContext(Dispatchers.IO) {
            val originalUri =
                try {
                    MediaStore.setRequireOriginal(uri)
                } catch (e: UnsupportedOperationException) {
                    uri
                }

            try {
                context.contentResolver.openInputStream(originalUri)?.use { stream ->
                    ExifInterface(stream).latLong?.let { latLong ->
                        LatLng(latitude = latLong[0], longitude = latLong[1])
                    }
                }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }
}
