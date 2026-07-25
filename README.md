# wegefrei

Android-App zum Melden von Falschparkern in Deutschland.

## Status

In aktiver Entwicklung. Aktueller Stand: Foto einer bereits vorhandenen Aufnahme auswählen oder ein neues Foto mit der Kamera aufnehmen.

## Tech Stack

- Kotlin
- Jetpack Compose
- Navigation Compose (typsichere Routen)
- CameraX (Fotoaufnahme)
- Android Photo Picker (Fotoauswahl aus der Galerie)

## Modulstruktur

- `app` — Einstiegspunkt, NavHost, verdrahtet die Feature-Module
- `core:designsystem` — Theme, Farben, Typografie
- `feature:<name>:api` — öffentliche Navigationsroute des Features
- `feature:<name>:impl` — Screens, ViewModel, Navigationsregistrierung (internal, außer der Registrierungsfunktion)

## Entwicklung

Jedes Feature wird auf einem eigenen Branch entwickelt (`feature/*`). Pull Requests werden manuell erstellt und gemerged.

### Build

`./gradlew :app:assembleDebug` genügt — der Gradle-Wrapper lädt sich über `gradle/gradle-daemon-jvm.properties` bei Bedarf selbst ein passendes JDK 21.
