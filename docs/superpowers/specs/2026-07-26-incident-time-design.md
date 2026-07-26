# Incident time (Tatzeitpunkt) design

## Context

The report screen already collects photos, an address ("Tatort"), and
vehicle fields ("Fahrzeug"). The next field is "Tatzeitpunkt" — the date
and time of the offense. Rather than making the user type this manually
every time, it should default to the earliest capture time found across
the attached photos' EXIF data, since that's usually the actual moment of
the offense. The user can always override it manually with a date/time
picker; once they do, their choice sticks even as photos are added or
removed.

## Non-goals

- No timezone handling beyond whatever the device's local time already
  gives us — EXIF timestamps and `LocalDateTime.now()` are both treated as
  local, naive date-times, with no explicit zone conversion.
- No format validation beyond what the picker itself enforces (a
  `DatePicker`/`TimePicker` can't produce an invalid date/time).
- Seconds are always zero — the field only carries minute precision, per
  the "day and minute only" requirement.

## Components

### `PhotoTimestampExtractor`

New file, alongside the existing `PhotoLocationExtractor.kt`, in
`feature/photocapture/impl`:

```kotlin
interface PhotoTimestampExtractor {
    suspend fun extractTimestamp(uri: Uri): LocalDateTime?
}
```

`ExifPhotoTimestampExtractor` implementation:
- Opens the photo via `ContentResolver.openInputStream(uri)` — no
  `MediaStore.setRequireOriginal()` needed here, since Android's
  location-metadata redaction (the reason `PhotoLocationExtractor` needs
  that call and `ACCESS_MEDIA_LOCATION`) does **not** apply to non-location
  EXIF tags like date/time; they're readable without any extra permission.
- Reads `ExifInterface.TAG_DATETIME_ORIGINAL` (falling back to
  `TAG_DATETIME` if that's absent) via `androidx.exifinterface.media.ExifInterface`
  (already a dependency from the address-selection feature).
- Parses the tag's `"yyyy:MM:dd HH:mm:ss"` format into a `LocalDateTime`
  via `DateTimeFormatter.ofPattern(...)`, truncated to minute precision
  (`.withSecond(0).withNano(0)`).
- Returns `null` on missing tag, parse failure, or any I/O error — silent,
  best-effort, matching every other lookup on this screen.

## ViewModel changes (`PhotoCaptureViewModel`)

```kotlin
private val _incidentDateTime = MutableStateFlow(LocalDateTime.now().withSecond(0).withNano(0))
val incidentDateTime: StateFlow<LocalDateTime> = _incidentDateTime.asStateFlow()

private var hasUserEditedIncidentDateTime = false

fun onIncidentDateTimeChanged(dateTime: LocalDateTime) {
    hasUserEditedIncidentDateTime = true
    _incidentDateTime.value = dateTime.withSecond(0).withNano(0)
}

fun onPhotoTimestampsExtracted(timestamps: List<LocalDateTime>) {
    if (hasUserEditedIncidentDateTime) return
    _incidentDateTime.value = (timestamps.minOrNull() ?: LocalDateTime.now())
        .withSecond(0).withNano(0)
}
```

Rules:
- Initial value, before any photo or manual edit, is "now" (truncated to
  the minute) — satisfies "if no image is set yet, set the current day and
  time."
- `onPhotoTimestampsExtracted` is called with the full list of
  successfully-extracted timestamps for the *current* photo list every
  time that list changes (add **or** remove — see UI section). It always
  recomputes the minimum from scratch; it does not merge with the previous
  value. If the list is empty (no photos, or none had a readable
  timestamp), it falls back to "now" again, so removing all photos returns
  the field to a fresh current time — matching "if no image is set yet, set
  the current day and time" as a live rule, not just an initial default.
- Once `onIncidentDateTimeChanged` has been called once (a real manual
  edit), `onPhotoTimestampsExtracted` becomes a permanent no-op — this is
  the "internal flag" the request asked for, mirroring the existing
  `hasUserEditedAddress` pattern.

## UI changes (`PhotoCaptureRoot` / `PhotoCaptureScreen`)

- `PhotoCaptureRoot` gets a `LaunchedEffect(photoUris)` (keyed on the whole
  list, so it re-fires on any add *or* remove — recomputing the minimum
  whenever the earliest-timestamped photo could have changed, not only on
  add): for each `Uri` in the current list, calls
  `PhotoTimestampExtractor.extractTimestamp`, collects the non-null
  results, and calls `viewModel.onPhotoTimestampsExtracted(results)`.
- `PhotoCaptureScreen` gains, in a new "Tatzeitpunkt" section (placed after
  the existing "Fahrzeug" section and before "Tatort" — vehicle
  identification and timing both come from the photos, address is a
  separate lookup):
  - A `Text` header ("Tatzeitpunkt").
  - A `Button` (matching this screen's existing button-heavy style, e.g.
    "Aktuellen Standort verwenden") showing the current value formatted as
    `"dd.MM.yyyy HH:mm"` via `DateTimeFormatter`. Tapping it opens a date
    picker.
  - Tapping the button shows a Material3 `DatePickerDialog` wrapping
    `DatePicker` (`rememberDatePickerState`). Confirming it stores the
    picked date and immediately opens a second dialog — an `AlertDialog`
    wrapping `TimePicker` (`rememberTimePickerState`) — for the time.
    Confirming that combines the picked date and time into one
    `LocalDateTime` and calls `viewModel.onIncidentDateTimeChanged(...)`.
    Dismissing either dialog cancels the whole edit with no change.

## Error handling

Every extraction failure (missing tag, parse error, I/O error) resolves to
`null` and is skipped when computing the minimum — consistent with the
rest of this screen's silent-failure lookups.

## Testing

- Extend `PhotoCaptureViewModelTest` (Robolectric) with:
  - `onIncidentDateTimeChanged` sets the value and truncates seconds/nanos
    to zero.
  - `onPhotoTimestampsExtracted` sets the value to the minimum of the given
    timestamps when nothing has been manually edited yet.
  - `onPhotoTimestampsExtracted` is a no-op after a manual edit.
  - `onPhotoTimestampsExtracted` with an empty list falls back to "now"
    (asserted with a tolerance window around the test's execution time,
    e.g. within a few seconds, to avoid flakiness) when nothing has been
    manually edited yet.
- `PhotoTimestampExtractor`/`ExifPhotoTimestampExtractor` is a thin
  Android/EXIF wrapper and is left untested, consistent with
  `PhotoLocationExtractor` and the rest of this screen's platform wrappers.
- The date/time picker UI wiring is left untested, consistent with the
  rest of this screen (no Compose UI tests exist in this module).
