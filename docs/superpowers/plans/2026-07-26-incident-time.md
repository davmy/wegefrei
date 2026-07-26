# Incident Time (Tatzeitpunkt) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Tatzeitpunkt" (incident date/time) field that defaults to the earliest EXIF timestamp among attached photos, falls back to the current day/time when there are none, and can be overridden with a date/time picker — once overridden, further photo changes never touch it again.

**Architecture:** A new `PhotoTimestampExtractor` (thin EXIF wrapper, mirrors the existing `PhotoLocationExtractor`) reads each photo's capture timestamp. `PhotoCaptureViewModel` gains an `incidentDateTime: StateFlow<LocalDateTime>` plus a `hasUserEditedIncidentDateTime` flag, mirroring the existing `addressText`/`hasUserEditedAddress` pattern. The UI wires a `LaunchedEffect` keyed on the whole photo list to recompute the minimum on every add/remove, and a two-step Material3 `DatePickerDialog` → `TimePicker` dialog chain for manual edits.

**Tech Stack:** Kotlin, Jetpack Compose, `java.time` (native on minSdk 33, no desugaring needed), `androidx.exifinterface` (already a dependency), Material3 `DatePicker`/`TimePicker`.

## Global Constraints

- All changes land in `feature/photocapture/impl`: one new file (`PhotoTimestampExtractor.kt`) plus modifications to `PhotoCaptureViewModel.kt` and `PhotoCaptureScreen.kt`. No new module.
- Variable/function names are English; UI copy is German: "Tatzeitpunkt" (section header), the button shows the value formatted as `"dd.MM.yyyy HH:mm"`, dialog buttons say "OK" / "Abbrechen".
- The value carries minute precision only — every write to `incidentDateTime` truncates seconds and nanoseconds to zero (`.withSecond(0).withNano(0)`).
- EXIF datetime tags (unlike GPS tags) are not subject to Android's location-metadata redaction, so no `MediaStore.setRequireOriginal()` call and no extra permission are needed to read them.
- `onPhotoTimestampsExtracted` always recomputes the minimum from scratch over the given list (never merges with the previous value), and is a permanent no-op once a manual edit (`onIncidentDateTimeChanged`) has happened.
- The "Tatzeitpunkt" section is placed after the existing "Fahrzeug" section and before "Tatort" in `PhotoCaptureScreen`'s `Column`.
- `PhotoTimestampExtractor`/`ExifPhotoTimestampExtractor` and the date/time picker UI wiring are intentionally left without automated tests — thin Android/EXIF wrapper and Compose UI wiring, consistent with `PhotoLocationExtractor` and the rest of this screen.
- Package for all new/changed code: `de.wegefrei.app.feature.photocapture.impl`.

---

### Task 1: `PhotoTimestampExtractor` (EXIF timestamp extraction)

**Files:**
- Create: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoTimestampExtractor.kt`

**Interfaces:**
- Produces: `interface PhotoTimestampExtractor { suspend fun extractTimestamp(uri: Uri): LocalDateTime? }`
- Produces: `internal class ExifPhotoTimestampExtractor(private val context: Context) : PhotoTimestampExtractor`

No automated test for this task — thin Android/EXIF wrapper, consistent with the existing untested `ExifPhotoLocationExtractor`.

- [ ] **Step 1: Create `PhotoTimestampExtractor.kt`**

```kotlin
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
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoTimestampExtractor.kt
git commit -m "Add EXIF-based timestamp extraction from photos"
```

---

### Task 2: `PhotoCaptureViewModel` incident time state

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt`
- Modify: `feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModelTest.kt`

**Interfaces:**
- Produces: `PhotoCaptureViewModel.incidentDateTime: StateFlow<LocalDateTime>`
- Produces: `PhotoCaptureViewModel.onIncidentDateTimeChanged(dateTime: LocalDateTime)`
- Produces: `PhotoCaptureViewModel.onPhotoTimestampsExtracted(timestamps: List<LocalDateTime>)`

- [ ] **Step 1: Write the failing tests**

Add to `PhotoCaptureViewModelTest.kt` (inside the existing `PhotoCaptureViewModelTest` class, after the existing tests), and add `import java.time.Duration` and `import java.time.LocalDateTime` near the top of the file with the other imports:

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.PhotoCaptureViewModelTest"`
Expected: FAIL — `incidentDateTime`, `onIncidentDateTimeChanged`, `onPhotoTimestampsExtracted` are unresolved references.

- [ ] **Step 3: Implement the incident time state in `PhotoCaptureViewModel.kt`**

Add `import java.time.LocalDateTime` to the file's imports.

Add these members to the `PhotoCaptureViewModel` class, right after the existing `_colorText`/`colorText` declarations and before `hasUserEditedAddress`:

```kotlin
    private val _incidentDateTime = MutableStateFlow(LocalDateTime.now().withSecond(0).withNano(0))
    val incidentDateTime: StateFlow<LocalDateTime> = _incidentDateTime.asStateFlow()

    private var hasUserEditedIncidentDateTime = false
```

Add these functions to the class, after `onColorTextChanged`:

