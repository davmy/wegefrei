# Witness Details (Zeuge) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user enter and persist their own name/address/email once, reused for every future report, editable from a "Meine Angaben" screen reached via a menu on the report screen.

**Architecture:** A new `feature/witness` module pair (`api` = route, `impl` = screen + ViewModel + DataStore-backed repository), following the existing `feature/photocapture` convention exactly. The report screen (`feature/photocapture/impl`) gains a top app bar with an overflow menu that navigates there, wired through `MainActivity`'s nav graph — `feature/photocapture` never depends on `feature/witness`.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.datastore:datastore-preferences` (new), `androidx.compose.material:material-icons-core` (new), `androidx.navigation.compose`, `androidx.lifecycle.viewmodel` factory DSL (new usage pattern for this codebase).

## Global Constraints

- Package roots: `de.wegefrei.app.feature.witness.api` and `de.wegefrei.app.feature.witness.impl`.
- Variable/function names are English; UI copy is German: "Meine Angaben" (menu item + screen title), field labels "Name"/"Adresse"/"E-Mail" (each with a `*` suffix, all required), "Pflichtfeld" required-field error, "Ungültige E-Mail-Adresse" email-format error, "Zurück" back-button content description, "Menü" overflow-button content description.
- All three fields are required: same touched-then-invalid validation shape as the existing `RequiredTextField` in `feature/photocapture/impl` (`wasFocused`/`touched` flags, armed only on a genuine blur-after-focus — do not reintroduce the fixed initial-attach bug). This module has no shared UI kit with `feature/photocapture`, so the pattern is reimplemented locally as `WitnessTextField`, not imported.
- No gating/submit action on this screen — edits auto-save on every change, no explicit save button, no blocking behavior.
- `feature/witness/impl`'s `WitnessDetailsViewModel` is the first `ViewModel` in this codebase requiring a factory (it needs a `Context`-backed repository at construction) — use `androidx.lifecycle.viewmodel.viewModelFactory { initializer { ... } }` with `viewModel(factory = ...)`, not a DI framework.
- `DataStoreWitnessDetailsRepository`, `WitnessDetailsScreen`, `WitnessTextField`'s touched/error display, and the menu/navigation wiring are intentionally left without automated tests — real DataStore I/O and Compose UI wiring, consistent with this codebase's existing pattern (e.g. `ExifPhotoLocationExtractor`, `AndroidCurrentLocationProvider`). `WitnessDetailsViewModel` and the email-format validation function DO get tests, since they're pure/fake-testable logic.
- `feature/photocapture` and `feature/witness` must not depend on each other. The menu callback (`onOpenWitnessDetailsRequested`) and the back callback (`onBackRequested`) are plain `() -> Unit` lambdas threaded through each module's `NavGraphBuilder` extension function; only `MainActivity` (in `:app`) knows about both routes and wires the navigation.

---

### Task 1: Module scaffolding

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `feature/witness/api/build.gradle.kts`
- Create: `feature/witness/api/src/main/java/de/wegefrei/app/feature/witness/api/WitnessDetailsRoute.kt`
- Create: `feature/witness/impl/build.gradle.kts`

**Interfaces:**
- Produces: `@Serializable data object WitnessDetailsRoute` in `de.wegefrei.app.feature.witness.api`
- Produces: an empty, compilable `:feature:witness:impl` Android library module ready for Task 2+ to add source files to

No automated test for this task — build configuration only.

- [ ] **Step 1: Register the new modules**

In `settings.gradle.kts`, add after the existing `include(":feature:photocapture:impl")` line:

```kotlin
include(":feature:witness:api")
include(":feature:witness:impl")
```

- [ ] **Step 2: Add new version catalog entries**

In `gradle/libs.versions.toml`, under `[versions]`, add (near the other androidx entries):

```toml
androidxDatastore = "1.1.7"
```

Under `[libraries]`, add (near the other androidx entries):

```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "androidxDatastore" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
```

