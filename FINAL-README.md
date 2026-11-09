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

## NSP/XCI access

The launcher accepts a legally dumped base NSP/XCI, optional matching update package or split
parts, `prod.keys`, optional `title.keys`, and the Switch shared system font
`nintendo_udsg-r_std_003.bfttf`.

The source now includes a package-first route: SAF opens the selected files, JNI passes duplicated
file descriptors to the native RomFS reader, and the Rust Knife engine reads assets on demand.
Archives and scripts use random-access package reads; path-only media consumers lazily cache only
the individual requested file. The existing verified extraction importer remains available as a
fallback. No full RomFS extraction is required by the direct route.

The original NSP/XCI and key files are not modified. Keys are not stored or transmitted. DLC and
real-device update-overlay behavior remain unverified.

## Gameplay preservation

The existing Knife WGPU renderer, controls, save/load behavior and save-backup UI are retained.
There is no Maxwell shader translation or Switch executable emulation in this architecture.

## Build and signing

Run `.github/workflows/android-arm64.yml` to run host importer tests, build the Rust ARM64
engine, build the C++ JNI importer with Android NDK CMake, package an unsigned APK, and verify
native library alignment. Then follow `TERMUX-SIGNING.md` to create a private signing key and
sign the APK locally.

## Verification status

Host importer tests, strict GCC/Clang C++ checks, UBSan and a host compile of the JNI shim pass.
The pinned Rust toolchain and Android SDK/NDK are not installed in the current environment, so
Rust compilation, Android linking and APK packaging remain pending. A real Tsukihime package
import and direct-mode gameplay test also remain pending.
