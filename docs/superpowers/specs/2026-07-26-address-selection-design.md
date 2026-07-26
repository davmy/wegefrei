# Address selection design

## Context

Reports in wegefrei currently only capture photos (`feature/photocapture`). The
next step in building out the report flow is letting the user attach an
address to the report. Rather than requiring the user to type an address from
scratch, we want to:

- Try to derive it automatically from the first photo's GPS EXIF data,
  reverse-geocoded into a human-readable address.
- Always let the user type or edit the address in a free-text field.
- Offer a "use current location" fallback/override that fetches the device's
  current GPS position and reverse-geocodes that instead.

This is scoped to fit into the existing photo capture screen (no new
navigation step, no new feature module) since there's no multi-step report
wizard yet.

## Non-goals

- GPS-tagging photos captured with the in-app camera. CameraX currently writes
  captures to app-private storage with no location metadata attached, so EXIF
  extraction will only ever succeed for gallery-picked photos that still carry
  location data. Making the camera itself location-aware is a separate
  feature.
- Any address validation, autocomplete, or structured address fields (street/
  city/zip). The manual field is plain free text.
- A dedicated report/wizard flow. This lands directly on
  `PhotoCaptureScreen`.

## Components

All new code lives in `feature/photocapture/impl`.

### `PhotoLocationExtractor`

```kotlin
interface PhotoLocationExtractor {
    suspend fun extractLocation(uri: Uri): LatLng?
}
```

`ExifPhotoLocationExtractor` implementation:
- Opens the photo via `ContentResolver`, using
  `MediaStore.setRequireOriginal(uri)` first so redacted GPS EXIF tags are
  unlocked (requires `ACCESS_MEDIA_LOCATION`).
- Reads lat/lon via `androidx.exifinterface.media.ExifInterface.getLatLong()`.
- Returns `null` on missing permission, missing tags, or any I/O error — this
  path is explicitly best-effort and silent (no error UI), per product
  decision below.

### `AddressLookupService`

```kotlin
interface AddressLookupService {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String?
}
```

`NominatimAddressLookupService` implementation:
- Calls the public Nominatim `/reverse` HTTP endpoint
  (`https://nominatim.openstreetmap.org/reverse?format=json&lat=..&lon=..`)
  via plain `HttpURLConnection` on `Dispatchers.IO` — no new HTTP client
  dependency.
- Sets a descriptive `User-Agent` header identifying the app, per Nominatim's
  usage policy (required; requests without one may be blocked).
- Parses the JSON response's `display_name` field via `org.json.JSONObject`
  (part of the Android SDK, no new dependency).
- JSON parsing is factored into a standalone pure function,
  `parseNominatimDisplayName(json: String): String?`, so it can be unit
  tested without a real network call.
- Returns `null` on any network error, non-200 response, or missing field.

### `CurrentLocationProvider`

```kotlin
interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): LatLng?
}
```

`AndroidCurrentLocationProvider` implementation:
- Wraps `LocationManager.getCurrentLocation()` (available since API 30, safe
  given minSdk 33) in a `suspendCancellableCoroutine`.
- Caller (the Composable) is responsible for holding `ACCESS_FINE_LOCATION`
  before invoking this; the provider assumes the permission is already
  granted and returns `null` on `SecurityException` as a defensive fallback.

### `LatLng`

Small `data class LatLng(val latitude: Double, val longitude: Double)` shared
by the above.

## ViewModel changes (`PhotoCaptureViewModel`)

Stays Android-`Context`-free, matching its current design (only Uri-typed
state, no direct Android service calls). New state and events:

```kotlin
private val _addressText = MutableStateFlow("")
val addressText: StateFlow<String> = _addressText.asStateFlow()

private var hasUserEditedAddress = false

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
```

Rules:
- `onAddressAutoDetected` (EXIF → geocode result) never overwrites text the
  user has already typed or a current-location result they've already
  accepted.
- `onCurrentLocationAddressReceived` always overwrites, since it's an
  explicit user action, and afterwards is treated the same as a manual edit
  (further auto-detection from photo changes won't clobber it).

## UI changes (`PhotoCaptureRoot` / `PhotoCaptureScreen`)

- `PhotoCaptureRoot` gets a `LaunchedEffect(photoUris.firstOrNull())`: when
  the first photo in the list changes (added, or a new photo becomes first
  after removal), launch a coroutine that runs
  `PhotoLocationExtractor.extractLocation` →
  `AddressLookupService.reverseGeocode` → `viewModel.onAddressAutoDetected`.
  Any `null` at any step just ends the coroutine silently.
- `PhotoCaptureScreen` gains, below the photo grid:
  - An `OutlinedTextField` bound to `addressText` /
    `onAddressTextChanged`, always editable, labeled something like
    "Adresse".
  - A "Use current location" button, always visible (not conditional on
    whether EXIF succeeded). On click:
    1. Request `ACCESS_FINE_LOCATION` via
       `rememberLauncherForActivityResult(RequestPermission())` if not
       already granted.
    2. On grant, fetch location via `CurrentLocationProvider`, reverse
       geocode, call `onCurrentLocationAddressReceived`.
    3. On denial, do nothing further (no dedicated error UI, consistent with
       the EXIF path) — the field remains manually editable regardless.
  - A small loading indicator (e.g. a `CircularProgressIndicator` next to the
    field) shown while either lookup is in flight.

## Permissions / manifest

Add to `feature/photocapture/impl/src/main/AndroidManifest.xml`:
- `INTERNET` (Nominatim call)
- `ACCESS_MEDIA_LOCATION` (unlock EXIF GPS tags on MediaStore-backed URIs)
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` (current-location
  button)

## Dependencies

Add `androidx.exifinterface` to `gradle/libs.versions.toml` and as an
`implementation` dependency in
`feature/photocapture/impl/build.gradle.kts`. No new networking library.

## Error handling

Every failure path (missing permission, no GPS tags, network error, bad
response) resolves to `null` and is swallowed silently at the lookup level —
per the earlier product decision, we don't show "no location found" messaging.
The user always has the free-text field and the current-location button as
fallbacks.

## Testing

- Extend `PhotoCaptureViewModelTest` (Robolectric) with cases for:
  - `onAddressTextChanged` sets the text.
  - `onAddressAutoDetected` sets the text when nothing has been manually
    entered yet.
  - `onAddressAutoDetected` is a no-op after a manual edit.
  - `onCurrentLocationAddressReceived` always overwrites, including after a
    prior manual edit.
  - A later `onAddressAutoDetected` call is a no-op after
    `onCurrentLocationAddressReceived` (since that counts as a manual edit).
- Unit test `parseNominatimDisplayName` directly with sample JSON payloads
  (success, missing field, malformed JSON) — no network involved.
- `PhotoLocationExtractor`/`CurrentLocationProvider`/the HTTP call in
  `NominatimAddressLookupService` are thin Android/network wrappers and are
  left untested, consistent with `CameraCaptureScreen` today.