(`material-icons-core`'s version is managed by the Compose BOM platform already used everywhere in this project, so it needs no `version.ref`.)

- [ ] **Step 3: Create `feature/witness/api`**

Create `feature/witness/api/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
```

Create `feature/witness/api/src/main/java/de/wegefrei/app/feature/witness/api/WitnessDetailsRoute.kt`:

```kotlin
package de.wegefrei.app.feature.witness.api

import kotlinx.serialization.Serializable

@Serializable
data object WitnessDetailsRoute
```

- [ ] **Step 4: Create `feature/witness/impl`**

Create `feature/witness/impl/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.wegefrei.app.feature.witness.impl"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":feature:witness:api"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

There is no `AndroidManifest.xml` for this module — no permissions are needed, and modern AGP generates one implicitly from the Gradle `namespace`, matching what a permission-free library module needs.

- [ ] **Step 5: Verify the new modules compile**

Run: `./gradlew :feature:witness:api:compileKotlin :feature:witness:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (the `impl` module will compile with zero source files at this point — that's fine, it's just verifying the Gradle wiring).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml feature/witness
git commit -m "Scaffold feature:witness api and impl modules"
```

---

### Task 2: `WitnessDetailsRepository` and email validation

**Files:**
- Create: `feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsRepository.kt`
- Create: `feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/EmailValidation.kt`
- Test: `feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/EmailValidationTest.kt`

**Interfaces:**
- Produces: `interface WitnessDetailsRepository { val name: Flow<String>; val address: Flow<String>; val email: Flow<String>; suspend fun saveName(value: String); suspend fun saveAddress(value: String); suspend fun saveEmail(value: String) }`
- Produces: `internal class DataStoreWitnessDetailsRepository(private val context: Context) : WitnessDetailsRepository`
- Produces: `internal fun isValidEmail(value: String): Boolean`

No automated test for `DataStoreWitnessDetailsRepository` — real DataStore I/O, consistent with this codebase's other untested platform wrappers. `isValidEmail` IS tested (pure function).

- [ ] **Step 1: Write the failing tests for `isValidEmail`**

```kotlin
package de.wegefrei.app.feature.witness.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailValidationTest {

    @Test
    fun `isValidEmail accepts a plausible email address`() {
        assertTrue(isValidEmail("max@example.com"))
    }

    @Test
    fun `isValidEmail rejects a value without an at sign`() {
        assertFalse(isValidEmail("max.example.com"))
    }

    @Test
    fun `isValidEmail rejects a value without a domain`() {
        assertFalse(isValidEmail("max@example"))
    }

    @Test
    fun `isValidEmail rejects a blank value`() {
        assertFalse(isValidEmail(""))
    }
}
```

This is a plain JUnit test (no `@RunWith(RobolectricTestRunner::class)` needed) since `isValidEmail` only uses `Regex`/`String`, pure Kotlin/JVM stdlib with no Android-framework classes involved.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.witness.impl.EmailValidationTest"`
Expected: FAIL — `isValidEmail` is unresolved (function doesn't exist yet).

- [ ] **Step 3: Create `EmailValidation.kt`**

```kotlin
package de.wegefrei.app.feature.witness.impl

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

internal fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value)
```

- [ ] **Step 4: Create `WitnessDetailsRepository.kt`**

```kotlin
package de.wegefrei.app.feature.witness.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.witnessDetailsDataStore by preferencesDataStore(name = "witness_details")

private val NAME_KEY = stringPreferencesKey("name")
private val ADDRESS_KEY = stringPreferencesKey("address")
private val EMAIL_KEY = stringPreferencesKey("email")

interface WitnessDetailsRepository {
    val name: Flow<String>
    val address: Flow<String>
    val email: Flow<String>
    suspend fun saveName(value: String)
    suspend fun saveAddress(value: String)
    suspend fun saveEmail(value: String)
}

internal class DataStoreWitnessDetailsRepository(
    private val context: Context,
) : WitnessDetailsRepository {

    override val name: Flow<String> = context.witnessDetailsDataStore.data.map { it[NAME_KEY] ?: "" }
    override val address: Flow<String> = context.witnessDetailsDataStore.data.map { it[ADDRESS_KEY] ?: "" }
    override val email: Flow<String> = context.witnessDetailsDataStore.data.map { it[EMAIL_KEY] ?: "" }

    override suspend fun saveName(value: String) {
        context.witnessDetailsDataStore.edit { it[NAME_KEY] = value }
    }

    override suspend fun saveAddress(value: String) {
        context.witnessDetailsDataStore.edit { it[ADDRESS_KEY] = value }
    }

    override suspend fun saveEmail(value: String) {
        context.witnessDetailsDataStore.edit { it[EMAIL_KEY] = value }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.witness.impl.EmailValidationTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsRepository.kt \
        feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/EmailValidation.kt \
        feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/EmailValidationTest.kt
git commit -m "Add witness details repository and email format validation"
```

---

### Task 3: `WitnessDetailsViewModel`

**Files:**
- Create: `feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsViewModel.kt`
- Test: `feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsViewModelTest.kt`
- Test: `feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/FakeWitnessDetailsRepository.kt`

**Interfaces:**
- Consumes: `WitnessDetailsRepository` (Task 2)
- Produces: `internal class WitnessDetailsViewModel(private val repository: WitnessDetailsRepository) : ViewModel()` with `name: StateFlow<String>`, `address: StateFlow<String>`, `email: StateFlow<String>`, `onNameChanged(value: String)`, `onAddressChanged(value: String)`, `onEmailChanged(value: String)`

This is the one task in this plan requiring real TDD evidence (RED then GREEN) — the ViewModel's behavior (does an edit reach both the local state and the repository? does the initial value load from the repository?) is exactly the kind of thing that's easy to get subtly wrong with coroutines.

- [ ] **Step 1: Create the fake repository test double**

Create `FakeWitnessDetailsRepository.kt`:

```kotlin
package de.wegefrei.app.feature.witness.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeWitnessDetailsRepository(
    initialName: String = "",
    initialAddress: String = "",
    initialEmail: String = "",
) : WitnessDetailsRepository {

    private val nameFlow = MutableStateFlow(initialName)
    private val addressFlow = MutableStateFlow(initialAddress)
    private val emailFlow = MutableStateFlow(initialEmail)

    override val name: Flow<String> = nameFlow
    override val address: Flow<String> = addressFlow
    override val email: Flow<String> = emailFlow

    var savedName: String? = null
        private set
    var savedAddress: String? = null
        private set
    var savedEmail: String? = null
        private set

    override suspend fun saveName(value: String) {
        savedName = value
        nameFlow.value = value
    }

    override suspend fun saveAddress(value: String) {
        savedAddress = value
        addressFlow.value = value
    }

    override suspend fun saveEmail(value: String) {
        savedEmail = value
        emailFlow.value = value
    }
}
```

This is test-only infrastructure (lives in `src/test`), matching the plan's convention of testing ViewModels without touching real Android I/O.

- [ ] **Step 2: Write the failing tests**

Create `WitnessDetailsViewModelTest.kt`:

```kotlin
package de.wegefrei.app.feature.witness.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WitnessDetailsViewModelTest {

    private val repository = FakeWitnessDetailsRepository()
    private lateinit var viewModel: WitnessDetailsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = WitnessDetailsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onNameChanged updates the name state and persists it`() {
        viewModel.onNameChanged("Max Mustermann")

        assertEquals("Max Mustermann", viewModel.name.value)
        assertEquals("Max Mustermann", repository.savedName)
    }

    @Test
    fun `onAddressChanged updates the address state and persists it`() {
        viewModel.onAddressChanged("Musterstraße 1, 12345 Musterstadt")

        assertEquals("Musterstraße 1, 12345 Musterstadt", viewModel.address.value)
        assertEquals("Musterstraße 1, 12345 Musterstadt", repository.savedAddress)
    }

    @Test
    fun `onEmailChanged updates the email state and persists it`() {
        viewModel.onEmailChanged("max@example.com")

        assertEquals("max@example.com", viewModel.email.value)
        assertEquals("max@example.com", repository.savedEmail)
    }

    @Test
    fun `loads persisted values from the repository on init`() {
        val prefilled = FakeWitnessDetailsRepository(
            initialName = "Erika Musterfrau",
            initialAddress = "Beispielweg 2",
            initialEmail = "erika@example.com",
        )

        val loadedViewModel = WitnessDetailsViewModel(prefilled)

        assertEquals("Erika Musterfrau", loadedViewModel.name.value)
        assertEquals("Beispielweg 2", loadedViewModel.address.value)
        assertEquals("erika@example.com", loadedViewModel.email.value)
    }
}
```

The `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before` is required because `WitnessDetailsViewModel` uses `viewModelScope` (which defaults to `Dispatchers.Main.immediate`); `UnconfinedTestDispatcher` makes the coroutines launched in `init` and in each `onXChanged` run eagerly/synchronously, so the assertions right after calling a method don't need any manual advancing. `kotlinx-coroutines-test` (which provides `setMain`/`resetMain`/`UnconfinedTestDispatcher`) is already a `testImplementation` dependency of this module (added in Task 1).

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.witness.impl.WitnessDetailsViewModelTest"`
Expected: FAIL — `WitnessDetailsViewModel` is unresolved (class doesn't exist yet).

- [ ] **Step 4: Create `WitnessDetailsViewModel.kt`**

```kotlin
package de.wegefrei.app.feature.witness.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class WitnessDetailsViewModel(
    private val repository: WitnessDetailsRepository,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    init {
        viewModelScope.launch { repository.name.collect { _name.value = it } }
        viewModelScope.launch { repository.address.collect { _address.value = it } }
        viewModelScope.launch { repository.email.collect { _email.value = it } }
    }

    fun onNameChanged(value: String) {
        _name.value = value
        viewModelScope.launch { repository.saveName(value) }
    }

    fun onAddressChanged(value: String) {
        _address.value = value
        viewModelScope.launch { repository.saveAddress(value) }
    }

    fun onEmailChanged(value: String) {
        _email.value = value
        viewModelScope.launch { repository.saveEmail(value) }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.witness.impl.WitnessDetailsViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsViewModel.kt \
        feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsViewModelTest.kt \
        feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/FakeWitnessDetailsRepository.kt
git commit -m "Add WitnessDetailsViewModel with persistence-backed state"
```

---

### Task 4: `WitnessDetailsScreen` UI and navigation

**Files:**
- Create: `feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsScreen.kt`
- Create: `feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsNavigation.kt`

**Interfaces:**
- Consumes: `WitnessDetailsViewModel`, `DataStoreWitnessDetailsRepository` (Tasks 2-3), `WitnessDetailsRoute` (Task 1)
- Produces: `internal fun WitnessDetailsRoot(onBackRequested: () -> Unit)`, `internal fun WitnessDetailsScreen(...)`, `fun NavGraphBuilder.witnessDetailsScreen(onBackRequested: () -> Unit)`

No automated test for this task — Compose UI wiring, consistent with the rest of this codebase's screens.

- [ ] **Step 1: Create `WitnessDetailsScreen.kt`**

```kotlin
package de.wegefrei.app.feature.witness.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
internal fun WitnessDetailsRoot(
    onBackRequested: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: WitnessDetailsViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer {
                    WitnessDetailsViewModel(DataStoreWitnessDetailsRepository(context))
                }
            }
        },
    )

    val name by viewModel.name.collectAsState()
    val address by viewModel.address.collectAsState()
    val email by viewModel.email.collectAsState()

    WitnessDetailsScreen(
        name = name,
        onNameChanged = viewModel::onNameChanged,
        address = address,
        onAddressChanged = viewModel::onAddressChanged,
        email = email,
        onEmailChanged = viewModel::onEmailChanged,
        onBackRequested = onBackRequested,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WitnessDetailsScreen(
    name: String,
    onNameChanged: (String) -> Unit,
    address: String,
    onAddressChanged: (String) -> Unit,
    email: String,
    onEmailChanged: (String) -> Unit,
    onBackRequested: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Meine Angaben") },
                navigationIcon = {
                    IconButton(onClick = onBackRequested) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WitnessTextField(
                value = name,
                onValueChange = onNameChanged,
                label = "Name",
            )

            WitnessTextField(
                value = address,
                onValueChange = onAddressChanged,
                label = "Adresse",
            )

            WitnessTextField(
                value = email,
                onValueChange = onEmailChanged,
                label = "E-Mail",
                validate = { value ->
                    when {
                        value.isBlank() -> "Pflichtfeld"
                        !isValidEmail(value) -> "Ungültige E-Mail-Adresse"
                        else -> null
                    }
                },
            )
        }
    }
}

@Composable
private fun WitnessTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    validate: (String) -> String? = { if (it.isBlank()) "Pflichtfeld" else null },
) {
    var wasFocused by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    val errorMessage = if (touched) validate(value) else null

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "$label *") },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { message -> { Text(text = message) } },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    wasFocused = true
                } else if (wasFocused) {
                    touched = true
                }
            },
    )
}
```

- [ ] **Step 2: Create `WitnessDetailsNavigation.kt`**

```kotlin
package de.wegefrei.app.feature.witness.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.wegefrei.app.feature.witness.api.WitnessDetailsRoute

fun NavGraphBuilder.witnessDetailsScreen(onBackRequested: () -> Unit) {
    composable<WitnessDetailsRoute> {
        WitnessDetailsRoot(onBackRequested = onBackRequested)
    }
}
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew :feature:witness:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full module test suite**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions from Tasks 2-3).

- [ ] **Step 5: Commit**

```bash
git add feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsScreen.kt \
        feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsNavigation.kt
git commit -m "Add WitnessDetailsScreen UI and navigation entry point"
```

---

### Task 5: Add the menu to `PhotoCaptureScreen`

**Files:**
- Modify: `feature/photocapture/impl/build.gradle.kts`
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureNavigation.kt`

**Interfaces:**
- Produces: `PhotoCaptureRoot(onOpenWitnessDetailsRequested: () -> Unit)` (new required parameter), `PhotoCaptureScreen(..., onOpenWitnessDetailsRequested: () -> Unit)` (new required parameter), `fun NavGraphBuilder.photoCaptureScreen(onOpenWitnessDetailsRequested: () -> Unit)` (new required parameter)

`feature/photocapture` does NOT gain a dependency on `feature/witness` — the callback is a plain lambda, resolved to an actual navigation call only in `MainActivity` (Task 6).

No automated test for this task — Compose UI wiring.

- [ ] **Step 1: Add the material-icons-core dependency**

In `feature/photocapture/impl/build.gradle.kts`, in the `dependencies` block, add next to `implementation(libs.androidx.compose.material3)`:

```kotlin
    implementation(libs.androidx.compose.material.icons.core)
```

- [ ] **Step 2: Add the new imports**

At the top of `PhotoCaptureScreen.kt`, add:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
```

- [ ] **Step 3: Thread the new parameter through `PhotoCaptureRoot`**

Add `onOpenWitnessDetailsRequested: () -> Unit,` as a new parameter of `PhotoCaptureRoot`, right after `viewModel: PhotoCaptureViewModel = viewModel(),`:

```kotlin
internal fun PhotoCaptureRoot(
    viewModel: PhotoCaptureViewModel = viewModel(),
    onOpenWitnessDetailsRequested: () -> Unit,
) {
```

In the `PhotoCaptureScreen(...)` call inside `PhotoCaptureRoot`, add this parameter (anywhere among the existing ones):

```kotlin
            onOpenWitnessDetailsRequested = onOpenWitnessDetailsRequested,
```

- [ ] **Step 4: Add the parameter to the `PhotoCaptureScreen` composable signature and add the top app bar**

Add `onOpenWitnessDetailsRequested: () -> Unit,` to the `PhotoCaptureScreen` function signature, after the last existing parameter (`onUseCurrentLocationRequested: () -> Unit,`).

Add `@OptIn(ExperimentalMaterial3Api::class)` immediately above the `@Composable` annotation of `PhotoCaptureScreen` (it doesn't have one yet — only the file's `IncidentDateTimePicker` does).

Replace the existing `Scaffold { innerPadding ->` line with:

```kotlin
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Falschparker melden") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "Meine Angaben") },
                            onClick = {
                                showMenu = false
                                onOpenWitnessDetailsRequested()
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
```

- [ ] **Step 5: Update `PhotoCaptureNavigation.kt`**

Replace the file's contents with:

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.wegefrei.app.feature.photocapture.api.PhotoCaptureRoute

fun NavGraphBuilder.photoCaptureScreen(onOpenWitnessDetailsRequested: () -> Unit) {
    composable<PhotoCaptureRoute> {
        PhotoCaptureRoot(onOpenWitnessDetailsRequested = onOpenWitnessDetailsRequested)
    }
}
```

- [ ] **Step 6: Build the module**

Run: `./gradlew :feature:photocapture:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full module test suite**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions).

- [ ] **Step 8: Commit**

```bash
git add feature/photocapture/impl/build.gradle.kts \
        feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt \
        feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureNavigation.kt
git commit -m "Add Meine Angaben menu to the report screen"
```

---

### Task 6: Wire navigation in `MainActivity`

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/de/wegefrei/app/MainActivity.kt`

**Interfaces:**
- Consumes: `WitnessDetailsRoute` (Task 1), `witnessDetailsScreen(onBackRequested: () -> Unit)` (Task 4), `photoCaptureScreen(onOpenWitnessDetailsRequested: () -> Unit)` (Task 5)

No automated test for this task — app-module wiring, no existing tests in `:app` to extend.

- [ ] **Step 1: Add the new module dependencies**

In `app/build.gradle.kts`, in the `dependencies` block, add after `implementation(project(":feature:photocapture:impl"))`:

```kotlin
    implementation(project(":feature:witness:api"))
    implementation(project(":feature:witness:impl"))
```

- [ ] **Step 2: Wire the navigation graph**

Replace `app/src/main/java/de/wegefrei/app/MainActivity.kt`'s contents with:

```kotlin
package de.wegefrei.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import de.wegefrei.app.core.designsystem.WegefreiTheme
import de.wegefrei.app.feature.photocapture.api.PhotoCaptureRoute
import de.wegefrei.app.feature.photocapture.impl.photoCaptureScreen
import de.wegefrei.app.feature.witness.api.WitnessDetailsRoute
import de.wegefrei.app.feature.witness.impl.witnessDetailsScreen

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
    NavHost(navController = navController, startDestination = PhotoCaptureRoute) {
        photoCaptureScreen(
            onOpenWitnessDetailsRequested = { navController.navigate(WitnessDetailsRoute) },
        )
        witnessDetailsScreen(
            onBackRequested = { navController.navigateUp() },
        )
    }
}
```

- [ ] **Step 2: Build the whole project**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full project test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass across every module (no regressions).

- [ ] **Step 4: Manual verification**

Install and run the app (`./gradlew :app:installDebug`, or use the `run` skill if available):
- Confirm the report screen now shows a top app bar with a title and an overflow (⋮) icon.
- Tap the overflow icon → confirm a dropdown appears with "Meine Angaben".
- Tap it → confirm navigation to the "Meine Angaben" screen with its own top app bar and back arrow.
- Enter a name, address, and email → confirm no crash, and that clearing a field and tapping into/out of it shows the "Pflichtfeld"/"Ungültige E-Mail-Adresse" error as appropriate.
- Tap the back arrow → confirm return to the report screen.
- Kill and restart the app (or reopen "Meine Angaben") → confirm the previously entered values are still there.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/de/wegefrei/app/MainActivity.kt
git commit -m "Wire Meine Angaben navigation into the app's nav graph"
```
