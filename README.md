# Tsukihime Android 0.1.3 (development)

This package contains the Kotlin launcher and the upstream Rust `knife` engine source adapted for Android GameActivity. It is an integration development build, not yet a device-tested playable release.

1. Open the Gradle project in Code on the Go. A local build without the native Rust library is only the importer shell; Play stays disabled.
2. Build the Rust ARM64 library and debug APK with the included GitHub Actions workflow (`Android ARM64 debug APK`) from a GitHub repository containing this project.
3. Install the resulting ARM64 debug APK, select the existing extracted `assets` folder from a normal shared-storage folder (not another app's `Android/data`), then press Play.

The engine uses the user's own extracted game data. No copyrighted game assets are bundled. See `ANDROID-PORT-NEXT-STAGE.md` for integration and validation status.
