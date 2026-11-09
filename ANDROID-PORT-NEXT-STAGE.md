# TsukiPort next-stage integration status

The previous extracted-RomFS folder picker has been replaced in source by a native NSP/XCI
import-and-prepare flow. The architecture and remaining validation requirements are documented
in `IMPORTER-IMPLEMENTATION.md` and `BUILD-STATUS.md`.

## What changed

- Launcher: package, keys and shared-font selection through Android's Storage Access Framework.
- Native importer: P5R project's MIT-licensed C++ importer, JNI bridge renamed for TsukiPort.
- Data preparation: verified RomFS extraction into a staging directory, followed by validation
  and activation only after success.
- App identity: display name TsukiPort, application ID `com.dev.tsukihimeandroid.dev`,
  namespace unchanged as `com.dev.tsukihimeandroid`.
- Signing: no keystore, passwords, or release signing secrets in source; CI emits an unsigned
  release APK for local signing through Termux.

## Explicitly unchanged

`GameActivity.kt`, `SaveBackupManager.kt`, and all Rust `engine/` files are byte-for-byte
unchanged from the user's supplied `knife-android-0.1.5.zip`. This preserves the existing
controls, backlog arrows, opacity controls, save/load features, game interpreter and WGPU/WGSL
renderer.

## Important architecture boundary

P5R's no-extraction workflow is not just a generic package reader. It depends on its own NCE
loader for the Switch ARM64 executable, Switch OS service implementations, NVN graphics API
implementation on Vulkan, and Maxwell shader translation to SPIR-V. Knife is a
Tsukihime-specific engine reimplementation with its own WGPU/WGSL renderer and filesystem-based
asset loader. This project uses the P5R importer to decrypt, verify and extract RomFS; it does
not add P5R's NCE or GPU runtime.

## Validation

Host importer parser tests pass. The JNI bridge and importer compile/link in a host CMake build
using JDK JNI headers and stub Android logging. Android NDK compilation, Gradle packaging,
Rust ARM64 build and real-device import/gameplay tests are pending.
