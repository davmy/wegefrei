# Vehicle Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three required vehicle-identification fields (license plate, make, color) to the report screen, with required-field visual validation and a "Weiter" button that enables once all three are filled.

**Architecture:** Three new plain `StateFlow<String>` fields on the existing `PhotoCaptureViewModel`, mirroring the existing `addressText` pattern but without auto-fill logic. UI wiring adds a "Fahrzeug" section above the existing "Tatort" section, using a small shared `RequiredTextField` composable for the touched/error-display behavior, and a "Weiter" button at the bottom of the screen.

**Tech Stack:** Kotlin, Jetpack Compose, existing `PhotoCaptureViewModel`/`PhotoCaptureScreen` in `feature/photocapture/impl`.

## Global Constraints

- All changes land in two existing files: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt` and `.../PhotoCaptureScreen.kt`. No new files, no new module.
- Variable/function names are English (`licensePlateText`, `makeText`, `colorText`, `onLicensePlateTextChanged`, `onMakeTextChanged`, `onColorTextChanged`), per explicit instruction — only UI-facing copy stays German.
- UI copy: field labels are "Kennzeichen", "Marke", "Farbe" (each rendered with a `*` suffix to indicate required); the "Fahrzeug" section header; "Pflichtfeld" as the required-field error message; "Weiter" as the button label. Matches the existing German UI copy style.
- "Required" means non-blank (`.isNotBlank()`) — no format validation.
- The "Weiter" button's `onClick` is an intentional no-op (`{}`) — there is no next screen yet.
- Touched/error-display state is Compose-local (`remember`), not part of the ViewModel — matches how `isLookingUpAddress` is handled today. No test coverage is expected for this UI-only behavior, consistent with the rest of this screen (no Compose UI tests exist in this module).
- Package for all changes: `de.wegefrei.app.feature.photocapture.impl`.

---

### Task 1: `PhotoCaptureViewModel` vehicle fields

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt`
- Modify: `feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModelTest.kt`

**Interfaces:**
- Produces: `PhotoCaptureViewModel.licensePlateText: StateFlow<String>`, `onLicensePlateTextChanged(text: String)`
- Produces: `PhotoCaptureViewModel.makeText: StateFlow<String>`, `onMakeTextChanged(text: String)`
- Produces: `PhotoCaptureViewModel.colorText: StateFlow<String>`, `onColorTextChanged(text: String)`

- [ ] **Step 1: Write the failing tests**

Add to `PhotoCaptureViewModelTest.kt` (inside the existing `PhotoCaptureViewModelTest` class, after the existing tests):

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.PhotoCaptureViewModelTest"`
Expected: FAIL — `licensePlateText`, `onLicensePlateTextChanged`, `makeText`, `onMakeTextChanged`, `colorText`, `onColorTextChanged` are unresolved references.

- [ ] **Step 3: Implement the vehicle fields in `PhotoCaptureViewModel.kt`**

Add these members to the `PhotoCaptureViewModel` class, after the existing `_addressText`/`addressText` declarations and before `hasUserEditedAddress`:

```kotlin
    private val _licensePlateText = MutableStateFlow("")
    val licensePlateText: StateFlow<String> = _licensePlateText.asStateFlow()

    private val _makeText = MutableStateFlow("")
    val makeText: StateFlow<String> = _makeText.asStateFlow()

    private val _colorText = MutableStateFlow("")
    val colorText: StateFlow<String> = _colorText.asStateFlow()
```

Add these functions to the class, after `onCurrentLocationAddressReceived`:

```kotlin
    fun onLicensePlateTextChanged(text: String) {
        _licensePlateText.value = text
    }

    fun onMakeTextChanged(text: String) {
        _makeText.value = text
    }

    fun onColorTextChanged(text: String) {
        _colorText.value = text
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.photocapture.impl.PhotoCaptureViewModelTest"`
Expected: PASS (all tests in the class, old and new).

- [ ] **Step 5: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModel.kt \
        feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureViewModelTest.kt
