# Witness details (Zeuge) design

## Context

Every report is filed by a witness (the "Zeuge" — the person making the
complaint), and their name, address, and email are the same across every
report they file. Rather than re-typing this on each report, it should be
entered once, persisted on the device, and reused automatically going
forward. This is the app's first piece of data that outlives a single
report — everything built so far (photos, address, vehicle, incident time)
lives only in a `ViewModel` and is gone on process death. It's also the
app's first second screen and first navigation menu.

## Non-goals

- No wiring into the report screen's data or the (currently no-op) "Weiter"
  flow. This feature is standalone: a place to store and edit the witness's
  own details. How a future report-submission step reads them is a
  separate concern for whenever that flow is built.
- No account system, no cloud sync, no multiple saved profiles — one local
  set of values for "the person using this device."
- No gating action tied to the required fields — there's no "Weiter"/save
  button on this screen (edits auto-save), so "mandatory" here means
  visual required-field validation only (matching the existing `*` /
  "Pflichtfeld" pattern from the vehicle fields), not blocking navigation
  or disabling anything.

## Architecture decision: new feature module

This is a new, independent screen and concern (a settings/profile screen,
not a report field), and the codebase already has an established
convention for that: `feature/<name>/api` (just the route) +
`feature/<name>/impl` (the screen, ViewModel, and now also persistence).
This gets `feature/witness/api` and `feature/witness/impl`, mirroring
`feature/photocapture`.

This also introduces two things the app has never had before, both scoped
entirely to this new module:
- **Real persistence**: `androidx.datastore:datastore-preferences`, the
  standard choice for a handful of flat string values — far lighter than
  introducing Room for three fields.
- **A `ViewModel` factory**: every existing `ViewModel` in this app is
  constructed with the zero-arg default (`viewModel()`), which works
  because none of them need anything at construction time beyond what
  `remember`-scoped collaborators already provide per-call. This
  `ViewModel` needs a `Context`-backed repository at construction, so it
  needs `viewModel(factory = ...)` via `viewModelFactory { initializer { ... } }`
  — standard Android/Compose, no new library.

## Components

### `feature/witness/api`

Pure-Kotlin module (mirrors `feature/photocapture/api`):

```kotlin
@Serializable
data object WitnessDetailsRoute
```

### `feature/witness/impl`

**`WitnessDetailsRepository`** — persistence abstraction:

```kotlin
interface WitnessDetailsRepository {
    val name: Flow<String>
    val address: Flow<String>
    val email: Flow<String>
    suspend fun saveName(value: String)
    suspend fun saveAddress(value: String)
    suspend fun saveEmail(value: String)
}
```

`DataStoreWitnessDetailsRepository(context: Context)` implementation backed
by a `Context.witnessDetailsDataStore by preferencesDataStore(name = "witness_details")`
extension property, one `stringPreferencesKey` per field, defaulting to
`""` when absent.

**`WitnessDetailsViewModel`**:

```kotlin
class WitnessDetailsViewModel(
    private val repository: WitnessDetailsRepository,
) : ViewModel() {
    val name: StateFlow<String> = repository.name.stateIn(...)
    val address: StateFlow<String> = repository.address.stateIn(...)
    val email: StateFlow<String> = repository.email.stateIn(...)

    fun onNameChanged(value: String) { viewModelScope.launch { repository.saveName(value) } }
    fun onAddressChanged(value: String) { viewModelScope.launch { repository.saveAddress(value) } }
    fun onEmailChanged(value: String) { viewModelScope.launch { repository.saveEmail(value) } }
}
```

Every edit writes straight through to the repository (auto-save, no
explicit save action) — DataStore's `edit {}` is itself debounced/batched
at the OS level, so no additional debouncing is needed here.

