# Compose and send the report via email

## Context

The report screen collects photos, vehicle details, incident time, address,
and (on a separate screen) the witness's own contact details — but nothing
is ever done with any of it. "Weiter" has been a no-op since it was added.
This feature gives it a real purpose: build the actual complaint email
(matching a fixed German legal-complaint template) and hand it to whichever
email app the user has installed via Android's share/send intent — this app
never sends anything itself, never talks to an email API/SMTP server.

Two fields the template needs don't exist in the app yet: "Verstoß"
(violation description) and "Behinderung" (obstruction/danger description).
Both are added as required fields, same pattern as the existing
Kennzeichen/Marke/Farbe fields.

## Non-goals

- No in-app email sending (no SMTP, no email-sending API/service). "Some
  API" in the request refers to Android's `Intent` system — the OS-level
  mechanism for handing off to another app — not a network email API.
- No recipient address is pre-filled; "To" stays empty for the user to fill
  in, since the right authority varies by location/police jurisdiction and
  the app has no way to know it.
- No new "Weitere Zeugen" (additional witnesses) feature — that line in the
  template is always the literal `-`.
- No vehicle-type field — "PKW" is always appended literally after
  Kennzeichen/Marke/Farbe, matching the template; the app doesn't collect a
  vehicle type today and this doesn't change that.
- No validation/preview of the generated email before handoff — the user
  reviews and edits it in their own email app before sending, same as any
  other app that hands off to `ACTION_SEND`.

## Architecture decision: exposing witness *values*, not just completeness

The witness-details module's public surface today is deliberately
boolean-only (`areWitnessDetailsComplete`) — built that way specifically so
no other module could see the underlying name/address/email. Building the
actual email body requires those values. This is a deliberate, intentional
relaxation of that earlier decision, not a violation of it: `feature/witness/impl`
gains one more public entry point,

```kotlin
data class WitnessDetails(val name: String, val address: String, val email: String)
suspend fun readWitnessDetails(context: Context): WitnessDetails
```

Both new witness-crossing values (the boolean check and now this) remain
the *only* public surface `feature/witness/impl` exposes; the internal
repository, ViewModel, and screen stay internal exactly as before.

## Components

### `feature/photocapture/impl`: new required fields

`PhotoCaptureViewModel` gains `violationText`/`obstructionText`
(`StateFlow<String>` + `onXChanged` setters), following the exact pattern
already used for `licensePlateText`/`makeText`/`colorText` — no auto-fill
logic, just plain required text.

`PhotoCaptureScreen` gains a new section (placed after "Tatort", before
"Weiter"): a header, then two `RequiredTextField`s labeled "Verstoß" and
"Behinderung". The "Weiter" button's `enabled` expression grows to also
require these two non-blank, alongside the existing vehicle-field check.

### `feature/photocapture/impl`: exposing report data to `:app`

`onWeiterRequested` changes shape from `() -> Unit` to
`(ReportDetails) -> Unit`, where `ReportDetails` is a new public data class
in this module (an "impl" module can and already does expose specific
public members for `:app` to consume — see `photoCaptureScreen()`,
`areWitnessDetailsComplete`):

```kotlin
data class ReportDetails(
    val licensePlate: String,
    val make: String,
    val color: String,
    val address: String,
    val incidentDateTime: LocalDateTime,
    val violation: String,
    val obstruction: String,
    val photoUris: List<Uri>,
)
```

This is only constructed and passed when the button is actually tappable
(i.e., all required fields — including the two new ones — are already
non-blank), so every field is trusted to be non-blank; no additional
validation happens downstream.

### `feature/photocapture/impl`: photo compression for attachment

```kotlin
interface EmailAttachmentPreparer {
    suspend fun prepareAttachments(photoUris: List<Uri>): List<Uri>
}
```

