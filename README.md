# wegefrei

Android-App zum Melden von Falschparkern in Deutschland.

## Status

In aktiver Entwicklung. Aktueller Stand: Foto einer bereits vorhandenen Aufnahme auswählen oder ein neues Foto mit der Kamera aufnehmen.

## Tech Stack

- Kotlin
- Jetpack Compose
- CameraX (Fotoaufnahme)
- Android Photo Picker (Fotoauswahl aus der Galerie)

## Entwicklung

Jedes Feature wird auf einem eigenen Branch entwickelt (`feature/*`). Pull Requests werden manuell erstellt und gemerged.

### Build

`./gradlew :app:assembleDebug` genügt — der Gradle-Wrapper lädt sich über `gradle/gradle-daemon-jvm.properties` bei Bedarf selbst ein passendes JDK 21.
