# Address Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user see an address auto-derived from the first photo's GPS EXIF data (reverse-geocoded), edit it in a free-text field, or replace it with their current GPS location.

**Architecture:** Three small Android-facing wrapper interfaces (`PhotoLocationExtractor`, `AddressLookupService`, `CurrentLocationProvider`) live in `feature/photocapture/impl` and are called directly from Compose (`LaunchedEffect` / button click coroutines), which push results into a Context-free `PhotoCaptureViewModel` that owns the address text state and the "don't clobber manual edits" rule.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.exifinterface`, plain `HttpURLConnection` + `org.json` against the public Nominatim reverse-geocoding API, `android.location.LocationManager`.

## Global Constraints

- Module: everything lands in `feature/photocapture/impl` — no new feature module, no new navigation destination (per design spec).
- minSdk is 33, so `LocationManager.getCurrentLocation()` (API 30+) can be used directly with no legacy fallback.
- No DI framework in this codebase — dependencies are instantiated directly (`remember { SomeImpl(...) }` in Composables), matching the existing `PhotoCaptureViewModel = viewModel()` pattern.
- Nominatim requests must set a descriptive `User-Agent` header (usage policy requirement) — use `"wegefrei-android-app"`.
- All failure paths (missing permission, no GPS tags, network error, malformed response) resolve to `null` and are swallowed silently — no error UI, per product decision in the spec.
- UI copy is German, matching existing strings ("Aus Galerie wählen", "Foto aufnehmen").
- `PhotoLocationExtractor`, `CurrentLocationProvider`, and the network call in `AddressLookupService` are thin Android/network wrappers and are intentionally left without automated tests, consistent with `CameraCaptureScreen` today. Only pure logic (`parseNominatimDisplayName`, the ViewModel's address state rules) gets unit tests.
- Package for all new files: `de.wegefrei.app.feature.photocapture.impl`.

---

### Task 1: `LatLng` + `AddressLookupService` (Nominatim reverse geocoding)

**Files:**
- Create: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/LatLng.kt`
- Create: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/AddressLookupService.kt`
- Test: `feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/NominatimDisplayNameParsingTest.kt`
- Modify: `feature/photocapture/impl/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `data class LatLng(val latitude: Double, val longitude: Double)`
- Produces: `interface AddressLookupService { suspend fun reverseGeocode(latitude: Double, longitude: Double): String? }`
- Produces: `internal class NominatimAddressLookupService : AddressLookupService`
- Produces: `internal fun parseNominatimDisplayName(json: String): String?`

- [ ] **Step 1: Write the failing test for the pure JSON-parsing function**

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NominatimDisplayNameParsingTest {

    @Test
    fun `parseNominatimDisplayName extracts display_name from a successful response`() {
        val json = """{"display_name":"Alexanderplatz, Mitte, Berlin, Deutschland"}"""

        val result = parseNominatimDisplayName(json)

        assertEquals("Alexanderplatz, Mitte, Berlin, Deutschland", result)
    }

    @Test
    fun `parseNominatimDisplayName returns null when display_name is missing`() {
        val json = """{"error":"Unable to geocode"}"""

        val result = parseNominatimDisplayName(json)

        assertNull(result)
    }

    @Test
    fun `parseNominatimDisplayName returns null for malformed json`() {
        val json = "not json"

        val result = parseNominatimDisplayName(json)

        assertNull(result)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.NominatimDisplayNameParsingTest"`
Expected: FAIL — `parseNominatimDisplayName` is unresolved (function doesn't exist yet).

- [ ] **Step 3: Create `LatLng.kt`**

```kotlin
package de.wegefrei.app.feature.photocapture.impl

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)
```

- [ ] **Step 4: Create `AddressLookupService.kt` with the minimal implementation**

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal fun parseNominatimDisplayName(json: String): String? =
    try {
        val displayName = JSONObject(json).optString("display_name", "")
        displayName.ifBlank { null }
    } catch (e: JSONException) {
        null
    }

interface AddressLookupService {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String?
}

internal class NominatimAddressLookupService : AddressLookupService {

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude",
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "wegefrei-android-app")
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    return@withContext null
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                parseNominatimDisplayName(body)
            } catch (e: IOException) {
                null
            }
        }
}
```

- [ ] **Step 5: Add the `INTERNET` permission**

In `feature/photocapture/impl/src/main/AndroidManifest.xml`, add alongside the existing `CAMERA` permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.NominatimDisplayNameParsingTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/LatLng.kt \
        feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/AddressLookupService.kt \
        feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/NominatimDisplayNameParsingTest.kt \
        feature/photocapture/impl/src/main/AndroidManifest.xml
git commit -m "Add Nominatim-based reverse geocoding for addresses"
```

