package de.wegefrei.app.feature.photocapture.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileOutputStream

/**
 * Exercises [CompressingEmailAttachmentPreparer] against real JPEG files, real Bitmap
 * compression/decoding, and real cache-directory I/O (Robolectric ships native Skia, so
 * [Bitmap.compress]/[BitmapFactory.decodeStream] behave like on a device).
 */
@RunWith(RobolectricTestRunner::class)
class EmailAttachmentPreparerTest {
    @Before
    fun clearFileProviderPathStrategyCache() {
        // FileProvider caches the resolved PathStrategy (which is derived from the app's
        // cacheDir) in a static map keyed only by authority. Robolectric gives each test
        // method its own throwaway cacheDir but doesn't reset this static, so a strategy
        // cached by an earlier test method points at a cacheDir that no longer exists,
        // making getUriForFile fail with "Failed to find configured root" in every test
        // after the first. Clearing it before each test keeps tests order-independent.
        FileProvider::class.java.getDeclaredField("sCache").apply {
            isAccessible = true
            (get(null) as MutableMap<*, *>).clear()
        }
    }

    private fun jpegFile(
        name: String,
        width: Int,
        height: Int,
        orientation: Int? = null,
    ): File {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun decodedSize(uri: Uri): Pair<Int, Int> {
        val context = RuntimeEnvironment.getApplication()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        return options.outWidth to options.outHeight
    }

    @Test
    fun `prepareAttachments produces a readable jpeg for each input photo`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val file = jpegFile("input.jpg", 100, 50)
            val preparer = CompressingEmailAttachmentPreparer(context)

            val result = preparer.prepareAttachments(listOf(Uri.fromFile(file)))

            assertEquals(1, result.size)
            assertEquals(100 to 50, decodedSize(result.single()))
        }

    @Test
    fun `prepareAttachments downsamples photos larger than the max dimension`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val file = jpegFile("large.jpg", 3200, 1600)
            val preparer = CompressingEmailAttachmentPreparer(context)

            val result = preparer.prepareAttachments(listOf(Uri.fromFile(file)))

            val (width, height) = decodedSize(result.single())
            assertTrue("expected width <= 1600 but was $width", width <= 1600)
            assertTrue("expected height <= 1600 but was $height", height <= 1600)
        }

    @Test
    fun `prepareAttachments rotates photos according to their EXIF orientation`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val file = jpegFile("rotated.jpg", 100, 50, orientation = ExifInterface.ORIENTATION_ROTATE_90)
            val preparer = CompressingEmailAttachmentPreparer(context)

            val result = preparer.prepareAttachments(listOf(Uri.fromFile(file)))

            assertEquals(50 to 100, decodedSize(result.single()))
        }

    @Test
    fun `prepareAttachments skips photos that cannot be decoded and keeps the rest`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val goodFile = jpegFile("good.jpg", 100, 50)
            val missingFile = File(context.cacheDir, "does-not-exist.jpg")
            val preparer = CompressingEmailAttachmentPreparer(context)

            val result = preparer.prepareAttachments(listOf(Uri.fromFile(missingFile), Uri.fromFile(goodFile)))

            assertEquals(1, result.size)
        }

    @Test
    fun `prepareAttachments clears photos left over from an earlier report`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val reportPhotosDir = File(context.cacheDir, "report_photos")
            val preparer = CompressingEmailAttachmentPreparer(context)

            preparer.prepareAttachments(listOf(Uri.fromFile(jpegFile("first.jpg", 100, 50))))
            val firstReportDir = reportPhotosDir.listFiles()!!.single()
            assertTrue(firstReportDir.listFiles()!!.isNotEmpty())

            preparer.prepareAttachments(listOf(Uri.fromFile(jpegFile("second.jpg", 100, 50))))

            assertTrue(
                "expected the first report's directory to have been deleted",
                !firstReportDir.exists(),
            )
            assertEquals(1, reportPhotosDir.listFiles()!!.size)
        }
}
