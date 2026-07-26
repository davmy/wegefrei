# Gate Weiter on Complete Witness Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping "Weiter" on the report screen checks whether "Meine Angaben" (witness details) is completely and correctly filled out; if not, navigate there instead of doing nothing.

**Architecture:** `feature/witness/impl` exposes exactly one new public function, `areWitnessDetailsComplete(context): Boolean`, the only thing that crosses its module boundary — never the underlying name/address/email values. The "Weiter" button's `onClick` becomes a threaded `onWeiterRequested: () -> Unit` callback, resolved in `MainActivity` (the only place allowed to know both feature modules) to a coroutine that checks completeness and navigates to `WitnessDetailsRoute` when it's false.

**Tech Stack:** Kotlin, Jetpack Compose, existing `WitnessDetailsRepository`/`isValidEmail` (from the witness-details feature already on this branch).

## Global Constraints

- `feature/photocapture` gains no dependency on `feature/witness`, and vice versa — the only new cross-module surface is the single public function in `feature/witness/impl`.
- "Correctly filled out" means: name and address non-blank, email non-blank AND passing the existing `isValidEmail` check — the same rule the "Meine Angaben" screen itself already enforces.
- The "Weiter" button's `enabled` state is unchanged (still gated only on the vehicle fields). This is a click-time check, not a new visual precondition.
- When redirected to "Meine Angaben", nothing is passed into it — it loads its own persisted state as it already does.
- When witness details ARE complete, "Weiter" remains a no-op — there is still no next step built.
- `areWitnessDetailsComplete` and the Compose/MainActivity wiring are left without automated tests (thin DataStore wrapper / UI wiring, consistent with this codebase). `isWitnessDetailsRecordComplete` (the pure predicate it delegates to) DOES get tests.

---

### Task 1: `areWitnessDetailsComplete` public completeness check

**Files:**
- Create: `feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsCompletion.kt`
- Test: `feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsCompletionTest.kt`