---

### Task 2: `PhotoLocationExtractor` (EXIF GPS extraction)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/photocapture/impl/build.gradle.kts`
- Modify: `feature/photocapture/impl/src/main/AndroidManifest.xml`
- Create: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoLocationExtractor.kt`

**Interfaces:**
- Consumes: `LatLng` (Task 1)
- Produces: `interface PhotoLocationExtractor { suspend fun extractLocation(uri: Uri): LatLng? }`
- Produces: `internal class ExifPhotoLocationExtractor(private val context: Context) : PhotoLocationExtractor`

No automated test for this task — it's a thin wrapper around `ContentResolver`/`ExifInterface`, consistent with the "left untested" constraint above (same as `CameraCaptureScreen`).

- [ ] **Step 1: Add the `androidx.exifinterface` dependency to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` (near the other androidx entries):

```toml
androidxExifinterface = "1.3.7"
```

Under `[libraries]` (near the other androidx entries):

```toml
androidx-exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version.ref = "androidxExifinterface" }
```

- [ ] **Step 2: Add the dependency to the module**

In `feature/photocapture/impl/build.gradle.kts`, in the `dependencies` block, add next to the other `implementation` lines:

```kotlin
    implementation(libs.androidx.exifinterface)
```

- [ ] **Step 3: Add the `ACCESS_MEDIA_LOCATION` permission**

In `feature/photocapture/impl/src/main/AndroidManifest.xml`, add alongside `INTERNET`:

```xml
<uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION" />
```

- [ ] **Step 4: Create `PhotoLocationExtractor.kt`**

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface PhotoLocationExtractor {
    suspend fun extractLocation(uri: Uri): LatLng?
}