git commit -m "Add vehicle fields (license plate, make, color) to PhotoCaptureViewModel"
```

---

### Task 2: Wire vehicle fields UI into `PhotoCaptureScreen`

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`

**Interfaces:**
- Consumes: `PhotoCaptureViewModel.licensePlateText`, `makeText`, `colorText`, `onLicensePlateTextChanged`, `onMakeTextChanged`, `onColorTextChanged` (Task 1)

No automated test for this task — Compose UI wiring, consistent with the rest of this screen.

- [ ] **Step 1: Add the `onFocusChanged` import**

At the top of `PhotoCaptureScreen.kt`, add:

```kotlin
import androidx.compose.ui.focus.onFocusChanged
```

- [ ] **Step 2: Update `PhotoCaptureRoot` to collect and pass through the three new fields**

In `PhotoCaptureRoot`, add these lines after the existing `val addressText by viewModel.addressText.collectAsState()` line:

```kotlin
    val licensePlateText by viewModel.licensePlateText.collectAsState()
    val makeText by viewModel.makeText.collectAsState()
    val colorText by viewModel.colorText.collectAsState()
```

In the `PhotoCaptureScreen(...)` call inside `PhotoCaptureRoot`, add these parameters (anywhere among the existing ones, e.g. right after `onPhotoRemoved = viewModel::onPhotoRemoved,`):

```kotlin
            licensePlateText = licensePlateText,
            onLicensePlateTextChanged = viewModel::onLicensePlateTextChanged,
            makeText = makeText,
            onMakeTextChanged = viewModel::onMakeTextChanged,
            colorText = colorText,
            onColorTextChanged = viewModel::onColorTextChanged,
```

- [ ] **Step 3: Add the new parameters to the `PhotoCaptureScreen` composable signature**

Add these parameters to the `PhotoCaptureScreen` function signature, after `onPhotoRemoved: (Int) -> Unit,` and before `addressText: String,`:

```kotlin
    licensePlateText: String,
    onLicensePlateTextChanged: (String) -> Unit,
    makeText: String,
    onMakeTextChanged: (String) -> Unit,
    colorText: String,
    onColorTextChanged: (String) -> Unit,
```

- [ ] **Step 4: Add the `RequiredTextField` helper composable**

Add this new private composable function in `PhotoCaptureScreen.kt`, right after the `PhotoCaptureScreen` function's closing brace (before `PhotoThumbnail`):

```kotlin
@Composable
private fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var touched by remember { mutableStateOf(false) }
    val isError = touched && value.isBlank()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "$label *") },
        isError = isError,
        supportingText = if (isError) {
            { Text(text = "Pflichtfeld") }
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState -> if (!focusState.isFocused) touched = true },
    )
}
```

- [ ] **Step 5: Add the "Fahrzeug" section above "Tatort"**

In `PhotoCaptureScreen`'s `Column`, insert this block right after the existing "Foto aufnehmen" `Button` and before the existing `Text(text = "Tatort", ...)` block:

```kotlin
            Text(
                text = "Fahrzeug",
                style = MaterialTheme.typography.titleLarge,
            )

            RequiredTextField(
                value = licensePlateText,
                onValueChange = onLicensePlateTextChanged,
                label = "Kennzeichen",
            )

            RequiredTextField(
                value = makeText,
                onValueChange = onMakeTextChanged,
                label = "Marke",
            )

            RequiredTextField(
                value = colorText,
                onValueChange = onColorTextChanged,
                label = "Farbe",
            )
```

- [ ] **Step 6: Add the "Weiter" button at the bottom of the screen**

In `PhotoCaptureScreen`'s `Column`, add this block right after the existing "Aktuellen Standort verwenden" `Button` (the last item in the `Column`, before its closing brace):

```kotlin
            Button(
                onClick = {},
                enabled = licensePlateText.isNotBlank() && makeText.isNotBlank() && colorText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Weiter")
            }
```

- [ ] **Step 7: Build the module**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run the full module test suite**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions from Task 1).

- [ ] **Step 9: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt
git commit -m "Add Fahrzeug section (license plate, make, color) and Weiter button"
```
