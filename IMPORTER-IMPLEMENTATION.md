# TsukiPort NSP/XCI importer integration

## Scope

This integration changes the Android launcher and game-data preparation path only. It does not
replace or patch the Knife engine, WGPU renderer, WGSL shaders, Rust game logic, GameActivity,
touch overlay, backlog controls, opacity controls, save/load UI, or save-backup manager.

The application identity for this parallel development install is:

- Display name: `TsukiPort`
- Application ID: `com.dev.tsukihimeandroid.dev`
- Gradle namespace: `com.dev.tsukihimeandroid` (kept unchanged)

## Import path

1. The user selects one legally dumped base-game `.nsp` or `.xci` and optionally one matching update package; every part of a split XCI must be selected.
2. The user selects `prod.keys` and optionally `title.keys`.
3. The user separately selects the Switch shared system font `nintendo_udsg-r_std_003.bfttf`.
   Knife already requires this file, and it is not part of the Tsukihime base-game package.
4. Android opens seekable file descriptors through the Storage Access Framework. The package
   and key files are not copied into the app.
5. The native importer validates the package, decrypts the relevant Switch content, verifies
   the RomFS integrity tree, and extracts the RomFS into a staging directory in app-private
   storage. The importer keeps its journal so an interrupted extraction can resume.
6. The launcher invokes the importer's verify-only pass to re-read extracted outputs and compare
   them against the import CRC journal.
7. The selected system font is validated and copied into the staged RomFS.
8. TsukiPort checks `allscr.mrg`, `script_text.mrg`, the detected build's `allpac` archive pair,
   the `allui` archive pair, the expected BFSAR sound archive, and the valid BFTTF font.
9. Only after validation does the launcher swap the staged import into the active data
   directory and write `filesDir/assets-path.txt`, which is the existing handoff contract
   already consumed by `android_main()` in Knife.
9. The existing GameActivity starts the unchanged Knife engine.

## Important distinctions

- This is an extraction/preparation integration, not P5R's full no-extraction runtime.
- P5R's direct-from-package path is coupled to its NCE loader, Switch service runtime, NVN
  implementation on Vulkan, and Maxwell shader translation to SPIR-V. Those parts cannot be
  transplanted into Knife as a standalone importer.
- Knife already implements the Tsukihime engine and renders through WGPU/WGSL. The Switch
  executable is not being run, and the original Maxwell shader programs are not being executed.
  Therefore P5R's Maxwell-to-SPIR-V shader translator is not part of this architecture.
- The importer is adapted from the MIT-licensed `android/wonder/importer` in
  `Brandon04Gonzalez/p5r-android`. The original MIT license is included in
  `THIRD_PARTY_NOTICES/`.
- The UI intentionally restricts the first milestone to one base-game package or one set of
  split XCI parts. The importer applies one matching BKTR update patch when present. Knife's
  engine README recommends the Japanese 1.0.3 update and notes earlier data still plays. DLC is
  not supported in this milestone.

## Key handling

- `prod.keys` and optional `title.keys` are opened as read-only descriptors.
- Key values are parsed in native memory and wiped by the upstream importer key store.
- The launcher does not copy keys into app storage, log key values, or transmit them.
- The importer source itself is MIT-licensed; no game data, firmware, keys, or copyrighted
  Tsukihime assets are included in this repository or APK.

## Storage and failure behaviour

- Original NSP/XCI files and key files are never modified.
- Extracted data is stored under `filesDir/tsukihime-import`.
- New imports are staged under `filesDir/tsukihime-import-staging`.
- The existing prepared installation remains active until the new staged import passes
  validation. Failed imports do not intentionally replace the active installation.
- A separate font file is retained under app-private `filesDir/system-fonts`.
- The importer requires seekable local file descriptors. Cloud/document providers that expose
  non-seekable streams are rejected with an explanatory error.
- The first import may require substantial free storage because it extracts RomFS. This is
  deliberate: it is the most compatible interface with Knife's current filesystem-based
  asset loader.

## Native page-size compatibility

The Android build uses NDK r28c, which enables 16 KiB page-size compatibility by default.
The CI workflow also supplies explicit 16 KiB linker flags to the Rust ARM64 library and checks
the ELF PT_LOAD alignment of every ARM64 `.so` in the final APK. This matters because the app
includes native Rust and C++ libraries; Android documents that NDK r27 and lower need explicit
linker flags, while r28 and higher enable the compatibility option by default.

## Validation performed in this environment

- The upstream host importer test suite is compiled and run with GCC; its synthetic NSP/XCI
  parser tests pass.
- The JNI bridge and importer sources are compiled and linked with host CMake using JDK JNI
  headers and a stub Android log library/header. This checks C++ syntax and JNI symbol names,
  not Android NDK ABI compatibility.
- The CMake host build exports the expected
  `Java_com_dev_tsukihimeandroid_NativeImporter_*` JNI symbols.
- Android XML, Gradle version catalog TOML, workflow YAML, and shell script syntax are checked.
- A full Android Gradle/NDK/Rust build and real-device import have not been run in this
  environment because the Android SDK/NDK, Rust toolchain, and Gradle distribution are not
  installed and network resolution for Gradle is unavailable.
