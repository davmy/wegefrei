# Brand dropdown design

## Context

The "Marke" field in the vehicle section of `PhotoCaptureScreen`
(`feature/photocapture/impl`) is currently a plain required text field. Most
reports will name one of a handful of common car brands, so typing the full
name every time is unnecessary friction. This pass adds predefined brand
options while keeping the ability to enter any brand not on the list.

## Non-goals

- No change to the underlying data model. `makeText` stays a single
  `StateFlow<String>` in `PhotoCaptureViewModel` — this is a UI-only change.
- No server-driven or configurable brand list. The list is a static,
  hardcoded array.
- No validation restricting the value to the predefined list. Free text is
  always accepted, exactly as today.

## Components

All changes land in `PhotoCaptureScreen.kt` in `feature/photocapture/impl`.
No ViewModel changes, no new files, no new module.

### Brand list

A private constant list of the ~25 brands most commonly registered in
Germany (per KBA new-registration figures), in roughly descending market
share:

```kotlin
private val germanCarBrands = listOf(
    "Volkswagen", "Mercedes-Benz", "BMW", "Audi", "Opel", "Škoda", "Ford",
    "Seat", "Renault", "Hyundai", "Kia", "Toyota", "Peugeot", "Fiat",
    "Volvo", "Mini", "Citroën", "Dacia", "Nissan", "Mazda", "Porsche",
    "Smart", "Honda", "Suzuki", "Tesla",
)
```

### UI: `RequiredBrandDropdownField`

Replaces the current `RequiredTextField(value = makeText, ...)` call site
with a new private composable, `RequiredBrandDropdownField`, built on
Material3's `ExposedDropdownMenuBox`:

- A single editable `OutlinedTextField` inside the box (not read-only) —
  the user can type at any time, exactly like today's field.
- Local Compose state `expanded: Boolean` controls dropdown visibility;
  opens whenever the field is focused, closes on dismiss/selection.
- The dropdown's items are `germanCarBrands.filter { it.contains(value,
  ignoreCase = true) }`, recomputed on every keystroke. If `value` is
  blank, this yields the full list (25 items); if nothing matches, the
  dropdown renders with no items (or is not shown) and the user simply
  keeps typing their own brand — no separate "other" affordance needed.
- Tapping a `DropdownMenuItem` calls the same `onValueChange` callback with
  the selected brand text and closes the dropdown.
- Required-field/touched/error behavior (Pflichtfeld error on blur when
  blank) is preserved unchanged, reusing the same `wasFocused`/`touched`
  local-state pattern from `RequiredTextField`.
- Label stays `"Marke *"`, matching the existing field.

## Error handling

None needed — same as the existing field, this is a plain local string
value with no I/O.

## Testing

No Compose UI tests exist in this module today (per the earlier vehicle
fields design), so this stays consistent: the dropdown filtering,
selection, and touched/error display are UI wiring and are left
untested. No ViewModel test changes are needed since `makeText` /
`onMakeTextChanged` are unchanged.
