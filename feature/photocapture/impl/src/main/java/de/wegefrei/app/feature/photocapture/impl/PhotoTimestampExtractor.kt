package de.wegefrei.app.feature.photocapture.impl

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val EXIF_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

interface PhotoTimestampExtractor {
    suspend fun extractTimestamp(uri: Uri): LocalDateTime?
}

internal class ExifPhotoTimestampExtractor(
    private val context: Context,
) : PhotoTimestampExtractor {

    override suspend fun extractTimestamp(uri: Uri): LocalDateTime? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val rawDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                rawDateTime?.let(::parseExifDateTime)
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}

private fun parseExifDateTime(rawDateTime: String): LocalDateTime? =
    try {
        LocalDateTime.parse(rawDateTime, EXIF_DATETIME_FORMATTER).withSecond(0).withNano(0)
    } catch (e: DateTimeParseException) {
        null
    }
