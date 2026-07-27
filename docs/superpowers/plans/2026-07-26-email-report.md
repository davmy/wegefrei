# Email Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping "Weiter" — once witness details, vehicle fields, and two new required fields (Verstoß, Behinderung) are all complete — builds the German complaint-letter email and hands it to the user's email app via an Android send intent, with compressed photo attachments.

**Architecture:** Two new required fields land in `feature/photocapture/impl` the same way the existing vehicle fields did. `onWeiterRequested` changes from `() -> Unit` to `(ReportDetails) -> Unit`, a new public data class carrying every report-side value. `feature/witness/impl` gains one more public entry point (`readWitnessDetails`) alongside the existing `areWitnessDetailsComplete`, exposing the actual witness values now that the email body genuinely needs them. Photo compression + `FileProvider`-based attachment lives in `feature/photocapture/impl`. The pure email-text-building logic and the final intent-launching wiring both live in `:app` — the only place with both feature modules' data in scope.

**Tech Stack:** Kotlin, Jetpack Compose, `android.graphics.Bitmap`/`BitmapFactory` (new), `androidx.core.content.FileProvider` (new, via existing `androidx.core:core-ktx` artifact), `android.content.Intent` (`ACTION_SEND`/`ACTION_SEND_MULTIPLE`).

## Global Constraints

- No in-app email sending — only `Intent`-based handoff to whatever email app is installed. No SMTP, no network email API.
- "Verstoß" and "Behinderung" are new required fields, same `RequiredTextField`/Pflichtfeld pattern as Kennzeichen/Marke/Farbe, placed after "Tatort" and before "Weiter". The "Weiter" button's `enabled` expression grows to require these two non-blank as well.
- "Weitere Zeugen" is always the literal `-`; "PKW" is always appended literally after Kennzeichen/Marke/Farbe. Neither is a field — both are fixed template text.
- No recipient address is pre-filled ("To" stays empty).
- `feature/photocapture` and `feature/witness` still do not depend on each other. `:app` (`MainActivity`) is the only place that imports from both.
- `WitnessDetails`/`readWitnessDetails` and `ReportDetails` are the only new cross-module-boundary public surfaces this plan adds.
- Package roots: `de.wegefrei.app.feature.photocapture.impl`, `de.wegefrei.app.feature.witness.impl`, `de.wegefrei.app`.
- `CompressingEmailAttachmentPreparer`, the `FileProvider`/manifest/resource wiring, `buildReportEmailIntent`, and the `MainActivity` wiring are left without automated tests — real Android I/O (bitmaps, files, intents) and Compose/app wiring, consistent with this codebase's established pattern. `buildReportEmailBody` (pure string assembly) and the two new ViewModel fields DO get tests. `readWitnessDetails` gets a light Robolectric acceptance test mirroring the existing `areWitnessDetailsComplete` one, since it's the same class of DataStore-plumbing risk that test was added to cover.

---

