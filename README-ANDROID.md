# Tsukihime Android 0.1.3 (development)

## Game data: no-copy mode

The launcher asks the user to select the existing local extracted RomFS `assets` directory, or its parent directory. It validates the selected folder in place and stores its URI and resolved local filesystem path. It does **not** copy, move, rename, or delete game data. “Forget selected folder” only clears the saved location and releases the persistable URI permission.

The current upstream `knife` engine uses ordinary filesystem paths (`std::fs`) for assets. This first Android storage integration therefore supports the Android ExternalStorageProvider's local shared-storage paths and uses `MANAGE_EXTERNAL_STORAGE` so the Rust process can read those paths. It rejects cloud/document providers because they do not expose normal filesystem paths. A future SAF-backed VFS would remove the broad permission requirement, but that is a separate Rust-engine change.

The app expects the selected folder itself to be named `assets`, or to contain an `assets` child. Required initial checks are `nintendo_udsg-r_std_003.bfttf`, `allscr.mrg`, and `script_text.mrg`. The upstream README also describes the game RomFS and font requirements. This is only basic validation, not a complete integrity check.

## Videos

The upstream desktop backends are VideoToolbox (macOS), Media Foundation (Windows), and GStreamer (other desktop platforms). Android uses the NDK MediaCodec backend in `engine/knife/src/movie/android.rs`; actual movie playback still needs device coverage across supported Android codec implementations.

## Saves

The upstream engine implements Switch-format `savedata000` with 100 manual save slots, an auto-save ring and a quick-save slot. On Android, the port must explicitly set `--save-file` to an app-private path (for example `filesDir/knife/savedata000` for Japanese, with separate `overseas/savedata000` for overseas data). The engine owns serialization and save/load UI. The app should later expose export/import of these small save files; uninstalling the app otherwise removes app-private saves.

## Current status

Build #16 successfully packages the Rust ARM64 engine and has been installed by the user; the user confirmed the game launches, renders and accepts touch input. The controls patch described below is a separate, not-yet-built source update.

## Android virtual controls

The controls patch migrates the existing NativeActivity host to AndroidX GameActivity. It uses Winit 0.30.13's `android-game-activity` backend and `androidx.games:games-activity:4.4.0`. GameActivity hosts the Rust renderer in a `SurfaceView`, allowing the Kotlin controls overlay and its Controls settings dialog to render above the game without replacing Winit's input path. The controls call a small JNI bridge that queues typed actions through Winit's `EventLoopProxy`; Knife handles them on its game thread using its existing Pad, quick-save/load and menu actions. The overlay does not synthesize keyboard events or touch game state from the Android UI thread.

See `ANDROID-CONTROLS-IMPLEMENTATION.md` for the control map, settings and validation status. The source patch still requires the project's GitHub Actions ARM64 build and device testing before it can be treated as a release APK.
