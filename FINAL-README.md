# TsukiPort 0.3.0-dev — current Android source

This source is based on the supplied `knife-android-0.1.5.zip`. It preserves the existing
gameplay controls, backlog arrows, opacity settings, save/load bridge and save-backup UI.

## App identity

- Display name: TsukiPort
- Application ID: `com.dev.tsukihimeandroid.dev`
- Gradle namespace: `com.dev.tsukihimeandroid`
- Version: `0.3.0-dev` / versionCode `6`
- ABI: `arm64-v8a`
- Release APK: unsigned; sign locally with your private Termux key.

## Launcher import

The launcher accepts one base NSP/XCI and optionally one matching update package, including
split XCI parts. It also asks for `prod.keys`, optional `title.keys`, and the shared system font
`nintendo_udsg-r_std_003.bfttf`.

The native importer reads the selected packages through SAF file descriptors, decrypts and
verifies Switch content, applies a matching BKTR update patch when supplied, and extracts
RomFS into an app-private staging directory. A separate verify-only pass checks the extracted
outputs against the importer's CRC journal. The launcher validates core archives, scripts,
sound data and the font before switching the active RomFS path.

The original NSP/XCI and key files are not modified. Keys are not stored or transmitted.
DLC and optional Chinese system fonts are not included in this first importer milestone.

## Gameplay preservation

`GameActivity.kt`, `SaveBackupManager.kt`, and every file under `engine/` are byte-for-byte
identical to the supplied 0.1.5 source. Only the launcher and build/import integration changed.
Knife's WGPU renderer and WGSL shaders remain in place; there is no Maxwell shader translation
or Switch executable emulation in this architecture.

## Build and signing

Run `.github/workflows/android-arm64.yml` to run host importer tests, build the Rust ARM64
engine, build the C++ JNI importer with Android NDK CMake, package an unsigned APK, and verify
native library alignment. Then follow `TERMUX-SIGNING.md` to create a private signing key and
sign the APK locally.

## Verification status

Host C++ importer tests and a host CMake/JNI build pass. XML, TOML, YAML and shell syntax checks
pass. The full Gradle/Android NDK/Rust build could not run in the current environment because
Gradle's distribution server could not be resolved and the Android SDK/NDK and Rust toolchain
are not installed here. A real Tsukihime package import and gameplay test remain pending.