**Interfaces:**
- Consumes: `DataStoreWitnessDetailsRepository`, `isValidEmail` (already on this branch)
- Produces: `internal fun isWitnessDetailsRecordComplete(name: String, address: String, email: String): Boolean`
- Produces: `suspend fun areWitnessDetailsComplete(context: Context): Boolean` (public — this is the module's new external entry point)

- [ ] **Step 1: Write the failing tests**

```kotlin
package de.wegefrei.app.feature.witness.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WitnessDetailsCompletionTest {

    @Test
    fun `isWitnessDetailsRecordComplete is true when all fields are present and email is valid`() {
        assertTrue(isWitnessDetailsRecordComplete("Max Mustermann", "Musterstraße 1", "max@example.com"))
    }

    @Test
    fun `isWitnessDetailsRecordComplete is false when name is blank`() {
        assertFalse(isWitnessDetailsRecordComplete("", "Musterstraße 1", "max@example.com"))
    }

    @Test
    fun `isWitnessDetailsRecordComplete is false when address is blank`() {
        assertFalse(isWitnessDetailsRecordComplete("Max Mustermann", "", "max@example.com"))
    }

    @Test
    fun `isWitnessDetailsRecordComplete is false when email is blank`() {
        assertFalse(isWitnessDetailsRecordComplete("Max Mustermann", "Musterstraße 1", ""))
    }

    @Test
    fun `isWitnessDetailsRecordComplete is false when email is present but invalid`() {
        assertFalse(isWitnessDetailsRecordComplete("Max Mustermann", "Musterstraße 1", "not-an-email"))
    }
}
```

This is a plain JUnit test (no `@RunWith(RobolectricTestRunner::class)` needed) — `isWitnessDetailsRecordComplete` only uses `String`/`isValidEmail` (itself pure), no Android-framework classes.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.witness.impl.WitnessDetailsCompletionTest"`
Expected: FAIL — `isWitnessDetailsRecordComplete` is unresolved (function doesn't exist yet).

- [ ] **Step 3: Create `WitnessDetailsCompletion.kt`**

```kotlin
package de.wegefrei.app.feature.witness.impl

import android.content.Context
import kotlinx.coroutines.flow.first

internal fun isWitnessDetailsRecordComplete(name: String, address: String, email: String): Boolean =
    name.isNotBlank() && address.isNotBlank() && email.isNotBlank() && isValidEmail(email)

suspend fun areWitnessDetailsComplete(context: Context): Boolean {
    val repository = DataStoreWitnessDetailsRepository(context.applicationContext)
    return isWitnessDetailsRecordComplete(
        name = repository.name.first(),
        address = repository.address.first(),
        email = repository.email.first(),
    )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:witness:impl:testDebugUnitTest --tests "de.wegefrei.app.feature.witness.impl.WitnessDetailsCompletionTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/witness/impl/src/main/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsCompletion.kt \
        feature/witness/impl/src/test/java/de/wegefrei/app/feature/witness/impl/WitnessDetailsCompletionTest.kt
git commit -m "Add areWitnessDetailsComplete public completeness check"
```

---

### Task 2: Wire Weiter to gate on witness details

**Files:**
- Modify: `feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt`
- Modify: `app/src/main/java/de/wegefrei/app/MainActivity.kt`

**Interfaces:**
- Consumes: `areWitnessDetailsComplete(context: Context): Boolean` (Task 1), `WitnessDetailsRoute` (already on this branch)
- Produces: `PhotoCaptureRoot(..., onWeiterRequested: () -> Unit)` (new required parameter), `PhotoCaptureScreen(..., onWeiterRequested: () -> Unit)` (new required parameter)

`feature/photocapture` gains no dependency on `feature/witness` — `onWeiterRequested` is a plain lambda, same as `onOpenWitnessDetailsRequested`.

No automated test for this task — Compose UI wiring.

- [ ] **Step 1: Thread `onWeiterRequested` through `PhotoCaptureRoot`**

In `PhotoCaptureScreen.kt`, add `onWeiterRequested: () -> Unit,` as a new parameter of `PhotoCaptureRoot`, right after the existing `onOpenWitnessDetailsRequested: () -> Unit,`:

```kotlin
internal fun PhotoCaptureRoot(
    viewModel: PhotoCaptureViewModel = viewModel(),
    onOpenWitnessDetailsRequested: () -> Unit,
    onWeiterRequested: () -> Unit,
) {
```

In the `PhotoCaptureScreen(...)` call inside `PhotoCaptureRoot`, add this parameter (e.g. right after the existing `onOpenWitnessDetailsRequested = onOpenWitnessDetailsRequested,` line):

```kotlin
            onWeiterRequested = onWeiterRequested,
```

- [ ] **Step 2: Add the parameter to `PhotoCaptureScreen`'s signature and wire the button**

Add `onWeiterRequested: () -> Unit,` to the `PhotoCaptureScreen` function signature, after the existing `onOpenWitnessDetailsRequested: () -> Unit,` parameter.

Replace the "Weiter" `Button`'s `onClick = {}` with:

```kotlin
                onClick = onWeiterRequested,
```

(Leave the `enabled = licensePlateText.isNotBlank() && makeText.isNotBlank() && colorText.isNotBlank(),` line and everything else about that `Button` exactly as it is.)

- [ ] **Step 3: Wire `MainActivity.kt`**

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import de.wegefrei.app.core.designsystem.WegefreiTheme
import de.wegefrei.app.feature.photocapture.api.PhotoCaptureRoute
import de.wegefrei.app.feature.photocapture.impl.photoCaptureScreen
import de.wegefrei.app.feature.witness.api.WitnessDetailsRoute
import de.wegefrei.app.feature.witness.impl.areWitnessDetailsComplete
import de.wegefrei.app.feature.witness.impl.witnessDetailsScreen
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = PhotoCaptureRoute) {
        photoCaptureScreen(
            onOpenWitnessDetailsRequested = { navController.navigate(WitnessDetailsRoute) },
            onWeiterRequested = {
                coroutineScope.launch {
                    if (!areWitnessDetailsComplete(context)) {
                        navController.navigate(WitnessDetailsRoute)
                    }
                }
            },
        )
        witnessDetailsScreen(
            onBackRequested = { navController.navigateUp() },
        )
    }
}
```

- [ ] **Step 4: Build the project**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full project test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions from Task 1).

- [ ] **Step 6: Manual verification**

Install and run the app (`./gradlew :app:installDebug`, or use the `run` skill if available):
- With "Meine Angaben" empty/incomplete (fresh install, or clear its fields): fill in the vehicle fields so "Weiter" becomes enabled, tap it → confirm navigation to "Meine Angaben" (not a no-op).
- Fill in all three "Meine Angaben" fields correctly, go back to the report screen, tap "Weiter" again → confirm it does NOT navigate anywhere this time (stays on the report screen, since there's still no next step).
- Confirm "Meine Angaben" shows its own previously-saved values when opened this way — nothing was overwritten or pre-filled from the report screen.

- [ ] **Step 7: Commit**

```bash
git add feature/photocapture/impl/src/main/java/de/wegefrei/app/feature/photocapture/impl/PhotoCaptureScreen.kt \
        app/src/main/java/de/wegefrei/app/MainActivity.kt
git commit -m "Gate Weiter on complete witness details"
```
