# Color dropdown design

## Context

`PhotoCaptureScreen.kt` (`feature/photocapture/impl`) recently gained a
predefined-options dropdown for the "Marke" (brand) field
(`RequiredBrandDropdownField`, see
`docs/superpowers/specs/2026-07-27-brand-dropdown-design.md`): an editable
combobox that suggests options while typing but still accepts any free
text. "Farbe" (color) is currently a plain `RequiredTextField` and should
get the same treatment — most reports name one of a handful of common car
colors.

## Non-goals

- No change to the underlying data model. `colorText` stays a single
  `StateFlow<String>` in `PhotoCaptureViewModel` — this is a UI-only
  change, exactly like the brand field.
- No server-driven or configurable color list. Static, hardcoded array.
- No validation restricting the value to the predefined list. Free text is
  always accepted, exactly as today.

## Components

All changes land in `PhotoCaptureScreen.kt`. No ViewModel changes, no new
files, no new module.

### Refactor: generalize the brand dropdown

`RequiredBrandDropdownField` is brand-specific (hardcoded `germanCarBrands`
filter and hardcoded `"Marke *"` label). Adding a second, near-identical
composable for colors would duplicate ~65 lines of dropdown/menu-anchor/
touched-state wiring twice. Instead, generalize it into a single reusable
composable:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequiredOptionDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
)
```

Behavior is unchanged from today's `RequiredBrandDropdownField` — editable
`ExposedDropdownMenuBox`, filter-as-you-type (substring, case-insensitive),
trailing icon as the secondary anchor to reopen after selection, same
touched/error/"Pflichtfeld" handling — the only new parameters are `label`
and `options` in place of the hardcoded `"Marke *"` and `germanCarBrands`.

The existing `filterCarBrands(query: String): List<String>` top-level
function (used by `FilterCarBrandsTest`) becomes a generic
`filterOptions(query: String, options: List<String>): List<String>`, and
`filterCarBrands` is removed along with `germanCarBrands`'s direct
reference inside it — the brand call site now passes
`germanCarBrands` explicitly.

### Color list

A private constant list of the ~14 most common car colors in Germany (per
KBA color-share data), roughly descending by share, placed next to
`germanCarBrands`:

```kotlin
internal val germanCarColors = listOf(
    "Schwarz", "Weiß", "Silber", "Grau", "Blau", "Rot", "Braun", "Grün",
    "Beige", "Gelb", "Orange", "Violett", "Gold", "Bronze",
)
```

### Call sites

```kotlin
RequiredOptionDropdownField(
    value = makeText,
    onValueChange = onMakeTextChanged,
    label = "Marke",
    options = germanCarBrands,
)

RequiredOptionDropdownField(
    value = colorText,
    onValueChange = onColorTextChanged,
    label = "Farbe",
    options = germanCarColors,
)
```

replacing the current `RequiredBrandDropdownField(...)` and
`RequiredTextField(value = colorText, ...)` calls.

## Error handling

None needed — same as today, plain local string values with no I/O.

## Testing

Extend the existing `FilterCarBrandsTest.kt` (rename to reflect the
generalized function, e.g. `FilterOptionsTest.kt`) to test the generic
`filterOptions` function against both an arbitrary options list and,
specifically, `germanCarColors` for a couple of cases (blank query, a
substring match). No Compose UI tests, consistent with the module's
existing convention (also followed by the brand-dropdown work).