**`WitnessDetailsScreen`** — three required fields (Name, Adresse,
E-Mail), all using the same touched-then-invalid validation shape as the
existing `RequiredTextField` from the vehicle-fields feature
(`wasFocused`/`touched` flags, fixed there to not fire on initial
composition) — reimplemented locally in this module as a single
`WitnessTextField` composable, since this module has no shared UI kit with
`feature/photocapture` and extracting one into `core` isn't justified for
three fields:

```kotlin
@Composable
private fun WitnessTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    validate: (String) -> String? = { if (it.isBlank()) "Pflichtfeld" else null },
)
```

`validate` returns an error message to show (once touched) or `null` if
valid. Name and Adresse use the default (required, blank check only). The
email field passes a custom validator combining required-ness and format:

```kotlin
validate = { value ->
    when {
        value.isBlank() -> "Pflichtfeld"
        !isValidEmail(value) -> "Ungültige E-Mail-Adresse"
        else -> null
    }
}
```

Email format check: a simple regex,
`^[^@\s]+@[^@\s]+\.[^@\s]+$` — not RFC-perfect, just enough to catch
"forgot the @" or "forgot the domain," matching the "basic format check"
decision, exposed as `internal fun isValidEmail(value: String): Boolean`
so it's independently testable.

**`WitnessDetailsNavigation.kt`** — mirrors `PhotoCaptureNavigation.kt`:

```kotlin
fun NavGraphBuilder.witnessDetailsScreen() {
    composable<WitnessDetailsRoute> {
        WitnessDetailsRoot()
    }
}
```

### Menu wiring (`feature/photocapture/impl`)

`PhotoCaptureScreen`'s `Scaffold` gains a `topBar`: a `TopAppBar` with a
title and a trailing overflow icon button (`Icons.Default.MoreVert`) that
opens a `DropdownMenu` with one item, "Meine Angaben", via
`onOpenWitnessDetailsRequested: () -> Unit` threaded down through
`PhotoCaptureRoot` from a new parameter on `PhotoCaptureRoot` itself (not
resolved to a `NavController` inside `feature/photocapture` — that module
must not depend on `feature/witness` or on navigation specifics, to keep
the existing module boundary clean). The composable is wired end-to-end
in `MainActivity.kt`'s `WegefreiNavHost`:

```kotlin
NavHost(navController = navController, startDestination = PhotoCaptureRoute) {
    photoCaptureScreen(
        onOpenWitnessDetailsRequested = { navController.navigate(WitnessDetailsRoute) },
    )
    witnessDetailsScreen()
}
```

`photoCaptureScreen()`'s signature gains the matching
`onOpenWitnessDetailsRequested: () -> Unit` parameter, threaded straight
through to `PhotoCaptureRoot`. `app`'s `build.gradle.kts` gains a
dependency on `feature/witness/api` and `feature/witness/impl` (same shape
as its existing `feature/photocapture` dependencies).

`WitnessDetailsScreen` itself also gets a minimal `TopAppBar` with a back
button (`navigateUp()`), since it's the app's first non-start destination.

## Error handling

DataStore reads/writes don't realistically fail on-device for this data
shape; no special error handling beyond what DataStore itself provides
(each field flow just emits `""` if the file doesn't exist yet, which is
DataStore's built-in behavior for a fresh install).

## Testing

- `WitnessDetailsViewModel`: Robolectric tests using a fake in-memory
  `WitnessDetailsRepository` (three mutable `MutableStateFlow`-backed
  properties, matching the existing pattern in this codebase of testing
  ViewModels without touching real Android I/O) verifying each
  `onXChanged` call reaches the repository and the corresponding
  `StateFlow` reflects it.
- A pure unit test for the email-format regex/validation function,
  extracted as a standalone `internal fun isValidEmail(value: String): Boolean`
  so it's testable without Compose or Robolectric.
- `DataStoreWitnessDetailsRepository`, `WitnessDetailsScreen`,
  `WitnessTextField`'s touched/error display, and the menu/navigation
  wiring are left untested — real DataStore I/O and Compose UI wiring,
  consistent with this codebase's existing pattern of leaving thin
  platform wrappers and Compose UI untested.
