# Brand Dropdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain "Marke" text field on the vehicle-report screen with an editable dropdown offering the most common German car brands, while still accepting any free-text brand.

**Architecture:** Single-file UI change in `PhotoCaptureScreen.kt` (module `feature/photocapture/impl`). A new private `RequiredBrandDropdownField` composable wraps Material3's `ExposedDropdownMenuBox` around an editable `OutlinedTextField`, filtering a static hardcoded brand list as the user types. No ViewModel or state-shape changes — `makeText: StateFlow<String>` is untouched.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 (`ExposedDropdownMenuBox`, `ExposedDropdownMenuDefaults`, `DropdownMenuItem`).

## Global Constraints

- No ViewModel changes — `makeText` / `onMakeTextChanged` signatures in `PhotoCaptureViewModel.kt` stay exactly as-is (per spec: UI-only change).
- No new state/data model, no new module, no new file — everything lands in `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`.
- Free text is always accepted; the predefined list never restricts the value.
- Label text stays `"Marke *"`, matching the existing field's copy convention (German UI strings, `"Pflichtfeld"` for the required-field error).
- No Compose UI tests are added — this module has none today, and the spec keeps that convention (filtering/selection/touched logic is left untested, consistent with `RequiredTextField`).
- Brand list (exact, in this order):
  `"Volkswagen", "Mercedes-Benz", "BMW", "Audi", "Opel", "Škoda", "Ford", "Seat", "Renault", "Hyundai", "Kia", "Toyota", "Peugeot", "Fiat", "Volvo", "Mini", "Citroën", "Dacia", "Nissan", "Mazda", "Porsche", "Smart", "Honda", "Suzuki", "Tesla"`

---

### Task 1: Add `RequiredBrandDropdownField` and wire it into the vehicle section

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`

**Interfaces:**
- Consumes: existing `makeText: String` and `onMakeTextChanged: (String) -> Unit` parameters already threaded through `PhotoCaptureScreen` (lines 214-215) and `PhotoCaptureRoot` (line 89) — unchanged, no new parameters anywhere.
- Produces: a new private composable `RequiredBrandDropdownField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier)` used only within this file, at the call site that previously called `RequiredTextField(value = makeText, onValueChange = onMakeTextChanged, label = "Marke")`.

This is a single self-contained UI task — there is no separate ViewModel or wiring task, since the state shape doesn't change. The whole feature is one edit to one file, verified by compiling the module and manually confirming the screen still renders/behaves (no automated UI test exists for this module, per Global Constraints).

- [ ] **Step 1: Add the new Material3 imports needed for the dropdown**

Add these three imports alongside the existing `androidx.compose.material3.*` import block (after the `DropdownMenuItem` import on line 32, before `ExperimentalMaterial3Api` on line 33 — keep the block alphabetically sorted like the rest of the file):

```kotlin
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
```

(`DropdownMenuItem` and `ExperimentalMaterial3Api` are already imported; the file is already annotated `@OptIn(ExperimentalMaterial3Api::class)` at the `PhotoCaptureScreen` composable, but `ExposedDropdownMenuBox` needs that opt-in wherever it's used, so the new composable itself must also carry `@OptIn(ExperimentalMaterial3Api::class)`.)

- [ ] **Step 2: Add the brand list constant**

Add this near the top of the file, right after the existing `photoThumbnailKey` function (after line 77, before the blank line and `@Composable internal fun PhotoCaptureRoot`):

```kotlin
private val germanCarBrands = listOf(
    "Volkswagen", "Mercedes-Benz", "BMW", "Audi", "Opel", "Škoda", "Ford",
    "Seat", "Renault", "Hyundai", "Kia", "Toyota", "Peugeot", "Fiat",
    "Volvo", "Mini", "Citroën", "Dacia", "Nissan", "Mazda", "Porsche",
    "Smart", "Honda", "Suzuki", "Tesla",
)
```

- [ ] **Step 3: Write the `RequiredBrandDropdownField` composable**

Add this new composable right after the existing `RequiredTextField` composable (after the closing `}` on line 521, before `@Composable private fun PhotoThumbnail` on line 523):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequiredBrandDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wasFocused by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val isError = touched && value.isBlank()
    val filteredBrands = remember(value) {
        germanCarBrands.filter { it.contains(value, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredBrands.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(text = "Marke *") },
            isError = isError,
            supportingText = if (isError) {
                { Text(text = "Pflichtfeld") }
            } else {
                null
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuDefaults.PrimaryEditable)
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
            expanded = expanded && filteredBrands.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filteredBrands.forEach { brand ->
                DropdownMenuItem(
                    text = { Text(text = brand) },
                    onClick = {
                        onValueChange(brand)
                        expanded = false
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 4: Update the call site to use the new composable**

Replace, at what is currently lines 333-337:

```kotlin
            RequiredTextField(
                value = makeText,
                onValueChange = onMakeTextChanged,
                label = "Marke",
            )
```

with:

```kotlin
            RequiredBrandDropdownField(
                value = makeText,
                onValueChange = onMakeTextChanged,
            )
```

- [ ] **Step 5: Compile the module**

Run: `./gradlew :feature:photocapture:impl:compileDebugSources`
Expected: BUILD SUCCESSFUL, no unresolved references, no unused-import warnings for the new imports.

- [ ] **Step 6: Run the module's existing unit tests to confirm no regression**

Run: `./gradlew :feature:photocapture:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — `PhotoCaptureViewModelTest` still passes unchanged, since `makeText`/`onMakeTextChanged` were not touched.

- [ ] **Step 7: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt
git commit -m "Add predefined brand dropdown with free-text fallback for Marke field"
```

## Self-Review Notes

- Spec coverage: brand list ✓ (Step 2), editable combobox with type-ahead filtering ✓ (Step 3), free text always accepted (filtering never blocks typing, just narrows suggestions) ✓, required/touched/error behavior preserved ✓, no ViewModel changes ✓, no new tests per module convention ✓.
- Type consistency: `RequiredBrandDropdownField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier)` matches the call site in Step 4 exactly (no `label` param, since it's hardcoded to `"Marke *"` per spec).
