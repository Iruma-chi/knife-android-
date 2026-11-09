# TsukiPort 0.3.0-dev — source audit

**Audit date:** 2026-11-09  
**Status:** Source integration prepared; Android APK build and real-game validation pending.

## Input archives

| Archive | SHA-256 |
|---|---|
| User's working baseline `knife-android--0.1.5.zip` | `843f40cb0a062ba4b5019aa3748b4cbac6e1d5d65ad64540586932b9a8ef45ba` |
| Supplied P5R reference `p5r-android-0.5.6.zip` | `5a4adbbe45d3d5539ec41c550e5efeec5ed07390cb68c9981bd4f43be0e2450d` |

The new source was rebuilt from the user's `knife-android--0.1.5.zip`, not from the earlier TsukiPort 0.2.x snapshots.

## Application identity and signing

- Display name: `TsukiPort`
- Application ID: `com.dev.tsukihimeandroid.dev`
- Gradle namespace: `com.dev.tsukihimeandroid` (unchanged)
- Version: `0.3.0-dev`, versionCode 6
- ABI: `arm64-v8a`
- Release build: unsigned
- No keystore, private key, signing password, `release.properties`, prod.keys, title.keys, NSP or XCI is included.
- The release APK is intended to be signed locally by the user in Termux. See `TERMUX-SIGNING.md`.

## Gameplay-preservation comparison

The following source files are byte-for-byte identical to the supplied baseline:

| Protected file set | Result |
|---|---|
| `GameActivity.kt` | Identical; SHA-256 `747e9d00e1907de81007eba233fe93f703cf56f5237328296aeb02b1d5f938e2` |
| `SaveBackupManager.kt` | Identical; SHA-256 `57e7ff482deba041ab72fd7d48d72ca9f1c24e57a5f6c1a9fa0264fe0de0c9f6` |
| All files under `engine/` | 140 baseline files, 0 differences, 0 added files |

This preserves the existing in-game controls, touch mappings, backlog arrows and repeat behaviour, opacity controls, save/load bridge, save-backup manager, Knife Rust code, WGPU renderer and WGSL shaders. Only the launcher and build/import integration were changed.

## Importer architecture

The launcher now supports:
1. One base NSP/XCI, or all parts of one split XCI.
2. Optionally one matching update package (or all parts of its split XCI).
3. `prod.keys`, with optional `title.keys`.
4. The separately supplied Switch shared font `nintendo_udsg-r_std_003.bfttf`.

The native importer reads the selected packages through seekable Android Storage Access Framework file descriptors. It does not copy or modify the original package or key files. It decrypts and verifies Switch content, applies a matching BKTR update when one is supplied, extracts RomFS to a private staging directory, and performs a second verify-only pass against its CRC journal.

Before activating the new import, the launcher validates the detected build's core archive pair, script files, sound archive and BFTTF font. Only then does it switch the staging directory into `filesDir/tsukihime-import` and write the existing `filesDir/assets-path.txt` handoff expected by Knife.

The generic C++ importer's `apply_update` option defaults to false; the Android JNI bridge explicitly enables it. The UI permits only a base package and at most one matching update package. DLC and optional Chinese-language system-font selection are not supported in this milestone.

### P5R comparison and architecture boundary

The supplied P5R source contains both a direct package-backed game-image interface and a separate import/extraction path. P5R's actual Android runtime is a much larger system involving its own NCE loader, Switch services, NVN/Vulkan implementation and Maxwell shader translation to SPIR-V. We are not transplanting that runtime.

This integration uses the P5R project's MIT-licensed C++ package importer/extraction code as a data-preparation adapter. That is deliberate: Knife currently consumes ordinary filesystem paths for Tsukihime archives and scripts. A no-extraction, direct-RomFS runtime would require a separate virtual-filesystem abstraction inside Knife.

Knife keeps its own WGPU/WGSL renderer. No Maxwell-to-SPIR-V translation is required because this port is not executing the original Switch GPU command stream or Switch executable.

The imported P5R C++ files are in `app/src/main/cpp/nximport/`; the original MIT license and attribution are in `THIRD_PARTY_NOTICES/`.

## Build configuration cross-check

- Android Gradle Plugin: 9.3.1
- Kotlin Gradle Plugin: 2.4.21
- Gradle wrapper / CI Gradle: 9.6.1
- Compile/target SDK: 36
- NDK: 28.2.13676358 (r28c)
- CMake: 3.22.1
- Minimum Android API: 31
- `externalNativeBuild` compiles `libnximport.so`; Cargo-ndk builds the existing Rust `libmain.so`.
- The CI workflow uses explicit 16 KiB linker flags for the ARM64 Rust/C++ libraries and checks every packaged ARM64 ELF PT_LOAD alignment. It also runs `zipalign` verification.
- No release signing secrets are required by CI.

The Kotlin/AGP/Gradle versions were cross-checked against the official compatibility table. The Android SAF and CMake configuration were cross-checked against Android developer documentation. NDK r28 and higher enable 16 KiB page-size compatibility by default.

## Tests actually run in this environment

| Test | Result |
|---|---|
| P5R-derived host importer tests | PASS — 13 synthetic NSP/XCI parser cases plus default update-policy check |
| Host CMake build of JNI + importer | PASS — built and linked with JDK JNI headers and a stub Android log header/library |
| JNI export symbol check | PASS — expected `Java_com_dev_tsukihimeandroid_NativeImporter_*` symbols present |
| 16 KiB ELF checker | PASS — synthetic host shared library linked with 16 KiB flags passed the checker |
| Android XML parsing | PASS — 13 XML files |
| Gradle version catalog and Rust TOML parsing | PASS |
| GitHub Actions workflow YAML parsing | PASS |
| Shell syntax (`bash -n`) | PASS — importer test script and logo-fetch script |
| Python syntax for ELF alignment checker | PASS |
| Kotlin delimiter scan | PASS — structural balance only; not a Kotlin compiler |
| Protected gameplay/engine comparison | PASS — zero differences |
| Secret/game-file scan | PASS — no keystore, passwords, keys or game packages in source |

### What could not be verified here

A full Android APK build was attempted with `./gradlew --no-daemon :app:assembleRelease`, but the Gradle wrapper could not resolve `services.gradle.org` (`UnknownHostException`). This environment also has no Android SDK/NDK, Rust/Cargo or Kotlin compiler installed.

Therefore:
- No APK has been produced or claimed as built.
- The Android NDK CMake build has not run; the CMake/JNI test was a host build with a stub Android logging API.
- Kotlin and Gradle DSL have not been compiler-validated here; the Android Gradle CI workflow must do that.
- Rust ARM64/WGPU compilation has not run here.
- No real Tsukihime NSP/XCI, keys or system font were available for an end-to-end import.
- Installation and gameplay on an Android device remain pending.

The host importer tests are synthetic parser tests; they do not prove successful decryption of a real game dump.

## Online references checked

- Kotlin Gradle Plugin compatibility: https://kotlinlang.org/docs/gradle-configure-project.html
- Android Gradle Plugin 9.3 release notes: https://developer.android.com/build/releases/gradle-plugin
- Android Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Android NDK 16 KiB page-size guidance: https://developer.android.com/guide/practices/page-sizes
- Android CMake / external native build: https://developer.android.com/studio/projects/gradle-external-native-builds
- Android APK signing and verification: https://developer.android.com/tools/apksigner
- Termux `apksigner` package: https://github.com/termux/termux-packages/tree/master/packages/apksigner

**Bottom line:** This is an unsigned, source-level importer integration with host C++ tests and protected gameplay files verified unchanged. It is not yet a compiled or playable Android release.
