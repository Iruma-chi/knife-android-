# Tsukihime Android 0.1.3 - GameActivity host, Android media and virtual controls integration

## Included in this stage

- The upstream `knife` application source is a Rust library (`cdylib`) named `main`, loaded by Android GameActivity through Winit’s `android-game-activity` backend.
- A desktop binary wrapper remains available for non-Android targets.
- Android `android_main` reads `files/assets-path.txt`, configures the existing engine with the selected RomFS path, stores Switch-format saves under app-private `files/knife/`, and creates Winit's event loop with the AndroidApp supplied by GameActivity.
- The Kotlin launcher persists the selected assets path into its `filesDir` and exposes a Play button that launches the declared GameActivity subclass only when `libmain.so` is packaged.
- The manifest declares the app’s GameActivity subclass and `android.app.lib_name=main`, with landscape/fullscreen configuration.
- The previous missing `touch_id: None` initialization is fixed. Android finger events route through the engine's existing touch/mouse gesture implementation (tap, drag, edge swipes, mode panel, backlog and menu gestures). The original title/settings/save/load UI is not recreated in Kotlin.
- Android virtual controls call a JNI bridge which queues typed actions through Winit's `EventLoopProxy`; the game thread dispatches them into the existing Knife Pad and save/load actions. Android external gamepad polling remains disabled in this stage.
- The Android movie backend uses the NDK `MediaCodec` API, requests H.264 decoding into YUV420 semi-planar output, retries planar YUV420 if configuration rejects semi-planar, and converts decoded frames to the NV12 format consumed by the existing renderer.
- AAC movie audio continues through the existing Symphonia decoder and Kira/CPAL output. Winit's `android-activity` glue initializes the Android JNI context before `android_main`, which CPAL's Android backend can use.
- A GitHub Actions workflow builds the Rust ARM64 library and packages it into a debug APK.

## Build

A Gradle build without the native Rust library creates only the Kotlin launcher/importer shell; the Play button remains disabled. The controls patch uses the GameActivity AAR and Winit’s matching GameActivity backend. To build a playable candidate, the Rust library must be built first and placed in `app/src/main/jniLibs/arm64-v8a/libmain.so`. The included GitHub Actions workflow does this with `cargo-ndk`, then runs Gradle `assembleDebug`.

## Important storage limitation

Android 11 and later prevent apps from accessing another app's external app-specific directory under `Android/data`, even with All files access. The system folder picker also blocks `Android/data` and `Android/obb`. Put the extracted `assets` folder in a normal shared-storage location such as `Download/Tsukihime/assets` or `Documents/Tsukihime/assets`, then select that folder. Do not put it inside another app's `Android/data` folder.

## Validation status

Build #16 was successfully compiled and installed by the user, who confirmed that the game launches and accepts touch input. The separate GameActivity controls patch has not yet been compiled or installed; it needs an ARM64 GitHub Actions build and device testing. See `ANDROID-CONTROLS-IMPLEMENTATION.md` for the patch validation status.
