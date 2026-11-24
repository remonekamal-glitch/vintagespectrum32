# Vintage Spectrum 32 — Java 17

Standalone Android floating spectrum visualizer project.

## Build on GitHub Actions
1. Upload the contents of this folder to a new GitHub repository (keep `.github/workflows/build.yml`).
2. Open the repository's **Actions** tab.
3. Select **Build Vintage Spectrum 32 APK**.
4. Tap **Run workflow**.
5. When complete, open the run and download the `Vintage-Spectrum-32-debug` artifact.
6. Extract the ZIP and install `app-debug.apk` on Android.

## Build configuration
- Java 17 (Temurin)
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- compileSdk 35, targetSdk 32, minSdk 26

## First launch
Grant microphone permission and Android's "Display over other apps" permission.
Select a mode and input, then tap Start Floating Visualizer.

Internal playback capture requires Android 10+ and a source app that permits playback capture.
Some apps (including protected or privacy-sensitive audio) block capture. Android displays a system
screen-capture consent prompt before internal capture starts.

## Important prototype notes
This is a starter implementation and needs device testing. It includes a 512-point FFT mapped to
32 logarithmic frequency bands, a draggable overlay, and pinch resizing. The capture source selector
offers microphone or Android playback capture. Android playback capture works only on Android 10+
and only when the playing app permits capture; protected apps may provide silence. Audio capture may
also be affected by device policy and permissions.
