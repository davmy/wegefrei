package de.wegefrei.app.feature.photocapture.impl

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoCaptureViewModelTest {

    private val viewModel = PhotoCaptureViewModel()

    @Test
    fun `onImagesPicked adds photos to an empty list`() {
        val uri = Uri.parse("content://photos/1")

        viewModel.onImagesPicked(listOf(uri))

        assertEquals(listOf(uri), viewModel.photoUris.value)
    }

    @Test
    fun `onImagesPicked allows the same photo to be picked twice`() {
        val uri = Uri.parse("content://photos/1")

        viewModel.onImagesPicked(listOf(uri, uri))

        assertEquals(listOf(uri, uri), viewModel.photoUris.value)
    }

    @Test
    fun `onImagesPicked truncates at MAX_PHOTOS`() {
        val uris = (1..MAX_PHOTOS + 3).map { Uri.parse("content://photos/$it") }

        viewModel.onImagesPicked(uris)

        assertEquals(MAX_PHOTOS, viewModel.photoUris.value.size)
        assertEquals(uris.take(MAX_PHOTOS), viewModel.photoUris.value)
    }

    @Test
    fun `onPhotoCaptured is ignored once MAX_PHOTOS is reached`() {
        val uris = (1..MAX_PHOTOS).map { Uri.parse("content://photos/$it") }
        viewModel.onImagesPicked(uris)

        viewModel.onPhotoCaptured(Uri.parse("content://photos/extra"))

        assertEquals(uris, viewModel.photoUris.value)
    }

    @Test
    fun `onPhotoRemoved removes the tapped occurrence, not just any matching uri`() {
        val uri = Uri.parse("content://photos/1")
        val other = Uri.parse("content://photos/2")
        viewModel.onImagesPicked(listOf(uri, other, uri))

        // Remove the duplicate at index 2, the first occurrence (index 0) must survive.
        viewModel.onPhotoRemoved(2)

        assertEquals(listOf(uri, other), viewModel.photoUris.value)
    }

    @Test
    fun `onPhotoRemoved removes by index`() {
        val first = Uri.parse("content://photos/1")
        val second = Uri.parse("content://photos/2")
        val third = Uri.parse("content://photos/3")
        viewModel.onImagesPicked(listOf(first, second, third))

        viewModel.onPhotoRemoved(1)

        assertEquals(listOf(first, third), viewModel.photoUris.value)
    }
}
