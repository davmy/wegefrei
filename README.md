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

Benötigt ein JDK 17-21 mit `javac` (die vorinstallierte JRE reicht nicht). Falls `JAVA_HOME` nicht auf ein passendes JDK zeigt:

```
./gradlew -Dorg.gradle.java.home=/pfad/zu/jdk-21 :app:assembleDebug
```
