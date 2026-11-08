# Tsukihime Android 0.1.2 (development)

## Game data: no-copy mode

The launcher asks the user to select the existing local extracted RomFS `assets` directory, or its parent directory. It validates the selected folder in place and stores its URI and resolved local filesystem path. It does **not** copy, move, rename, or delete game data. “Forget selected folder” only clears the saved location and releases the persistable URI permission.

The current upstream `knife` engine uses ordinary filesystem paths (`std::fs`) for assets. This first Android storage integration therefore supports the Android ExternalStorageProvider's local shared-storage paths and uses `MANAGE_EXTERNAL_STORAGE` so the Rust process can read those paths. It rejects cloud/document providers because they do not expose normal filesystem paths. A future SAF-backed VFS would remove the broad permission requirement, but that is a separate Rust-engine change.

The app expects the selected folder itself to be named `assets`, or to contain an `assets` child. Required initial checks are `nintendo_udsg-r_std_003.bfttf`, `allscr.mrg`, and `script_text.mrg`. The upstream README also describes the game RomFS and font requirements. This is only basic validation, not a complete integrity check.

## Videos

The upstream desktop backends are VideoToolbox (macOS), Media Foundation (Windows), and GStreamer (other desktop platforms). The Android-specific decoder in this source package is still a placeholder that returns “not implemented”. Excluding GStreamer from Android does **not** mean the video feature is implemented. Android MediaCodec must be implemented against the existing `FrameDecoder` interface before story movies/title movies can play.

## Saves

The upstream engine implements Switch-format `savedata000` with 100 manual save slots, an auto-save ring and a quick-save slot. On Android, the port must explicitly set `--save-file` to an app-private path (for example `filesDir/knife/savedata000` for Japanese, with separate `overseas/savedata000` for overseas data). The engine owns serialization and save/load UI. The app should later expose export/import of these small save files; uninstalling the app otherwise removes app-private saves.

## Current status

This is an Android launcher/storage preparation build. It does not yet contain a working Rust NativeActivity integration, Android MediaCodec decoder, or playable engine. Do not treat a successful launcher APK build as proof of gameplay support.
