# TsukiPort Android build and import notes

## Import flow

The launcher no longer requests `MANAGE_EXTERNAL_STORAGE` and no longer asks the user to
manually select an extracted RomFS folder.

1. Select one base-game `.nsp` or `.xci`, or every part of one split XCI.
2. Select `prod.keys`.
3. Select `title.keys` only if the dump needs a title key.
4. Select `nintendo_udsg-r_std_003.bfttf`, the Switch shared system font required by Knife.
5. Press Prepare Game Data and wait for verification/extraction to finish.
6. Press Play.

Select one base-game package and optionally one matching update package. The importer applies a
matching update patch when supplied; Knife's engine documentation recommends Tsukihime update
1.0.3, while noting that earlier game data still plays. DLC is not supported in this milestone.

The native importer requires seekable local file descriptors. Select files stored locally on
the phone; cloud providers that return non-seekable streams are rejected. The original package
and key files are read-only and are not copied or modified.

## Private storage

The importer extracts into app-private storage under `filesDir/tsukihime-import-staging`,
validates the result, and then switches it into `filesDir/tsukihime-import`. The active RomFS
path is written to `filesDir/assets-path.txt`, preserving the existing Rust engine handoff.
A staging directory makes failed imports non-destructive to the previous prepared install.

`prod.keys` and optional `title.keys` are opened only for the import call. Their contents are
not saved or uploaded. The separately selected standard BFTTF system font is retained in private
app storage and copied into the prepared RomFS. Additional Chinese-language system fonts are not
surfaced by this first launcher build.

## Native components

- `libmain.so`: the existing Rust Knife engine, built for `arm64-v8a` with `cargo-ndk`.
- `libnximport.so`: the C++ extraction importer and its JNI bridge, built with Android NDK CMake.
- `libpackagejni.so`: the direct-package JNI bridge, linked one-way against `libmain.so`; the native package reader C ABI is compiled into `libmain.so`.
- `nximport.cpp` and `nxcrypto.cpp`: adapted from the MIT-licensed P5R Android importer.
- `nximport_jni.cpp`: package-renamed JNI bridge for `com.dev.tsukihimeandroid.NativeImporter`.

The importer extraction path is deliberately used instead of P5R's direct GameImage runtime.
Knife currently resolves ordinary filesystem paths for archives and scripts; a virtual RomFS
filesystem would require a separate, invasive change to its asset-loading abstraction.

## Build from CI

Run `.github/workflows/android-arm64.yml`. It installs JDK 17, Gradle 9.6.1, Android SDK 36,
NDK 28.2.13676358 and CMake 3.22.1; runs host importer tests; builds the Rust ARM64 engine;
runs `gradle :app:assembleRelease`, and checks APK ZIP alignment plus every packaged ARM64
ELF load segment for 16 KiB alignment. The artifact is unsigned.

Kotlin Gradle Plugin 2.4.21 is used with AGP 9.3.1; the Kotlin compatibility table lists
AGP 9.3.1 and Gradle through 9.7.0 as fully supported for KGP 2.4.20–2.4.21.

## Sign locally

Follow `TERMUX-SIGNING.md`. Keep the keystore and passwords in Termux's private home directory.
Never upload them to the repository or ChatGPT.

## Existing gameplay

The following files were compared byte-for-byte with the supplied `knife-android-0.1.5`
baseline and must remain unchanged by this importer work:

- `app/src/main/java/com/dev/tsukihimeandroid/GameActivity.kt`
- `app/src/main/java/com/dev/tsukihimeandroid/SaveBackupManager.kt`
- all files under `engine/`

These contain the existing in-game touch controls, backlog controls, save/quick-save/load
bridge, game logic, renderer and audio implementation.