### Task 1: Add Verstoß and Behinderung fields to `PhotoCaptureViewModel`

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt`
- Modify: `feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModelTest.kt`

**Interfaces:**
- Produces: `PhotoCaptureViewModel.violationText: StateFlow<String>`, `onViolationTextChanged(text: String)`
- Produces: `PhotoCaptureViewModel.obstructionText: StateFlow<String>`, `onObstructionTextChanged(text: String)`

- [ ] **Step 1: Write the failing tests**

Add to `PhotoCaptureViewModelTest.kt` (inside the existing test class, after the existing tests):

```kotlin
    @Test
    fun `onViolationTextChanged sets the violation text`() {
        viewModel.onViolationTextChanged("Parken im absoluten Halteverbot")

        assertEquals("Parken im absoluten Halteverbot", viewModel.violationText.value)
    }

    @Test
    fun `onObstructionTextChanged sets the obstruction text`() {
        viewModel.onObstructionTextChanged("Radfahrer muss auf die Fahrspur ausweichen")

        assertEquals("Radfahrer muss auf die Fahrspur ausweichen", viewModel.obstructionText.value)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.PhotoCaptureViewModelTest"`
Expected: FAIL — `violationText`, `onViolationTextChanged`, `obstructionText`, `onObstructionTextChanged` are unresolved references.

- [ ] **Step 3: Implement the fields in `PhotoCaptureViewModel.kt`**

Add these properties to the class, right after the existing `_colorText`/`colorText` declarations:

```kotlin
    private val _violationText = MutableStateFlow("")
    val violationText: StateFlow<String> = _violationText.asStateFlow()

    private val _obstructionText = MutableStateFlow("")
    val obstructionText: StateFlow<String> = _obstructionText.asStateFlow()
```

Add these functions to the class, right after the existing `onColorTextChanged`:

```kotlin
    fun onViolationTextChanged(text: String) {
        _violationText.value = text
    }

    fun onObstructionTextChanged(text: String) {
        _obstructionText.value = text
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.PhotoCaptureViewModelTest"`
Expected: PASS (all tests in the class, old and new).

- [ ] **Step 5: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt \
        feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModelTest.kt
git commit -m "Add Verstoß and Behinderung fields to PhotoCaptureViewModel"
```

---

### Task 2: `ReportDetails` and wiring the new fields + Weiter payload into the UI

**Files:**
- Create: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/ReportDetails.kt`
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureNavigation.kt`

**Interfaces:**
- Consumes: `PhotoCaptureViewModel.violationText`, `obstructionText`, `onViolationTextChanged`, `onObstructionTextChanged` (Task 1)
- Produces: `data class ReportDetails(licensePlate: String, make: String, color: String, address: String, incidentDateTime: LocalDateTime, violation: String, obstruction: String, photoUris: List<Uri>)` (public)
- Produces: `PhotoCaptureRoot(..., onWeiterRequested: (ReportDetails) -> Unit)`, `PhotoCaptureScreen(..., onWeiterRequested: (ReportDetails) -> Unit)`, `fun NavGraphBuilder.photoCaptureScreen(..., onWeiterRequested: (ReportDetails) -> Unit)` — all three change from `() -> Unit` to `(ReportDetails) -> Unit`

No automated test for this task — Compose UI wiring.

- [ ] **Step 1: Create `ReportDetails.kt`**

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import android.net.Uri
import java.time.LocalDateTime

data class ReportDetails(
    val licensePlate: String,
    val make: String,
    val color: String,
    val address: String,
    val incidentDateTime: LocalDateTime,
    val violation: String,
    val obstruction: String,
    val photoUris: List<Uri>,
)
```

- [ ] **Step 2: Update `PhotoCaptureRoot` in `PhotoCaptureScreen.kt`**

Change `onWeiterRequested: () -> Unit,` to `onWeiterRequested: (ReportDetails) -> Unit,` in `PhotoCaptureRoot`'s signature (the type annotation only — it's still just forwarded unchanged to the inner `PhotoCaptureScreen(...)` call, no other change needed in `PhotoCaptureRoot`).

Add these two lines after the existing `val colorText by viewModel.colorText.collectAsState()` line:

```kotlin
    val violationText by viewModel.violationText.collectAsState()
    val obstructionText by viewModel.obstructionText.collectAsState()
```

In the `PhotoCaptureScreen(...)` call inside `PhotoCaptureRoot`, add these parameters (e.g. right after the existing `onColorTextChanged = viewModel::onColorTextChanged,` line):

```kotlin
            violationText = violationText,
            onViolationTextChanged = viewModel::onViolationTextChanged,
            obstructionText = obstructionText,
            onObstructionTextChanged = viewModel::onObstructionTextChanged,
```

- [ ] **Step 3: Update `PhotoCaptureScreen`'s signature and body**

Add these parameters to the `PhotoCaptureScreen` function signature, after `onColorTextChanged: (String) -> Unit,` and before `incidentDateTime: LocalDateTime,`:

```kotlin
    violationText: String,
    onViolationTextChanged: (String) -> Unit,
    obstructionText: String,
    onObstructionTextChanged: (String) -> Unit,
```

Change `onWeiterRequested: () -> Unit,` to `onWeiterRequested: (ReportDetails) -> Unit,` in the same signature.

Add this new section inside the `Column`, right after the existing "Aktuellen Standort verwenden" `Button` and before the "Weiter" `Button`:

```kotlin
            Text(
                text = "Verstoß",
                style = MaterialTheme.typography.titleLarge,
            )

            RequiredTextField(
                value = violationText,
                onValueChange = onViolationTextChanged,
                label = "Verstoß",
            )

            RequiredTextField(
                value = obstructionText,
                onValueChange = onObstructionTextChanged,
                label = "Behinderung",
            )
```

Replace the existing "Weiter" `Button` block:

