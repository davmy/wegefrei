# Gate "Weiter" on complete witness details

## Context

The report screen's "Weiter" button currently does nothing (`onClick = {}`) —
there's no next step built yet. The witness-details ("Meine Angaben") screen,
built earlier in this branch, holds data that's just as necessary for a
report as the vehicle fields already gating that button. Tapping "Weiter"
should first make sure the witness's own details are complete; if not, send
the user to "Meine Angaben" instead of wherever "Weiter" eventually goes.

## Non-goals

- No change to the "Weiter" button's own `enabled` state (still gated only
  on the vehicle fields, as today) — this is a click-time check, not a
  visual precondition shown before the tap.
- No prepopulating "Meine Angaben" with anything when redirected there — it
  already loads its own persisted state on its own; nothing from the report
  screen is passed in.
- Still no actual "next step" once witness details ARE complete — "Weiter"
  remains a no-op in that case, same as today.

## The module-boundary problem

`feature/photocapture` and `feature/witness` must not depend on each other
(established when "Meine Angaben" was built). Checking "are the witness
details complete" requires reading data that lives entirely inside
`feature/witness/impl` (`WitnessDetailsRepository`, now `internal`).
`MainActivity` is the only place allowed to know about both modules, so the
check has to happen there — but it must not reach into `feature/witness`'s
internals to do it.

Fix: `feature/witness/impl` exposes exactly one new **public** (non-internal)
entry point that returns a `Boolean`, never the underlying values:

```kotlin
suspend fun areWitnessDetailsComplete(context: Context): Boolean
```

This is the only thing that crosses the boundary. `MainActivity` gets a
yes/no answer and never sees the name/address/email themselves — the same
encapsulation discipline as the rest of this feature, just narrowed to a
single derived fact instead of zero facts.

## Components

### `feature/witness/impl`

`WitnessDetailsCompletion.kt` (new file):

```kotlin
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

"Correctly filled out" means the same rule the screen itself already
enforces: name and address non-blank, email non-blank *and* passing the
existing `isValidEmail` format check.

`isWitnessDetailsRecordComplete` is the pure, testable half (mirrors how
`isValidEmail` was split out earlier). `areWitnessDetailsComplete` is the
thin DataStore-reading wrapper — left untested like every other real-I/O
wrapper in this codebase, but it's a two-line pass-through onto already-used
(and already covered) repository/read machinery, so the risk is low.

### `feature/photocapture/impl`

The "Weiter" `Button`'s `onClick = {}` becomes `onClick = onWeiterRequested`,
a new required parameter threaded through `PhotoCaptureScreen` and
`PhotoCaptureRoot` exactly like `onOpenWitnessDetailsRequested` was — a bare
`() -> Unit`, no knowledge of what it does or why.

### `app` (`MainActivity.kt`)

`WegefreiNavHost` gains a `rememberCoroutineScope()` and a `LocalContext`
read (both already common Compose patterns, no new dependency), and passes:

```kotlin
onWeiterRequested = {
    coroutineScope.launch {
        if (!areWitnessDetailsComplete(context)) {
            navController.navigate(WitnessDetailsRoute)
        }
    }
},
```

to `photoCaptureScreen(...)`. When the details are already complete, this is
a no-op, matching the "still no next step" non-goal above.

## Error handling

`areWitnessDetailsComplete` has no failure path of its own beyond what the
existing repository already handles (DataStore emits `""` for absent
values, which is simply "incomplete").

## Testing

- `isWitnessDetailsRecordComplete` gets a small set of pure unit tests
  (all fields present, each field blank in turn, email present-but-invalid)
  — no Robolectric needed, same as `isValidEmail`.
- `areWitnessDetailsComplete` is left untested (thin DataStore wrapper).
- The `onWeiterRequested` threading through `feature/photocapture` and the
  `MainActivity` wiring are left untested — Compose UI wiring, consistent
  with the rest of this codebase.