`CompressingEmailAttachmentPreparer(context: Context)` implementation:
decodes each source `Uri` (camera-captured `file://` in app-private
storage, or gallery-picked `content://`) into a downscaled `Bitmap`
(bounded to a max dimension, e.g. 1600px on the long side, via
`inSampleSize` during decode to avoid loading huge originals into memory),
re-encodes as JPEG at a reduced quality (e.g. 80), and writes the result
into a fresh file under `context.cacheDir`. Every output file is exposed as
a `content://` URI via a new `FileProvider` (declared in this module's
manifest, authority `${applicationId}.fileprovider`, root pointing at the
cache dir) — this sidesteps entirely whether the original source URIs are
re-shareable to another app, since the attachment is always this module's
own freshly-written, already-owned file.

Left untested (real bitmap/file/Android I/O), consistent with this
module's other platform wrappers.

### `:app`: composing and launching the email

New file `EmailReportComposer.kt` in `app/src/main/java/de/wegefrei/app/`,
split into a pure part and an impure part:

```kotlin
fun buildReportEmailSubject(): String =
    "Anzeige einer Verkehrsordnungswidrigkeit"

fun buildReportEmailBody(witness: WitnessDetails, report: ReportDetails): String
```

`buildReportEmailBody` is a pure string-formatting function producing
exactly the template from the request, with `LocalDateTime` formatted via
`DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)` (same
pattern/locale already used for `Tatzeitpunkt`'s own display), "Weitere
Zeugen" always `-`, and "PKW" always appended after the vehicle fields.
This is the one part of this feature with real, non-obvious string-assembly
logic, so it gets tests.

```kotlin
fun buildReportEmailIntent(subject: String, body: String, attachmentUris: List<Uri>): Intent
```

Builds `Intent(Intent.ACTION_SEND_MULTIPLE)` (or plain `ACTION_SEND` if
`attachmentUris` is empty — a user can reach "Weiter" with zero photos,
since photos aren't a required field), `type = "message/rfc822"` (restricts
the system chooser to email apps), `EXTRA_SUBJECT`/`EXTRA_TEXT` set, the
attachment URIs passed via `EXTRA_STREAM`, and
`FLAG_GRANT_READ_URI_PERMISSION` so the receiving email app can actually
read the `FileProvider` URIs.

### `:app`: wiring in `MainActivity`

`onWeiterRequested` becomes `onWeiterRequested = { reportDetails -> ... }`,
running in a coroutine (same `rememberCoroutineScope()` already there):

1. If `!areWitnessDetailsComplete(context)` → navigate to
   `WitnessDetailsRoute` (unchanged from today).
2. Otherwise: `readWitnessDetails(context)` for the witness values,
   `EmailAttachmentPreparer.prepareAttachments(reportDetails.photoUris)` for
   compressed attachment URIs, build the subject/body/intent, and
   `context.startActivity(Intent.createChooser(intent, "E-Mail senden"))`.

`MainActivity` is the only place with both feature modules' data in scope,
which is exactly why the email composition itself (the part that touches
both witness and report data together) lives here rather than in either
feature module.

## Error handling

- No installed email app: `ACTION_SEND_MULTIPLE` with `type = "message/rfc822"`
  will simply fail to resolve; wrap the `startActivity` call so a missing
  handler doesn't crash the app (a real, if rare, possibility — this device
  class isn't guaranteed to have an email app installed). No error UI is
  planned beyond "nothing visibly happens" for this first version, matching
  this app's existing silent-failure style elsewhere.
- Attachment compression failures (corrupt image, decode failure) are
  skipped per-photo — one bad photo shouldn't block sending the rest.

## Testing

- `PhotoCaptureViewModel`'s two new fields: same trivial
  set/get tests as the existing vehicle fields.
- `buildReportEmailBody`: unit tests asserting the exact generated string
  for a representative `WitnessDetails`/`ReportDetails` pair, matching the
  template precisely (including the literal `-` and `PKW`).
- `CompressingEmailAttachmentPreparer`, the `FileProvider` wiring, the new
  required-field UI, and the `MainActivity` intent-launching wiring are all
  left untested — real Android I/O and Compose UI wiring, consistent with
  the rest of this codebase.