```kotlin
            Button(
                onClick = onWeiterRequested,
                enabled = licensePlateText.isNotBlank() && makeText.isNotBlank() && colorText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Weiter")
            }
```

with:

```kotlin
            Button(
                onClick = {
                    onWeiterRequested(
                        ReportDetails(
                            licensePlate = licensePlateText,
                            make = makeText,
                            color = colorText,
                            address = addressText,
                            incidentDateTime = incidentDateTime,
                            violation = violationText,
                            obstruction = obstructionText,
                            photoUris = photoUris,
                        ),
                    )
                },
                enabled = licensePlateText.isNotBlank() && makeText.isNotBlank() && colorText.isNotBlank() &&
                    violationText.isNotBlank() && obstructionText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Weiter")
            }
```

- [ ] **Step 4: Update `PhotoCaptureNavigation.kt`**

Replace the file's contents with:

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.wegefrei.app.feature.photocapture.api.PhotoCaptureRoute

fun NavGraphBuilder.photoCaptureScreen(
    onOpenWitnessDetailsRequested: () -> Unit,
    onWeiterRequested: (ReportDetails) -> Unit,
) {
    composable<PhotoCaptureRoute> {
        PhotoCaptureRoot(
            onOpenWitnessDetailsRequested = onOpenWitnessDetailsRequested,
            onWeiterRequested = onWeiterRequested,
        )
    }
}
```

- [ ] **Step 5: Build the module**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the full module test suite**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions from Task 1).

- [ ] **Step 7: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/ReportDetails.kt \
        feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt \
        feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureNavigation.kt
git commit -m "Add Verstoß/Behinderung UI and pass ReportDetails to Weiter"
```

---

### Task 3: Photo compression and `FileProvider` for email attachments

**Files:**
- Modify: `feature/photocapture/impl/build.gradle.kts`
- Modify: `feature/photocapture/impl/src/main/AndroidManifest.xml`
- Create: `feature/photocapture/impl/src/main/res/xml/file_paths.xml`
- Create: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/EmailAttachmentPreparer.kt`

**Interfaces:**
- Produces: `interface EmailAttachmentPreparer { suspend fun prepareAttachments(photoUris: List<Uri>): List<Uri> }`
- Produces: `internal class CompressingEmailAttachmentPreparer(context: Context) : EmailAttachmentPreparer`

No automated test for this task — real bitmap/file/Android I/O, consistent with this module's other platform wrappers (`ExifPhotoLocationExtractor`, `ExifPhotoTimestampExtractor`).

- [ ] **Step 1: Add the `androidx.core-ktx` dependency**

In `feature/photocapture/impl/build.gradle.kts`, in the `dependencies` block, add:

```kotlin
    implementation(libs.androidx.core.ktx)
```

- [ ] **Step 2: Add the `FileProvider` declaration**

In `feature/photocapture/impl/src/main/AndroidManifest.xml`, add inside the `<manifest>` element (as a sibling of the existing `<uses-permission>`/`<uses-feature>` elements):

```xml
    <application>
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
```

- [ ] **Step 3: Create the FileProvider path config**

Create `feature/photocapture/impl/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="report_photos" path="report_photos/" />
</paths>
```

- [ ] **Step 4: Create `EmailAttachmentPreparer.kt`**

```kotlin
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
```

- [ ] **Step 5: Verify the module compiles**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/photocapture/impl/build.gradle.kts \
        feature/photocapture/impl/src/main/AndroidManifest.xml \
        feature/photocapture/impl/src/main/res/xml/file_paths.xml \
        feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/EmailAttachmentPreparer.kt
git commit -m "Add photo compression and FileProvider for email attachments"
```

---

### Task 4: Expose witness values via `readWitnessDetails`

**Files:**
- Modify: `feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsRepository.kt`
- Create: `feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsSnapshotTest.kt`

**Interfaces:**
- Produces: `data class WitnessDetails(val name: String, val address: String, val email: String)` (public)
- Produces: `suspend fun readWitnessDetails(context: Context): WitnessDetails` (public)

- [ ] **Step 1: Add `WitnessDetails` and `readWitnessDetails` to `WitnessDetailsRepository.kt`**

Add this import to the file:

```kotlin
import kotlinx.coroutines.flow.first
```

Add these at the bottom of the file:

```kotlin
data class WitnessDetails(
    val name: String,
    val address: String,
    val email: String,
)

suspend fun readWitnessDetails(context: Context): WitnessDetails {
    val repository = DataStoreWitnessDetailsRepository(context)
    return WitnessDetails(
        name = repository.name.first(),
        address = repository.address.first(),
        email = repository.email.first(),
    )
}
```

- [ ] **Step 2: Add a Robolectric acceptance test**

Create `WitnessDetailsSnapshotTest.kt`, following the exact same single-`@Test`-method pattern as the existing `WitnessDetailsCompletionAcceptanceTest.kt` in this same directory (open that file first for the pattern — `@RunWith(RobolectricTestRunner::class)`, `RuntimeEnvironment.getApplication()`, `runTest`):

```kotlin
package de.wegefrei.app.feature.witness.impl

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Exercises [readWitnessDetails] against a real (Robolectric-backed)
 * [DataStoreWitnessDetailsRepository], proving the three DataStore reads are wired into the
 * correct [WitnessDetails] fields (e.g. no field mix-up) — the same class of plumbing risk
 * [WitnessDetailsCompletionAcceptanceTest] covers for [areWitnessDetailsComplete].
 *
 * Kept to a single [Test] method deliberately, matching [DataStoreWitnessDetailsRepositoryTest]
 * and [WitnessDetailsCompletionAcceptanceTest].
 */
@RunWith(RobolectricTestRunner::class)
class WitnessDetailsSnapshotTest {

    @Test
    fun `readWitnessDetails reflects the current persisted state`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = DataStoreWitnessDetailsRepository(context)

        repository.saveName("Max Mustermann")
        repository.saveAddress("Musterstraße 1")
        repository.saveEmail("max@example.com")

        val details = readWitnessDetails(context)

        assertEquals(WitnessDetails("Max Mustermann", "Musterstraße 1", "max@example.com"), details)
    }
}
```

- [ ] **Step 3: Run the test to verify it passes**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.witness.impl.WitnessDetailsSnapshotTest"`
Expected: PASS.

- [ ] **Step 4: Run the full module test suite**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass, no regressions.

- [ ] **Step 5: Commit**

```bash
git add feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsRepository.kt \
        feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsSnapshotTest.kt
git commit -m "Expose witness details values via readWitnessDetails"
```

---

### Task 5: `EmailReportComposer` — building the email subject, body, and intent

**Files:**
- Create: `app/src/main/java/de/wegefrei/app/EmailReportComposer.kt`
- Create: `app/src/test/java/de/wegefrei/app/EmailReportComposerTest.kt`

**Interfaces:**
- Consumes: `WitnessDetails` (Task 4), `ReportDetails` (Task 2)
- Produces: `fun buildReportEmailSubject(): String`, `fun buildReportEmailBody(witness: WitnessDetails, report: ReportDetails): String`, `fun buildReportEmailIntent(subject: String, body: String, attachmentUris: List<Uri>): Intent`

`buildReportEmailSubject` and `buildReportEmailBody` are pure and get tests. `buildReportEmailIntent` is a thin `Intent`-construction wrapper and is left untested, consistent with this codebase's pattern for real-Android-API wrappers.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/de/wegefrei/app/EmailReportComposerTest.kt`:

