package de.wegefrei.app.feature.photocapture.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
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
        val reportPhotosDir = File(context.cacheDir, "report_photos")
        // Reclaim space from earlier reports before creating this one's directory. By the
        // time a new report is being prepared, any prior report's email has already been
        // handed off (or abandoned), so those compressed photos — which can contain other
        // people's license plates, faces, and GPS-tagged locations — no longer need to sit in
        // cache. Only pre-existing directories are removed, never the one about to be
        // created, so there's no race with a mail app still reading the current attachments.
        reportPhotosDir.listFiles()?.forEach { it.deleteRecursively() }

        val outputDir = File(reportPhotosDir, "${System.currentTimeMillis()}").apply { mkdirs() }
        photoUris.mapIndexedNotNull { index, uri -> compressAndStore(uri, index, outputDir) }
    }

    private fun compressAndStore(uri: Uri, index: Int, outputDir: File): Uri? {
        return try {
            val bitmap = decodeSampledBitmap(uri) ?: return null
            val outputFile = File(outputDir, "report_photo_$index.jpg")
            try {
                FileOutputStream(outputFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                }
            } finally {
                bitmap.recycle()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        if (boundsOptions.outWidth <= 0) return null

        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > MAX_DIMENSION_PX ||
            boundsOptions.outHeight / sampleSize > MAX_DIMENSION_PX
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        return applyExifRotation(uri, decoded)
    }

    private fun applyExifRotation(uri: Uri, source: Bitmap): Bitmap {
        val degrees = readExifOrientationDegrees(uri)
        if (degrees == 0f) return source

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) {
            source.recycle()
        }
        return rotated
    }

    private fun readExifOrientationDegrees(uri: Uri): Float {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        } catch (e: SecurityException) {
            ExifInterface.ORIENTATION_NORMAL
        }

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }
}

fun emailAttachmentPreparer(context: Context): EmailAttachmentPreparer =
    CompressingEmailAttachmentPreparer(context)
