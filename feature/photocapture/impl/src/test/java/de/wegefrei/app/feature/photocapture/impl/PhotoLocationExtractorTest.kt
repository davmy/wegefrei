package de.wegefrei.app.feature.photocapture.impl

import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Exercises [ExifPhotoLocationExtractor] against real JPEG files and real (Robolectric-backed)
 * EXIF I/O, rather than mocking [androidx.exifinterface.media.ExifInterface] or the content
 * resolver.
 */
@RunWith(RobolectricTestRunner::class)
class PhotoLocationExtractorTest {

    private fun jpegFile(name: String): File {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    @Test
    fun `extractLocation reads GPS coordinates embedded in EXIF`() = runTest {
        val file = jpegFile("with-gps.jpg")
        ExifInterface(file.absolutePath).apply {
            setLatLong(52.5200, 13.4050)
            saveAttributes()
        }
        val extractor = ExifPhotoLocationExtractor(RuntimeEnvironment.getApplication())

        val result = extractor.extractLocation(Uri.fromFile(file))

        assertEquals(52.5200, result?.latitude ?: 0.0, 0.001)
        assertEquals(13.4050, result?.longitude ?: 0.0, 0.001)
    }

    @Test
    fun `extractLocation returns null when the photo has no GPS tags`() = runTest {
        val file = jpegFile("without-gps.jpg")
        val extractor = ExifPhotoLocationExtractor(RuntimeEnvironment.getApplication())

        val result = extractor.extractLocation(Uri.fromFile(file))

        assertNull(result)
    }

    @Test
    fun `extractLocation returns null for a uri that cannot be opened`() = runTest {
        val extractor = ExifPhotoLocationExtractor(RuntimeEnvironment.getApplication())
        val missingFile = File(RuntimeEnvironment.getApplication().cacheDir, "does-not-exist.jpg")

        val result = extractor.extractLocation(Uri.fromFile(missingFile))

        assertNull(result)
    }
}
