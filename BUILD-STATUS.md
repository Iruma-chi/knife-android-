# TsukiPort 0.3.0-dev build status

## Implemented in source

- Separate development application ID `com.dev.tsukihimeandroid.dev`.
- Display name `TsukiPort`; namespace remains `com.dev.tsukihimeandroid`.
- Removed the old all-files-access / extracted-folder selection workflow.
- Added SAF selection for a base NSP/XCI plus an optional matching update package or split parts.
- Added `prod.keys`, optional `title.keys`, and separate BFTTF system-font selection.
- Integrated the MIT-licensed P5R NSP/XCI importer extraction path behind a TsukiPort JNI bridge.
- Added staging extraction, a separate CRC-journal verify-only pass, and validation of scripts,
  core archives, sound archive and BFTTF font before switching the active RomFS path.
- Kept `GameActivity.kt`, `SaveBackupManager.kt`, all Rust engine source, and existing in-game
  controls byte-for-byte identical to the supplied `knife-android-0.1.5.zip`.
- Removed all release keystore/secrets from the build configuration. Release output is unsigned.
- Updated to Android NDK r28c and added a CI ELF-load-segment check for 16 KiB page alignment
  across all packaged ARM64 native libraries.

## Tests run

- `scripts/test-nximport-host.sh`: PASS (13 synthetic parser cases).
- Host CMake build of JNI + importer: PASS (using JDK JNI headers and stub `android/log.h`/
  `liblog`; not an Android NDK build).
- JNI symbol export check: PASS.
- XML/TOML/YAML/shell syntax checks: see the accompanying audit report.

## Not yet tested

- Android NDK compilation and APK packaging.
- Rust ARM64/WGPU engine build in this environment.
- SAF file descriptor behaviour on the target Android device.
- Importing a real legally dumped Tsukihime NSP/XCI.
- Actual game launch and gameplay regression tests.

Do not treat this development source as a playable release until CI and real-device tests pass.
