# TsukiPort direct-reader source audit — 2026-11-09

## Scope

Audited the source delta from `tsuki-direct-reader-stages-1-7.zip`. The delta is limited to the direct-reader JNI/SAF handoff, native directory-list C ABI, Rust package/asset integration, host tests, and status/readme documentation.

## Cross-layer contract checks

- Kotlin sends `package_uris` and `package_names` arrays of equal length.
- Each package URI is opened read-only and checked for a positive size and seek support before its descriptor is passed to JNI.
- JNI validates array lengths and descriptor values, maps each name/descriptor pair to the C ABI input layout, clears any old package first, and returns a bounded diagnostic on native open failure.
- Rust `PackageInput` uses `#[repr(C)]` with `const char*`/`int32_t`-compatible fields and copies the UTF-8 names before the JNI-owned strings expire.
- `PackageImage::open` calls the native C ABI synchronously. The native `FdStorage` duplicates descriptors, so Kotlin can close the original `ParcelFileDescriptor`s after the call.
- The native handle is owned by `PackageImage`; a process-wide `Arc` keeps it alive through Android startup and `Roots` clones it for asset consumers.
- C ABI `nxi_game_list_count` / `nxi_game_list_name` declarations, definitions and Rust FFI signatures agree on argument order and integer/pointer widths.
- `Roots` lookup order is mods → package → extracted fallback. Archive readers use `ReadAt`; path-only media/audio consumers lazily materialize individual files under a package-session cache.
- The package cache includes a per-open nonce so a new package/update cannot reuse an old same-length cached file from a prior process.
- Android startup detects the build from the package when present and supplies the same active package to `Assets::Roots`.
- Existing extraction importer code remains present; package clear is called when launching without direct package extras to avoid stale process state.

## Automated checks run

- Synthetic C++ importer/C ABI tests: GCC — PASS.
- Synthetic C++ importer/C ABI tests: Clang — PASS.
- Strict C++20 host test builds with `-Wall -Wextra -Wpedantic -Werror`: GCC and Clang — PASS.
- GCC UndefinedBehaviorSanitizer host test run — PASS.
- JNI shim standalone C++ compilation against desktop JDK JNI headers with strict warnings: GCC and Clang — PASS.
- Python source syntax: 9 files — PASS.
- TOML parsing: 10 files — PASS.
- XML parsing: 13 files — PASS.
- Shell syntax checks — PASS.
- Changed-file trailing-whitespace/final-newline checks — PASS.
- JNI/C ABI/Rust/Kotlin handoff symbol-name and array-contract checks — PASS.
- Final source ZIP integrity and extracted file inventory — PASS.

## Not verified

- Rust source was manually reviewed but could not be compiled: the pinned Rust toolchain is not installed here.
- Kotlin/Gradle and Android NDK compilation were not run: no Android SDK/NDK is installed here.
- The JNI shim host compile is not a substitute for Android linking.
- No real encrypted Tsukihime NSP/XCI/update image was available. BKTR update overlay and direct game startup remain unverified with real game data.
- No device test was run for SAF document providers, font enumeration, movies, audio, saves, controls, or fallback behavior.

## Release recommendation

Do not replace the known-good extraction APK yet. First build this source with the pinned Rust toolchain and Android NDK, then test direct package mode on-device against the same legal game dump used by the extraction build. Compare startup, scenes, fonts, audio/video, update behavior and save/load before making it the default.


## Step 4 pre-build integration audit and correction (2026-11-09)

- [CORRECTED] The earlier CMake design introduced a circular dependency between `libmain.so` and `libnximport.so`. The package JNI shim now lives in `libpackagejni.so`, which depends one-way on `libmain.so`; `libmain.so` owns the C++ package reader and C ABI; `libnximport.so` remains the extraction importer.
- [FIXED] `GameActivity` loads `main`, `nximport`, and `packagejni` in dependency order before invoking package JNI methods.
- [FIXED] CI signing now uses the distinct `ANDROID_KEY_PASSWORD` secret for the private key.
- [PASSED] Host NSP/XCI importer suite (16 checks).
- [PASSED] GCC and Clang strict syntax checks for `package_jni.cpp` against JDK JNI headers.
- [PASSED] Static dependency-graph, workflow-secret and whitespace assertions.
- [BLOCKED] Gradle wrapper download: DNS resolution for `services.gradle.org` failed in this environment. No APK was produced.
- [NOT RUN] Rust compile, Android NDK link, APK packaging/signing, and device tests.

This is a corrected build-wiring checkpoint, not a successful APK build.
