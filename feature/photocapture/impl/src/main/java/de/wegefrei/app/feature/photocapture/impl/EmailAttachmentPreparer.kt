package de.wegefrei.app.feature.photocapture.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val MAX_DIMENSION_PX = 1600
private const val JPEG_QUALITY = 80

interface EmailAttachmentPreparer {
    suspend fun prepareAttachments(photoUris: List<Uri>): List<Uri>
}

internal class CompressingEmailAttachmentPreparer(
    private val context: Context,
) : EmailAttachmentPreparer {

    override suspend fun prepareAttachments(photoUris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        photoUris.mapIndexedNotNull { index, uri -> compressAndStore(uri, index) }
    }

    private fun compressAndStore(uri: Uri, index: Int): Uri? {
        return try {
            val bitmap = decodeSampledBitmap(uri) ?: return null
            val outputDir = File(context.cacheDir, "report_photos").apply { mkdirs() }
            val outputFile = File(outputDir, "report_photo_$index.jpg")
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
        } catch (e: IOException) {
            null
        }
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsRead = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        if (boundsRead == null && boundsOptions.outWidth <= 0) return null

        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > MAX_DIMENSION_PX ||
            boundsOptions.outHeight / sampleSize > MAX_DIMENSION_PX
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }
}