```kotlin
    fun onIncidentDateTimeChanged(dateTime: LocalDateTime) {
        hasUserEditedIncidentDateTime = true
        _incidentDateTime.value = dateTime.withSecond(0).withNano(0)
    }

    fun onPhotoTimestampsExtracted(timestamps: List<LocalDateTime>) {
        if (hasUserEditedIncidentDateTime) return
        _incidentDateTime.value = (timestamps.minOrNull() ?: LocalDateTime.now()).withSecond(0).withNano(0)
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.PhotoCaptureViewModelTest"`
Expected: PASS (all tests in the class, old and new).

- [ ] **Step 5: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt \
        feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModelTest.kt
git commit -m "Add incident time state to PhotoCaptureViewModel"
```

---

### Task 3: Wire Tatzeitpunkt UI into `PhotoCaptureScreen`

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`

**Interfaces:**
- Consumes: `PhotoCaptureViewModel.incidentDateTime`, `onIncidentDateTimeChanged`, `onPhotoTimestampsExtracted` (Task 2); `ExifPhotoTimestampExtractor`, `PhotoTimestampExtractor.extractTimestamp` (Task 1)

No automated test for this task — Compose UI wiring, consistent with the rest of this screen.

- [ ] **Step 1: Add the new imports**

At the top of `PhotoCaptureScreen.kt`, add:

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
```

- [ ] **Step 2: Update `PhotoCaptureRoot` to collect the new state and drive timestamp extraction**

In `PhotoCaptureRoot`, add this line after the existing `val colorText by viewModel.colorText.collectAsState()` line:

```kotlin
    val incidentDateTime by viewModel.incidentDateTime.collectAsState()
```

Add this line after the existing `val currentLocationProvider = remember { AndroidCurrentLocationProvider(context) }` line:

```kotlin
    val timestampExtractor = remember { ExifPhotoTimestampExtractor(context) }
```

Add this `LaunchedEffect` anywhere among the existing `LaunchedEffect`/launcher declarations in `PhotoCaptureRoot` (e.g. right after the existing `LaunchedEffect(photoUris.firstOrNull()) { ... }` block):

```kotlin
    LaunchedEffect(photoUris) {
        val timestamps = photoUris.mapNotNull { uri -> timestampExtractor.extractTimestamp(uri) }
        viewModel.onPhotoTimestampsExtracted(timestamps)
    }
```

In the `PhotoCaptureScreen(...)` call inside `PhotoCaptureRoot`, add these parameters (anywhere among the existing ones, e.g. right after `onColorTextChanged = viewModel::onColorTextChanged,`):

```kotlin
            incidentDateTime = incidentDateTime,
            onIncidentDateTimeChanged = viewModel::onIncidentDateTimeChanged,
```

- [ ] **Step 3: Add the new parameters to the `PhotoCaptureScreen` composable signature**

Add these parameters to the `PhotoCaptureScreen` function signature, after `onColorTextChanged: (String) -> Unit,` and before `addressText: String,`:

```kotlin
    incidentDateTime: LocalDateTime,
    onIncidentDateTimeChanged: (LocalDateTime) -> Unit,
```

- [ ] **Step 4: Add the "Tatzeitpunkt" section above "Tatort"**

In `PhotoCaptureScreen`'s `Column`, insert this block right after the three `RequiredTextField` calls of the existing "Fahrzeug" section and before the existing `Text(text = "Tatort", ...)` block:

```kotlin
            Text(
                text = "Tatzeitpunkt",
                style = MaterialTheme.typography.titleLarge,
            )

            IncidentDateTimePicker(
                incidentDateTime = incidentDateTime,
                onIncidentDateTimeChanged = onIncidentDateTimeChanged,
            )
```

- [ ] **Step 5: Add the `IncidentDateTimePicker` composable and its formatter**

Add this new private composable and top-level constant in `PhotoCaptureScreen.kt`, right after the `PhotoCaptureScreen` function's closing brace (before `RequiredTextField`):

```kotlin
private val INCIDENT_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentDateTimePicker(
    incidentDateTime: LocalDateTime,
    onIncidentDateTimeChanged: (LocalDateTime) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }

    Button(
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = incidentDateTime.format(INCIDENT_DATE_TIME_FORMATTER))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = incidentDateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            pendingDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            showDatePicker = false
                            showTimePicker = true
                        } else {
                            showDatePicker = false
                        }
                    },
                ) {
                    Text(text = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "Abbrechen")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val datePendingTime = pendingDate
    if (showTimePicker && datePendingTime != null) {
        val timePickerState = rememberTimePickerState(
            initialHour = incidentDateTime.hour,
            initialMinute = incidentDateTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIncidentDateTimeChanged(
                            datePendingTime.atTime(timePickerState.hour, timePickerState.minute),
                        )
                        showTimePicker = false
                    },
                ) {
                    Text(text = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(text = "Abbrechen")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            },
        )
    }
}
```

- [ ] **Step 6: Build the module**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full module test suite**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions from Task 2).

- [ ] **Step 8: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt
git commit -m "Add Tatzeitpunkt section with date/time picker"
```
