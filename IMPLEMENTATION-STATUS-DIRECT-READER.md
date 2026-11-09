# Direct NSP/XCI reader — implementation status

Source baseline: TsukiPort source snapshot from 2026-11-09, continued locally for direct package access.

## Steps 1–3 implemented in source (not yet Android-build verified)

### Step 1 — Android SAF descriptor handoff

- The launcher persists selected game/split-part and key-file SAF URIs.
- Direct launch passes the selected package URIs and parser-facing names (`prod.keys`, optional `title.keys`) to `GameActivity`.
- `GameActivity` opens seekable read-only `ParcelFileDescriptor`s before calling `super.onCreate`, so the package is installed before the GameActivity/Winit native thread starts.
- The JNI shim calls the Rust `knife_package_install` C ABI. The native reader duplicates the descriptors synchronously; Kotlin closes its descriptors after the call.
- The package slot is cleared before each launch/open, so a failed open cannot silently reuse a previous package.

### Step 2 — reader startup and asset-root connection

- `engine/knife/build.rs` compiles the native image reader and C ABI into `libmain.so`. The package JNI shim is a separate `libpackagejni.so` target which links one-way against `libmain.so`.
- The Rust `PackageImage` owns the native handle and exposes RomFS file reads through `ReadAt`.
- The C ABI exposes file stat/read and immediate-child directory enumeration. Font lookup therefore does not assume an extracted directory tree.
- `knife_package_install` stores the opened image in a process-wide slot. Android startup passes that package into `Assets::Roots` and detects the game build from the package.
- Package lookup precedence is mod files, package RomFS, then the extracted fallback directory. The shared Switch system font remains separately selected by the user and is copied into the fallback root when package-only launch is used.

### Step 3 — remaining asset consumers and compatibility

- Archive metadata and `.mrg` payloads use the random-access provider; the full game RomFS is not extracted by the direct launch path.
- Font folder enumeration uses the RomFS directory-list API. Font, exfont, shared-font, script and archive reads are package-aware.
- Existing consumers that require a filesystem path (platform movie readers and current audio stream paths) lazily materialize only the requested file into a package-session-specific cache under app-private storage. The cache is written to a temporary file and renamed only after a complete read.
- Existing extracted data remains a fallback. The original extraction importer and its verification flow were not removed.

## Verification performed in this environment

- Synthetic host importer/C ABI tests: passed with GCC and Clang.
- Strict C++20 host builds (`-Wall -Wextra -Wpedantic -Werror`): passed with GCC and Clang.
- GCC UndefinedBehaviorSanitizer host test run: passed.
- New `package_jni.cpp` compiled as a standalone C++ translation unit with strict warnings against installed JDK JNI headers using GCC and Clang.
- C ABI null-handle and directory-list null-argument checks are included in the host test source.
- ZIP integrity, source-tree whitespace, XML/TOML/YAML/shell and Python syntax checks are included in the final audit record.

## Verification limits — do not treat these as passed

- No `cargo`, `rustc`, `rustfmt`, Android SDK/NDK or Android platform build is available in this environment. Rust compilation, Kotlin/Gradle compilation and Android linking have **not** been run. Rust changes were manually reviewed only.
- The JNI shim was host-compiled against desktop JDK headers, not linked into an Android `libmain.so`.
- No real encrypted Tsukihime base/update package was available to exercise the complete SAF → JNI → Rust → BKTR → Knife path. Synthetic host tests validate container parsing and split-image handling, not decrypted game content or update overlay correctness.
- No device test has confirmed SAF provider compatibility, font loading, audio/video playback, saves, controls or fallback behavior in direct mode.

## Compatibility decision

Keep the currently working extraction APK installed. The source now contains a package-first launch path, but it is not a verified replacement until the Rust/Android build succeeds and direct mode is tested on the device with the user's legally dumped game and keys.


## Step 4 build-preparation findings and correction (2026-11-09)

A final dependency review found that the earlier proposed arrangement was circular: the package JNI shim was placed in `libnximport.so` and that library linked to `libmain.so`, while the Rust `libmain.so` also contained the native package reader implementation. The arrangement has been corrected before any GitHub build:

- `libmain.so` contains Rust plus `nximport.cpp`, `nxcrypto.cpp`, and `nxgame_c.cpp` through `engine/knife/build.rs`.
- `libnximport.so` contains only the extraction importer JNI and importer implementation; it has no dependency on `libmain.so`.
- `libpackagejni.so` contains `package_jni.cpp` and has a one-way dependency on `libmain.so` for `knife_package_install` / `knife_package_clear`.
- `GameActivity` loads `main`, `nximport`, then `packagejni` before invoking package JNI methods.
- The signing workflow now uses `ANDROID_KEYSTORE_PASSWORD` for the keystore and `ANDROID_KEY_PASSWORD` for the private key.
- `libpackagejni.so` receives the same ARM64 and 16 KiB ELF page-alignment linker settings as the other native libraries.

The host importer suite passed (16 checks). The package JNI translation unit passed GCC and Clang C++20 syntax checks with warnings-as-errors against desktop JDK JNI headers. Static dependency/workflow assertions and whitespace checks passed. No Rust compilation, Android NDK link, Gradle APK build, signing, or device test has been run in this environment.
