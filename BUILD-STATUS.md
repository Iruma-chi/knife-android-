# TsukiPort 0.3.0-dev build status

## Implemented in source

- Separate development application ID `com.dev.tsukihimeandroid.dev`.
- Native Android ARM64 application and Rust Knife engine.
- SAF selection for a base NSP/XCI plus an optional matching update package or split parts.
- `prod.keys`, optional `title.keys`, and separate Switch shared-font selection.
- Native C++ importer with staged extraction, CRC-journal verification and validated activation.
- Direct package-first path: SAF descriptors → JNI → Rust package adapter → package-aware asset roots.
- RomFS file stat/read and directory enumeration through the C ABI.
- Package-aware archives, scripts, fonts and file-path consumers; only individual path-required files are lazily cached.
- Existing extracted-data path remains available as fallback.
- Release signing secrets remain outside source; CI release output is unsigned.
- Android NDK r28c and CI ELF-load-segment check for 16 KiB page alignment across packaged ARM64 libraries.

## Host checks performed for the current source snapshot

- Synthetic importer/C ABI tests passed with GCC and Clang.
- Strict C++20 host test builds (`-Wall -Wextra -Wpedantic -Werror`) passed with GCC and Clang.
- GCC UBSan host tests passed.
- `package_jni.cpp` compiled with strict diagnostics against desktop JDK JNI headers using GCC and Clang.
- Full source archive integrity and static project-file checks passed; see `IMPLEMENTATION-STATUS-DIRECT-READER.md`.

## Step 4 build preparation update (2026-11-09)

- Removed the circular shared-library dependency found during final review.
- `libmain.so` owns the native package reader and C ABI; `libnximport.so` remains the standalone extraction importer; `libpackagejni.so` is the package JNI shim and links one-way to `libmain.so`.
- `GameActivity` loads the libraries in the order `main`, `nximport`, `packagejni`.
- Corrected CI signing to use the separate `ANDROID_KEY_PASSWORD` secret for the private key.
- Applied 16 KiB ELF page-alignment linker options to `libpackagejni.so`.
- Host importer suite passed (16 checks); package JNI passed GCC and Clang strict syntax checks; static dependency assertions passed.
- Local Gradle build remains blocked because the environment cannot resolve `services.gradle.org`; no APK was produced.

## Not yet verified

- Rust compilation with the pinned toolchain.
- Kotlin/Gradle compilation, Android NDK linking and APK packaging.
- Real encrypted NSP/XCI/update package behavior and BKTR overlay correctness.
- Direct-mode gameplay on the target Android device, including fonts, movies, audio, saves and controls.

Do not treat this development source as a verified playable replacement until those checks pass. Keep the known-good extraction APK installed.
