package de.wegefrei.app.feature.photocapture.impl

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoThumbnailKeyTest {

    @Test
    fun `keys stay unique when the same photo appears multiple times in the list`() {
        val uri = Uri.parse("content://photos/1")
        val photoUris = listOf(uri, uri, uri)

        val keys = photoUris.mapIndexed { index, u -> photoThumbnailKey(index, u) }

        assertEquals(keys.size, keys.toSet().size)
    }
}
