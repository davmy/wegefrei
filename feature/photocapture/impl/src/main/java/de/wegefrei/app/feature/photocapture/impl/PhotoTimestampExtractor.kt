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
import java.util.Locale

private val EXIF_DATETIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT)

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
                parseExifDateTime(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                    ?: parseExifDateTime(exif.getAttribute(ExifInterface.TAG_DATETIME))
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}

internal fun parseExifDateTime(rawDateTime: String?): LocalDateTime? {
    if (rawDateTime == null) return null
    return try {
        LocalDateTime.parse(rawDateTime, EXIF_DATETIME_FORMATTER).withSecond(0).withNano(0)
    } catch (e: DateTimeParseException) {
        null
    }
}