internal class ExifPhotoLocationExtractor(
    private val context: Context,
) : PhotoLocationExtractor {

    override suspend fun extractLocation(uri: Uri): LatLng? = withContext(Dispatchers.IO) {
        val originalUri = try {
            MediaStore.setRequireOriginal(uri)
        } catch (e: UnsupportedOperationException) {
            uri
        }

        try {
            context.contentResolver.openInputStream(originalUri)?.use { stream ->
                ExifInterface(stream).latLong?.let { latLong ->
                    LatLng(latitude = latLong[0], longitude = latLong[1])
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}
```

- [ ] **Step 5: Verify the module compiles**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml \
        feature/photocapture/impl/build.gradle.kts \
        feature/photocapture/impl/src/main/AndroidManifest.xml \
        feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoLocationExtractor.kt
git commit -m "Add EXIF-based GPS extraction from photos"
```

---

### Task 3: `CurrentLocationProvider` (device GPS fallback)

**Files:**
- Modify: `feature/photocapture/impl/src/main/AndroidManifest.xml`
- Create: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/CurrentLocationProvider.kt`

**Interfaces:**
- Consumes: `LatLng` (Task 1)
- Produces: `interface CurrentLocationProvider { suspend fun getCurrentLocation(): LatLng? }`
- Produces: `internal class AndroidCurrentLocationProvider(private val context: Context) : CurrentLocationProvider`

No automated test for this task, same reasoning as Task 2 — the caller (Task 5) is responsible for holding the runtime permission before invoking this.

- [ ] **Step 1: Add the location permissions**

In `feature/photocapture/impl/src/main/AndroidManifest.xml`, add alongside the other permissions:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

- [ ] **Step 2: Create `CurrentLocationProvider.kt`**

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): LatLng?
}

internal class AndroidCurrentLocationProvider(
    private val context: Context,
) : CurrentLocationProvider {

    override suspend fun getCurrentLocation(): LatLng? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                context.mainExecutor,
            ) { location ->
                val result = location?.let { LatLng(latitude = it.latitude, longitude = it.longitude) }
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }
}
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/photocapture/impl/src/main/AndroidManifest.xml \
        feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/CurrentLocationProvider.kt
git commit -m "Add device GPS lookup for the current-location fallback"
```

---

### Task 4: `PhotoCaptureViewModel` address state

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt`
- Modify: `feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModelTest.kt`

**Interfaces:**
- Produces: `PhotoCaptureViewModel.addressText: StateFlow<String>`
- Produces: `PhotoCaptureViewModel.onAddressTextChanged(text: String)`
- Produces: `PhotoCaptureViewModel.onAddressAutoDetected(text: String)`
- Produces: `PhotoCaptureViewModel.onCurrentLocationAddressReceived(text: String)`

- [ ] **Step 1: Write the failing tests**

Add to `PhotoCaptureViewModelTest.kt` (inside the existing `PhotoCaptureViewModelTest` class, after the existing tests):

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.PhotoCaptureViewModelTest"`
Expected: FAIL — `addressText`, `onAddressTextChanged`, `onAddressAutoDetected`, `onCurrentLocationAddressReceived` are unresolved references.

- [ ] **Step 3: Implement the address state in `PhotoCaptureViewModel.kt`**

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val MAX_PHOTOS = 5

internal class PhotoCaptureViewModel : ViewModel() {

    private val _photoUris = MutableStateFlow<List<Uri>>(emptyList())
    val photoUris: StateFlow<List<Uri>> = _photoUris.asStateFlow()

    private val _addressText = MutableStateFlow("")
    val addressText: StateFlow<String> = _addressText.asStateFlow()

    private var hasUserEditedAddress = false

    fun onImagesPicked(uris: List<Uri>) {
        val newUris = uris.filter { it !in _photoUris.value }.distinct()
        _photoUris.value = (_photoUris.value + newUris).take(MAX_PHOTOS)
    }

    fun onPhotoCaptured(uri: Uri) {
        if (_photoUris.value.size < MAX_PHOTOS && uri !in _photoUris.value) {
            _photoUris.value = _photoUris.value + uri
        }
    }

    fun onPhotoRemoved(index: Int) {
        _photoUris.value = _photoUris.value.toMutableList().apply { removeAt(index) }
    }

    fun onAddressTextChanged(text: String) {
        hasUserEditedAddress = true
        _addressText.value = text
    }

    fun onAddressAutoDetected(text: String) {
        if (!hasUserEditedAddress) {
            _addressText.value = text
        }
    }

    fun onCurrentLocationAddressReceived(text: String) {
        hasUserEditedAddress = true
        _addressText.value = text
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.PhotoCaptureViewModelTest"`
Expected: PASS (all tests in the class, old and new).

- [ ] **Step 5: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt \
        feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModelTest.kt
git commit -m "Add address text state to PhotoCaptureViewModel"
```

---

### Task 5: Wire address UI into `PhotoCaptureScreen`

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`

**Interfaces:**
- Consumes: `PhotoCaptureViewModel.addressText`, `onAddressTextChanged`, `onAddressAutoDetected`, `onCurrentLocationAddressReceived` (Task 4); `ExifPhotoLocationExtractor`, `PhotoLocationExtractor.extractLocation` (Task 2); `NominatimAddressLookupService`, `AddressLookupService.reverseGeocode` (Task 1); `AndroidCurrentLocationProvider`, `CurrentLocationProvider.getCurrentLocation` (Task 3); `LatLng.latitude`/`longitude` (Task 1).

No automated test for this task — it's Compose UI wiring, consistent with the rest of this screen (no existing Compose UI tests in the module).

- [ ] **Step 1: Update `PhotoCaptureRoot` to drive the address lookups**

Replace the existing `PhotoCaptureRoot` function in `PhotoCaptureScreen.kt` with:

```kotlin
@Composable
internal fun PhotoCaptureRoot(
    viewModel: PhotoCaptureViewModel = viewModel(),
) {
    val context = LocalContext.current
    val photoUris by viewModel.photoUris.collectAsState()
    val addressText by viewModel.addressText.collectAsState()
    var showCamera by remember { mutableStateOf(false) }
    var isLookingUpAddress by remember { mutableStateOf(false) }

    val locationExtractor = remember { ExifPhotoLocationExtractor(context) }
    val addressLookupService = remember { NominatimAddressLookupService() }
    val currentLocationProvider = remember { AndroidCurrentLocationProvider(context) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(photoUris.firstOrNull()) {
        val firstUri = photoUris.firstOrNull() ?: return@LaunchedEffect
        isLookingUpAddress = true
        val latLng = locationExtractor.extractLocation(firstUri)
        if (latLng != null) {
            val address = addressLookupService.reverseGeocode(latLng.latitude, latLng.longitude)
            if (address != null) {
                viewModel.onAddressAutoDetected(address)
            }
        }
        isLookingUpAddress = false
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                coroutineScope.launch {
                    isLookingUpAddress = true
                    val latLng = currentLocationProvider.getCurrentLocation()
                    if (latLng != null) {
                        val address = addressLookupService.reverseGeocode(latLng.latitude, latLng.longitude)
                        if (address != null) {
                            viewModel.onCurrentLocationAddressReceived(address)
                        }
                    }
                    isLookingUpAddress = false
                }
            }
        },
    )

    if (showCamera) {
        CameraCaptureScreen(
            onPhotoCaptured = { uri ->
                viewModel.onPhotoCaptured(uri)
                showCamera = false
            },
        )
    } else {
        PhotoCaptureScreen(
            photoUris = photoUris,
            onImagesPicked = viewModel::onImagesPicked,
            onTakePhotoRequested = { showCamera = true },
            onPhotoRemoved = viewModel::onPhotoRemoved,
            addressText = addressText,
            onAddressTextChanged = viewModel::onAddressTextChanged,
            isLookingUpAddress = isLookingUpAddress,
            onUseCurrentLocationRequested = {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
        )
    }
}
```

- [ ] **Step 2: Update the `PhotoCaptureScreen` composable signature and body**

Replace the existing `PhotoCaptureScreen` function signature:

```kotlin
@Composable
internal fun PhotoCaptureScreen(
    photoUris: List<Uri>,
    onImagesPicked: (List<Uri>) -> Unit,
    onTakePhotoRequested: () -> Unit,
    onPhotoRemoved: (Int) -> Unit,
    addressText: String,
    onAddressTextChanged: (String) -> Unit,
    isLookingUpAddress: Boolean,
    onUseCurrentLocationRequested: () -> Unit,
) {
```

Add this block inside the `Column`, right after the existing "Foto aufnehmen" `Button` and before the closing `}` of the `Column`:

```kotlin
            Text(
                text = "Adresse",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = addressText,
                onValueChange = onAddressTextChanged,
                label = { Text(text = "Adresse") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (isLookingUpAddress) {
                CircularProgressIndicator()
            }

            Button(
                onClick = onUseCurrentLocationRequested,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Aktuellen Standort verwenden")
            }
```

- [ ] **Step 3: Add the new imports**

At the top of `PhotoCaptureScreen.kt`, add:

```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
```

- [ ] **Step 4: Build the module**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full module test suite**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions from earlier tasks).

- [ ] **Step 6: Manual verification**

Install and run the app on a device/emulator (`./gradlew :app:installDebug`, or use the `run` skill if available):
- Pick a gallery photo that has GPS EXIF data (with `ACCESS_MEDIA_LOCATION` granted when prompted) → confirm the address field fills in automatically after a short delay.
- Edit the address field manually, then pick another such photo → confirm the manual text is *not* overwritten.
- Tap "Aktuellen Standort verwenden", grant location permission → confirm the field is replaced with the reverse-geocoded current-location address.
- Deny the location permission → confirm nothing crashes and the field remains editable.

- [ ] **Step 7: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt
git commit -m "Show and edit an address derived from the first photo or current location"
```
