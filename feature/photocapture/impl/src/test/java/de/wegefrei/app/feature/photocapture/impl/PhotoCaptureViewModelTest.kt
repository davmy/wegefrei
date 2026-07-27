package de.wegefrei.app.feature.photocapture.impl

import android.net.Uri
import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `onImagesPicked ignores duplicates within the same batch`() {
        val uri = Uri.parse("content://photos/1")

        viewModel.onImagesPicked(listOf(uri, uri))

        assertEquals(listOf(uri), viewModel.photoUris.value)
    }

    @Test
    fun `onImagesPicked ignores a photo already in the list`() {
        val uri = Uri.parse("content://photos/1")
        val other = Uri.parse("content://photos/2")
        viewModel.onImagesPicked(listOf(uri))

        viewModel.onImagesPicked(listOf(uri, other))

        assertEquals(listOf(uri, other), viewModel.photoUris.value)
    }

    @Test
    fun `onPhotoCaptured ignores a photo already in the list`() {
        val uri = Uri.parse("content://photos/1")
        viewModel.onImagesPicked(listOf(uri))

        viewModel.onPhotoCaptured(uri)

        assertEquals(listOf(uri), viewModel.photoUris.value)
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
    fun `onPhotoRemoved removes by index`() {
        val first = Uri.parse("content://photos/1")
        val second = Uri.parse("content://photos/2")
        val third = Uri.parse("content://photos/3")
        viewModel.onImagesPicked(listOf(first, second, third))

        viewModel.onPhotoRemoved(1)

        assertEquals(listOf(first, third), viewModel.photoUris.value)
    }

    @Test
    fun `onAddressTextChanged sets the address text`() {
        viewModel.onAddressTextChanged("Musterstraße 1, Berlin")

        assertEquals("Musterstraße 1, Berlin", viewModel.addressText.value)
    }

    @Test
    fun `onAddressAutoDetected sets the address text when nothing was entered yet`() {
        viewModel.onAddressAutoDetected("Alexanderplatz, Berlin")

        assertEquals("Alexanderplatz, Berlin", viewModel.addressText.value)
    }

    @Test
    fun `onAddressAutoDetected does not overwrite a manual edit`() {
        viewModel.onAddressTextChanged("Meine eigene Adresse")

        viewModel.onAddressAutoDetected("Alexanderplatz, Berlin")

        assertEquals("Meine eigene Adresse", viewModel.addressText.value)
    }

    @Test
    fun `onCurrentLocationAddressReceived always overwrites the address text`() {
        viewModel.onAddressTextChanged("Meine eigene Adresse")

        viewModel.onCurrentLocationAddressReceived("Potsdamer Platz, Berlin")

        assertEquals("Potsdamer Platz, Berlin", viewModel.addressText.value)
    }

    @Test
    fun `onAddressAutoDetected does not overwrite after onCurrentLocationAddressReceived`() {
        viewModel.onCurrentLocationAddressReceived("Potsdamer Platz, Berlin")

        viewModel.onAddressAutoDetected("Alexanderplatz, Berlin")

        assertEquals("Potsdamer Platz, Berlin", viewModel.addressText.value)
    }

    @Test
    fun `onLicensePlateTextChanged sets the license plate text`() {
        viewModel.onLicensePlateTextChanged("KS-T 2394")

        assertEquals("KS-T 2394", viewModel.licensePlateText.value)
    }

    @Test
    fun `onMakeTextChanged sets the make text`() {
        viewModel.onMakeTextChanged("Ford")

        assertEquals("Ford", viewModel.makeText.value)
    }

    @Test
    fun `onColorTextChanged sets the color text`() {
        viewModel.onColorTextChanged("Silber")

        assertEquals("Silber", viewModel.colorText.value)
    }

    @Test
    fun `onIncidentDateTimeChanged truncates seconds and nanoseconds`() {
        val dateTime = LocalDateTime.of(2026, 7, 26, 14, 32, 45, 123456789)

        viewModel.onIncidentDateTimeChanged(dateTime)

        assertEquals(LocalDateTime.of(2026, 7, 26, 14, 32, 0, 0), viewModel.incidentDateTime.value)
    }

    @Test
    fun `onPhotoTimestampsExtracted sets the minimum timestamp when nothing was manually edited`() {
        val earliest = LocalDateTime.of(2026, 7, 26, 10, 15)
        val later = LocalDateTime.of(2026, 7, 26, 12, 0)

        viewModel.onPhotoTimestampsExtracted(listOf(later, earliest))

        assertEquals(earliest, viewModel.incidentDateTime.value)
    }

    @Test
    fun `onPhotoTimestampsExtracted does not overwrite a manual edit`() {
        val manual = LocalDateTime.of(2026, 7, 20, 8, 0)
        viewModel.onIncidentDateTimeChanged(manual)

        viewModel.onPhotoTimestampsExtracted(listOf(LocalDateTime.of(2026, 7, 26, 10, 15)))

        assertEquals(manual, viewModel.incidentDateTime.value)
    }

    @Test
    fun `onPhotoTimestampsExtracted falls back to now when the list is empty and nothing was manually edited`() {
        viewModel.onPhotoTimestampsExtracted(emptyList())

        // Tolerance avoids flakiness from the truncation-to-minute and any tiny
        // scheduling delay between this call and reading LocalDateTime.now() here.
        val minutesDifference = Duration.between(viewModel.incidentDateTime.value, LocalDateTime.now())
            .toMinutes()
        assertTrue(minutesDifference in -1..1)
    }

    @Test
    fun `onPhotoTimestampsExtracted does not overwrite a manual edit even when the photo list is empty`() {
        val manual = LocalDateTime.of(2026, 7, 20, 8, 0)
        viewModel.onIncidentDateTimeChanged(manual)

        viewModel.onPhotoTimestampsExtracted(emptyList())

        assertEquals(manual, viewModel.incidentDateTime.value)
    }

    @Test
    fun `onViolationTextChanged sets the violation text`() {
        viewModel.onViolationTextChanged("Parken im absoluten Halteverbot")

        assertEquals("Parken im absoluten Halteverbot", viewModel.violationText.value)
    }

    @Test
    fun `parkOrHaltText defaults to Parken`() {
        assertEquals("Parken", viewModel.parkOrHaltText.value)
    }

    @Test
    fun `onParkOrHaltTextChanged sets the parkOrHalt text`() {
        viewModel.onParkOrHaltTextChanged("Halten")

        assertEquals("Halten", viewModel.parkOrHaltText.value)
    }

    @Test
    fun `onParkOrHaltTextChanged replaces the leading Parken in the violation text with Halten`() {
        viewModel.onViolationTextChanged("Parken im absoluten Halteverbot")

        viewModel.onParkOrHaltTextChanged("Halten")

        assertEquals("Halten im absoluten Halteverbot", viewModel.violationText.value)
    }

    @Test
    fun `onParkOrHaltTextChanged back to Parken restores the violation text`() {
        viewModel.onViolationTextChanged("Parken im absoluten Halteverbot")
        viewModel.onParkOrHaltTextChanged("Halten")

        viewModel.onParkOrHaltTextChanged("Parken")

        assertEquals("Parken im absoluten Halteverbot", viewModel.violationText.value)
    }

    @Test
    fun `onObstructionTextChanged sets the obstruction text`() {
        viewModel.onObstructionTextChanged("Radfahrer muss auf die Fahrspur ausweichen")

        assertEquals("Radfahrer muss auf die Fahrspur ausweichen", viewModel.obstructionText.value)
    }

    @Test
    fun `durationOver60MinutesText defaults to Nein`() {
        assertEquals("Nein", viewModel.durationOver60MinutesText.value)
    }

    @Test
    fun `onDurationOver60MinutesTextChanged sets the duration text`() {
        viewModel.onDurationOver60MinutesTextChanged("Ja")

        assertEquals("Ja", viewModel.durationOver60MinutesText.value)
    }
}
