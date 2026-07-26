# Vehicle fields design

## Context

The report screen (`feature/photocapture/impl`) already lets the user attach
photos and a "Tatort" (address). The next piece of report data is basic
vehicle identification: license plate, make, and color. These three fields
are required — the report doesn't make sense without them — so this is also
the first point where the screen needs any notion of "can the user proceed."

## Non-goals

- No actual next screen or report submission. The "Weiter" button becomes
  enabled once all three fields are filled, but tapping it is a no-op. What
  happens after "Weiter" is a separate future feature.
- No format validation (e.g. German license-plate pattern). "Required"
  means non-blank, nothing more.
- No fields beyond the three named (Kennzeichen, Marke, Farbe). Verstoß and
  Tatzeit, mentioned earlier, are explicitly out of scope for this pass.

## Components

All changes land in the existing two files in `feature/photocapture/impl`:
`PhotoCaptureViewModel.kt` and `PhotoCaptureScreen.kt`. No new files, no new
module.

### `PhotoCaptureViewModel`

Three new independent string fields, following the existing `addressText`
pattern but simpler — there's no auto-fill source for these, so no
"don't clobber" logic is needed:

```kotlin
private val _kennzeichenText = MutableStateFlow("")
val kennzeichenText: StateFlow<String> = _kennzeichenText.asStateFlow()

private val _markeText = MutableStateFlow("")
val markeText: StateFlow<String> = _markeText.asStateFlow()

private val _farbeText = MutableStateFlow("")
val farbeText: StateFlow<String> = _farbeText.asStateFlow()

fun onKennzeichenTextChanged(text: String) { _kennzeichenText.value = text }
fun onMarkeTextChanged(text: String) { _markeText.value = text }
fun onFarbeTextChanged(text: String) { _farbeText.value = text }
```

### UI (`PhotoCaptureScreen`)

- A new "Fahrzeug" section, placed **above** the existing "Tatort" section:
  header `Text` + three `OutlinedTextField`s labeled "Kennzeichen *",
  "Marke *", "Farbe *", each `fillMaxWidth()`, matching the existing German
  UI copy style.
- Each field tracks its own "touched" state locally in Compose
  (`remember { mutableStateOf(false) }`, set via
  `Modifier.onFocusChanged { if (!it.isFocused) touched = true }`). Once
  touched and still blank, the field shows `isError = true` and a
  `supportingText` of "Pflichtfeld". This is UI-only presentation state —
  it does not go in the ViewModel, matching how `isLookingUpAddress` is
  handled today.
- A "Weiter" `Button` at the bottom of the screen (after the "Tatort"
  section), `enabled` only when all three fields
  (`kennzeichenText`, `markeText`, `farbeText`) are non-blank. `onClick` is
  an empty lambda — intentionally a no-op, since there is nothing to
  navigate to yet.

## Error handling

None needed — these are plain local string fields with no I/O, no
network, no permissions.

## Testing

- Extend `PhotoCaptureViewModelTest` (Robolectric) with three small tests
  confirming each new field's value round-trips through its setter.
- The touched/error-display logic and the "Weiter" button's enabled state
  are Compose UI wiring — left untested, consistent with the rest of this
  screen (no Compose UI tests exist in this module).
