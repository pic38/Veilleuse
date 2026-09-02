*[Version française](README.fr.md)*

# Veilleuse

Minimalist Android app, pure black OLED theme, that turns the phone
into a night light: camera flash or screen (with adjustable warm tint
and brightness), adjustable duration, instant or progressive shutdown.
Secondary settings screen with time format, accent color (palette of
hues + white, with a slider to darken it) and app language (26
languages, or "System"). No Internet permission, no tracker, 100% free
software (GPL-3.0-or-later).

## How it works

1. On the main screen: choose the source (**Flash** or **Screen**),
   the duration before shutdown (1 to 120 min), the shutdown mode
   (**Instant** or **Progressive** with adjustable fade duration), and
   for Screen mode, the warm tint and brightness.
2. ⚙️ icon top right: **Settings** screen — time display format
   (Compact / Detailed / HH:MM:SS), UI accent color (palette of hues +
   white, with a slider to darken it), and app language (among 26
   languages, or "System" to follow the device's). A note at the
   bottom of this screen links back to this GitHub repo to report a
   bug or suggest something.
3. Tap **Start**: the interface disappears, the screen turns black (or
   shows the chosen warm color full-screen). The torch or the screen
   stay active, immersive mode (system bars hidden).
4. Tapping the screen temporarily brings back the remaining time and
   the **Stop** button (auto-hidden again after a few seconds).
5. When the timer ends, the light turns off (instantly or fading out)
   and the app closes itself, letting the phone go back to normal
   sleep.

## Building the project

Requirements: JDK 17, Android SDK (compileSdk 34), an Internet
connection to download Gradle/AndroidX dependencies (no proprietary
dependency, no Google Play service).

If the machine's default JDK isn't version 17 (the Kotlin compiler
bundled with Gradle can crash with newer JDKs), point Gradle to a JDK
17 without touching the repo: add
`org.gradle.java.home=/path/to/jdk17` to the user's global
`gradle.properties` (`~/.gradle/gradle.properties`), or set the
`JAVA_HOME` environment variable.

The `gradle-wrapper.jar` binary isn't versioned in this repo. Two
options:

- **Android Studio**: open the project folder, Android Studio
  regenerates the wrapper automatically on first sync.
- **Command line**, if `gradle` is installed on the machine:

  ```bash
  gradle wrapper --gradle-version 8.7
  ./gradlew assembleDebug
  ```

The debug APK is produced in `app/build/outputs/apk/debug/`.

## Repository structure

```
app/                      Application source code (Kotlin)
                            Resources translated into 26 languages
                            (app/src/main/res/values-*)
metadata/                  F-Droid/Play (fastlane) metadata: title,
                            descriptions, changelogs, in fr-FR and en-US
fdroid-metadata-template.yml   Template used for the fdroiddata submission
CHANGELOG.md               Detailed version history
LICENSE                    GPL-3.0-or-later
```

## Publishing to F-Droid

The project has been submitted to F-Droid: see the merge request
<https://gitlab.com/fdroid/fdroiddata/-/merge_requests/47609> on the
`fdroiddata` repo (awaiting maintainer review).

For reference, the process followed:

1. Code published on a public Git repo: <https://github.com/pic38/Veilleuse>.
2. `applicationId` / `namespace` (`app/build.gradle.kts`) set to
   `io.github.pic38.veilleuse`, an identifier actually owned by the
   author, with the matching Kotlin package under
   `app/src/main/java/io/github/pic38/veilleuse/`.
3. One Git tag per version (`vX.Y.Z`), matching `versionName` /
   `versionCode` (`app/build.gradle.kts`).
4. Official "Submitting to F-Droid" guide followed:
   <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/>
   — a merge request on the `fdroiddata` repo with a
   `metadata/<applicationId>.yml` file based on the
   `fdroid-metadata-template.yml` provided here.

## Permissions

No "dangerous" permission is requested: controlling the torch
(`CameraManager.setTorchMode`) doesn't require the `CAMERA` permission
on Android, and the app has no Internet permission.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE).