```kotlin
package de.wegefrei.app

import android.net.Uri
import de.wegefrei.app.feature.photocapture.impl.ReportDetails
import de.wegefrei.app.feature.witness.impl.WitnessDetails
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class EmailReportComposerTest {

    @Test
    fun `buildReportEmailBody matches the required template exactly`() {
        val witness = WitnessDetails(
            name = "Max Mustermann",
            address = "Musterstraße 1, 12345 Musterstadt",
            email = "max@example.com",
        )
        val report = ReportDetails(
            licensePlate = "KS-T 2394",
            make = "Ford",
            color = "Silber",
            address = "Musterplatz 5, 12345 Musterstadt",
            incidentDateTime = LocalDateTime.of(2011, 10, 6, 11, 27),
            violation = "Parken im absoluten Halteverbot und Radfahrstreifen, mehr als drei Minuten, kein Fahrzeughalter in der Nähe",
            obstruction = "Ich als Radfahrer muss auf die reguläre Fahrspur ausweichen",
            photoUris = emptyList(),
        )

        val body = buildReportEmailBody(witness, report)

        val expected = """
            Sehr geehrte Damen und Herren,

            ich möchte folgende Verkehrsordnungswidrigkeit zur Anzeige bringen, mit der Bitte um Weiterverfolgung:

                Anzeigender = Zeuge: Max Mustermann, Musterstraße 1, 12345 Musterstadt, E-Mail-Adresse: max@example.com
                Weitere Zeugen: -
                Tatörtlichkeit: Musterplatz 5, 12345 Musterstadt
                Tatzeit(en)/Zeit der Feststellung: 06.10.2011 11:27
                Angaben zum Fahrzeug, das falsch gestanden hat: KS-T 2394, Ford, Silber, PKW
                Angaben zum Verkehrsverstoß: Parken im absoluten Halteverbot und Radfahrstreifen, mehr als drei Minuten, kein Fahrzeughalter in der Nähe
                Angaben zu einer konkreten Verkehrsbehinderung oder -gefährdung: Ich als Radfahrer muss auf die reguläre Fahrspur ausweichen

            Meine oben gemachten Angaben einschließlich meiner Personalien sind zutreffend und vollständig (§111 OWiG). Mir ist bewusst, dass ich als Zeuge zur wahrheitsgemäßen Aussage (§ 57 und § 161a StPO i. V. m. § 46 OWiG) und auch zu einem möglichen Erscheinen vor Gericht verpflichtet bin. Vorsätzlich falsche Angaben zu angeblichen Ordnungswidrigkeiten können eine Straftat (§ 164 StGB) darstellen.

            Mit freundlichen Grüßen
            Max Mustermann
        """.trimIndent()

        assertEquals(expected, body)
    }

    @Test
    fun `buildReportEmailSubject returns a fixed subject`() {
        assertEquals("Anzeige einer Verkehrsordnungswidrigkeit", buildReportEmailSubject())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "de.wegefrei.app.EmailReportComposerTest"`
Expected: FAIL — `buildReportEmailBody`/`buildReportEmailSubject` are unresolved references.

- [ ] **Step 3: Create `EmailReportComposer.kt`**

```kotlin
package de.wegefrei.app

import android.content.Intent
import android.net.Uri
import de.wegefrei.app.feature.photocapture.impl.ReportDetails
import de.wegefrei.app.feature.witness.impl.WitnessDetails
import java.time.format.DateTimeFormatter
import java.util.Locale

private val REPORT_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)

fun buildReportEmailSubject(): String = "Anzeige einer Verkehrsordnungswidrigkeit"

fun buildReportEmailBody(witness: WitnessDetails, report: ReportDetails): String {
    val incidentDateTime = report.incidentDateTime.format(REPORT_DATE_TIME_FORMATTER)
    return """
        Sehr geehrte Damen und Herren,

        ich möchte folgende Verkehrsordnungswidrigkeit zur Anzeige bringen, mit der Bitte um Weiterverfolgung:

            Anzeigender = Zeuge: ${witness.name}, ${witness.address}, E-Mail-Adresse: ${witness.email}
            Weitere Zeugen: -
            Tatörtlichkeit: ${report.address}
            Tatzeit(en)/Zeit der Feststellung: $incidentDateTime
            Angaben zum Fahrzeug, das falsch gestanden hat: ${report.licensePlate}, ${report.make}, ${report.color}, PKW
            Angaben zum Verkehrsverstoß: ${report.violation}
            Angaben zu einer konkreten Verkehrsbehinderung oder -gefährdung: ${report.obstruction}

        Meine oben gemachten Angaben einschließlich meiner Personalien sind zutreffend und vollständig (§111 OWiG). Mir ist bewusst, dass ich als Zeuge zur wahrheitsgemäßen Aussage (§ 57 und § 161a StPO i. V. m. § 46 OWiG) und auch zu einem möglichen Erscheinen vor Gericht verpflichtet bin. Vorsätzlich falsche Angaben zu angeblichen Ordnungswidrigkeiten können eine Straftat (§ 164 StGB) darstellen.

        Mit freundlichen Grüßen
        ${witness.name}
    """.trimIndent()
}

fun buildReportEmailIntent(subject: String, body: String, attachmentUris: List<Uri>): Intent {
    val action = if (attachmentUris.isEmpty()) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
    return Intent(action).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        if (attachmentUris.isNotEmpty()) {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "de.wegefrei.app.EmailReportComposerTest"`
Expected: PASS (2 tests).

