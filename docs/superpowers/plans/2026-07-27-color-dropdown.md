# Color Dropdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give "Farbe" the same predefined-options-plus-free-text dropdown that "Marke" already has, by generalizing the existing brand-only dropdown composable into a reusable one driven by a `label` and `options` list.

**Architecture:** Single-file UI change in `PhotoCaptureScreen.kt` (module `feature/photocapture/impl`). `RequiredBrandDropdownField` is renamed/generalized to `RequiredOptionDropdownField(value, onValueChange, label, options, modifier)`; the module-level `filterCarBrands` helper becomes a generic `filterOptions(query, options)`. Both the "Marke" and "Farbe" fields become call sites of the same composable, passing `germanCarBrands` / a new `germanCarColors` list respectively. No ViewModel or state-shape changes.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 (`ExposedDropdownMenuBox`, `ExposedDropdownMenu`, `ExposedDropdownMenuDefaults`, `MenuAnchorType`) — same APIs already in use, already verified to compile in this module.

## Global Constraints

- No ViewModel changes — `colorText` / `onColorTextChanged` and `makeText` / `onMakeTextChanged` signatures in `PhotoCaptureViewModel.kt` stay exactly as-is.
- No new state/data model, no new module, no new file for the UI change (the test file gets renamed, see Task 1).
- Free text is always accepted for both fields; the predefined lists never restrict the value.
- Labels stay `"Marke *"` and `"Farbe *"` respectively (the composable takes `label: String` and appends `" *"` itself, matching `RequiredTextField`'s convention).
- No Compose UI tests are added — this module has none, consistent with prior work in this file.
- Color list (exact, in this order):
  `"Schwarz", "Weiß", "Silber", "Grau", "Blau", "Rot", "Braun", "Grün", "Beige", "Gelb", "Orange", "Violett", "Gold", "Bronze"`
- Brand list is unchanged from its current value in the file — do not re-type it, just reference `germanCarBrands` as it already exists.

---

### Task 1: Generalize the dropdown composable and wire up both fields

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`
- Rename + modify: `feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/FilterCarBrandsTest.kt` → `feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/FilterOptionsTest.kt`

**Interfaces:**
- Consumes: existing `makeText`/`onMakeTextChanged` and `colorText`/`onColorTextChanged` parameters already threaded through `PhotoCaptureScreen` and `PhotoCaptureRoot` — unchanged, no new parameters anywhere in the composable signatures that cross file boundaries.
- Produces: `private fun RequiredOptionDropdownField(value: String, onValueChange: (String) -> Unit, label: String, options: List<String>, modifier: Modifier = Modifier)` and `internal fun filterOptions(query: String, options: List<String>): List<String>`, both used only within this file (and the latter from the test file).

This is a single self-contained task — one file's worth of composable refactor plus a same-shape test rename, verified by compiling and running the module's unit tests.

- [ ] **Step 1: Add the `germanCarColors` constant**

In `PhotoCaptureScreen.kt`, right after the existing `germanCarBrands` list (it currently ends right before `internal fun filterCarBrands`), add:

```kotlin
internal val germanCarColors = listOf(
    "Schwarz", "Weiß", "Silber", "Grau", "Blau", "Rot", "Braun", "Grün",
    "Beige", "Gelb", "Orange", "Violett", "Gold", "Bronze",
)
```

- [ ] **Step 2: Generalize `filterCarBrands` into `filterOptions`**

Replace the existing:

```kotlin
internal fun filterCarBrands(query: String): List<String> =
    germanCarBrands.filter { it.contains(query, ignoreCase = true) }
```

with:

```kotlin
internal fun filterOptions(query: String, options: List<String>): List<String> =
    options.filter { it.contains(query, ignoreCase = true) }
```

- [ ] **Step 3: Generalize `RequiredBrandDropdownField` into `RequiredOptionDropdownField`**

Replace the existing `RequiredBrandDropdownField` composable in full with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequiredOptionDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
) {
    var wasFocused by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val isError = touched && value.isBlank()
    val filteredOptions = remember(value, options) {
        filterOptions(value, options)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredOptions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(text = "$label *") },
            isError = isError,
            supportingText = if (isError) {
                { Text(text = "Pflichtfeld") }
            } else {
                null
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = Modifier.menuAnchor(MenuAnchorType.SecondaryEditable),
                )
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        wasFocused = true
                        expanded = true
                    } else if (wasFocused) {
                        touched = true
                        expanded = false
                    }
                },
        )

        ExposedDropdownMenu(
            expanded = expanded && filteredOptions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filteredOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 4: Update the "Marke" call site**

Replace the existing:

```kotlin
            RequiredBrandDropdownField(
                value = makeText,
                onValueChange = onMakeTextChanged,
            )
```

with:

```kotlin
            RequiredOptionDropdownField(
                value = makeText,
                onValueChange = onMakeTextChanged,
                label = "Marke",
                options = germanCarBrands,
            )
```

- [ ] **Step 5: Update the "Farbe" call site**

Replace the existing:

```kotlin
            RequiredTextField(
                value = colorText,
                onValueChange = onColorTextChanged,
                label = "Farbe",
            )
```

with:

```kotlin
            RequiredOptionDropdownField(
                value = colorText,
                onValueChange = onColorTextChanged,
                label = "Farbe",
                options = germanCarColors,
            )
```

- [ ] **Step 6: Rename and generalize the test file**

Rename `feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/FilterCarBrandsTest.kt` to `FilterOptionsTest.kt` (same directory), and replace its contents with:

```kotlin
package de.wegefrei.app.feature.photocapture.impl

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterOptionsTest {

    @Test
    fun `blank query returns all brands`() {
        val result = filterOptions("", germanCarBrands)

        assertEquals(25, result.size)
    }

    @Test
    fun `substring match is case-insensitive and returns multiple matches`() {
        val result = filterOptions("Vol", germanCarBrands)

        assertEquals(listOf("Volkswagen", "Volvo"), result)
    }

    @Test
    fun `matching is case-insensitive`() {
        val result = filterOptions("BMW", germanCarBrands)

        assertEquals(listOf("BMW"), result)
    }

    @Test
    fun `matches substrings not just prefixes`() {
        val result = filterOptions("at", germanCarBrands)

        assertEquals(listOf("Seat", "Fiat"), result)
    }

    @Test
    fun `query matching nothing returns empty list`() {
        val result = filterOptions("zzz", germanCarBrands)

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `blank query returns all colors`() {
        val result = filterOptions("", germanCarColors)

        assertEquals(14, result.size)
    }

    @Test
    fun `color substring match is case-insensitive`() {
        val result = filterOptions("blau", germanCarColors)

        assertEquals(listOf("Blau"), result)
    }
}
```

(`germanCarBrands` and `germanCarColors` must be `internal val`s, not `private val`s, at file scope in `PhotoCaptureScreen.kt`: Kotlin top-level `private` is file-private, not package-private, so a `private val` would not be visible to this test file even though it's in the same package. `internal` makes them visible module-wide, including from this test.)

- [ ] **Step 7: Compile the module**

Run: `./gradlew :feature:photocapture:impl:compileDebugSources`
Expected: BUILD SUCCESSFUL, no unresolved references (in particular, confirm no leftover reference to the old `RequiredBrandDropdownField` or `filterCarBrands` names anywhere in the file).

- [ ] **Step 8: Run the module's unit tests**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — the renamed `FilterOptionsTest` (7 tests) passes, `PhotoCaptureViewModelTest` passes unchanged.

- [ ] **Step 9: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt
git add feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/FilterOptionsTest.kt
git rm feature/photocapture/impl/src/test/java/de/wegefrei/app/feature/photocapture/impl/FilterCarBrandsTest.kt
git commit -m "Add Farbe color dropdown; generalize brand dropdown to RequiredOptionDropdownField"
```

(If the rename was done via `git mv` or the IDE, `git add`/`git rm` above are already reflected — just confirm `git status` shows a clean rename before committing, and adjust the add/rm commands only if needed.)

## Self-Review Notes

- Spec coverage: color list ✓ (Step 1), generic `filterOptions` ✓ (Step 2), generalized composable with `label`/`options` params ✓ (Step 3), both call sites updated ✓ (Steps 4-5), test renamed and extended to cover colors too ✓ (Step 6), no ViewModel changes ✓, no Compose UI tests added ✓.
- Type consistency: `RequiredOptionDropdownField(value: String, onValueChange: (String) -> Unit, label: String, options: List<String>, modifier: Modifier = Modifier)` matches both call sites in Steps 4-5 exactly. `filterOptions(query: String, options: List<String>): List<String>` matches its use in Step 3 and in the Step 6 test file.
