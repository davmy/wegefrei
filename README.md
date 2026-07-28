# wegefrei

[![CI](https://github.com/davmy/wegefrei/actions/workflows/ci.yml/badge.svg)](https://github.com/davmy/wegefrei/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/minSdk-33-brightgreen)](https://developer.android.com/tools/releases/platforms#33)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Android app for reporting illegally parked vehicles in Germany.

Take a photo of the offending vehicle and wegefrei turns it into a ready-to-send report: it works out where and when the photo was taken, lets you fill in the vehicle and violation details, and hands you a pre-filled email — addressed to the local authority — with the photo attached. Your own contact details only need to be entered once and are then remembered for future reports.

## Features

- Take a photo with the camera or pick an existing one from the gallery
- Location and time are detected automatically from the photo (EXIF data) or the current position
- Simple form for the violation details: license plate, make, color, type of violation, location, time, and optional notes (e.g. obstruction, how long the vehicle was parked)
- Your own details (name, address, email) are entered once and saved for next time
- Generates a complete, pre-filled report email with the photo attached, ready to review and send

## Status

In active development. The core flow described above already works.

## Development

### Tech Stack

- Kotlin
- Jetpack Compose
- Navigation Compose (type-safe routes)
- CameraX (photo capture)
- Android Photo Picker (photo selection from the gallery)
- Nominatim/OpenStreetMap address lookup (reverse geocoding) for the incident location
- Jetpack DataStore (Preferences) for storing witness data
- kotlinx.serialization
- Coil (image thumbnails)

### Module Structure

- `app` — entry point, NavHost, wires up the feature modules, assembles the report email
- `core:designsystem` — theme, colors, typography, shared form UI building blocks
- `feature:photocapture` — capturing/selecting photos, location/time detection, the violation report form
- `feature:witness` — capturing and storing the reporter's own witness data ("My Details")
- `feature:<name>:api` — the feature's public navigation route
- `feature:<name>:impl` — screens, ViewModel, navigation registration (internal except for the registration function)

### Workflow

Each feature is developed on its own branch (`feature/*`). Pull requests are created and merged manually.

### Build

`./gradlew :app:assembleDebug` is enough — the Gradle wrapper fetches a suitable JDK 21 itself via `gradle/gradle-daemon-jvm.properties` if needed.

## License

[GPL-3.0](LICENSE)