If the exact whitespace/indentation of the multi-line `"""..."""` template in `EmailReportComposerTest.kt`'s `expected` value doesn't byte-for-byte match `buildReportEmailBody`'s output (Kotlin's `trimIndent()` strips the *common* leading whitespace across all lines, so mismatched indentation between the test file and the implementation file can produce a mismatch even though both "look" the same visually) — fix by carefully aligning the indentation of the `"""..."""` block in `EmailReportComposerTest.kt` to exactly match `EmailReportComposer.kt`'s block, or by comparing the actual failure diff and adjusting whichever side is inconsistent. Do not change the required template text itself to make the test pass — only whitespace/indentation may need adjusting.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/wegefrei/app/EmailReportComposer.kt \
        app/src/test/java/de/wegefrei/app/EmailReportComposerTest.kt
git commit -m "Add EmailReportComposer for building the report email"
```

---

### Task 6: Wire "Weiter" to launch the email

**Files:**
- Modify: `app/src/main/java/de/wegefrei/app/MainActivity.kt`

**Interfaces:**
- Consumes: `ReportDetails` (Task 2), `EmailAttachmentPreparer`/`CompressingEmailAttachmentPreparer` (Task 3), `WitnessDetails`/`readWitnessDetails` (Task 4), `buildReportEmailSubject`/`buildReportEmailBody`/`buildReportEmailIntent` (Task 5)

No automated test for this task — app-module wiring.

- [ ] **Step 1: Replace `MainActivity.kt`'s contents**

```kotlin
package de.wegefrei.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import de.wegefrei.app.core.designsystem.WegefreiTheme
import de.wegefrei.app.feature.photocapture.api.PhotoCaptureRoute
import de.wegefrei.app.feature.photocapture.impl.CompressingEmailAttachmentPreparer
import de.wegefrei.app.feature.photocapture.impl.photoCaptureScreen
import de.wegefrei.app.feature.witness.api.WitnessDetailsRoute
import de.wegefrei.app.feature.witness.impl.areWitnessDetailsComplete
import de.wegefrei.app.feature.witness.impl.readWitnessDetails
import de.wegefrei.app.feature.witness.impl.witnessDetailsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WegefreiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WegefreiNavHost()
                }
            }
        }
    }
}

@Composable
private fun WegefreiNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val attachmentPreparer = remember { CompressingEmailAttachmentPreparer(context) }

    NavHost(navController = navController, startDestination = PhotoCaptureRoute) {
        photoCaptureScreen(
            onOpenWitnessDetailsRequested = {
                navController.navigate(WitnessDetailsRoute) { launchSingleTop = true }
            },
            onWeiterRequested = { reportDetails ->
                coroutineScope.launch {
                    if (!areWitnessDetailsComplete(context)) {
                        navController.navigate(WitnessDetailsRoute) { launchSingleTop = true }
                        return@launch
                    }

                    val witnessDetails = readWitnessDetails(context)
                    val attachmentUris = attachmentPreparer.prepareAttachments(reportDetails.photoUris)
                    val subject = buildReportEmailSubject()
                    val body = buildReportEmailBody(witnessDetails, reportDetails)
                    val intent = buildReportEmailIntent(subject, body, attachmentUris)

                    try {
                        context.startActivity(
                            Intent.createChooser(intent, "E-Mail senden"),
                        )
                    } catch (e: ActivityNotFoundException) {
                        // No email app installed — silent no-op, consistent with this app's
                        // existing style for failure paths with no dedicated error UI yet.
                    }
                }
            },
        )
        witnessDetailsScreen(
            onBackRequested = { navController.navigateUp() },
        )
    }
}
```

- [ ] **Step 2: Build the project**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full project test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass across every module (no regressions).

- [ ] **Step 4: Manual verification**

Install and run the app (`./gradlew :app:installDebug`, or use the `run` skill if available):
- Fill in "Meine Angaben" completely, add at least one photo, fill in all report fields (Kennzeichen, Marke, Farbe, Verstoß, Behinderung), tap "Weiter".
- Confirm an email app chooser/composer opens (not a crash, not silently nothing — unless no email app is installed on the test device/emulator, in which case note that honestly).
- Confirm the subject is "Anzeige einer Verkehrsordnungswidrigkeit", the body matches the required template with real values substituted, and the photo is attached.
- With "Meine Angaben" incomplete, tap "Weiter" again → confirm it still redirects there instead of opening an email (unchanged prior behavior).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/wegefrei/app/MainActivity.kt
git commit -m "Launch the report email from Weiter"
```
