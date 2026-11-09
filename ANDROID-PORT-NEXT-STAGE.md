# TsukiPort Android architecture and validation status

TsukiPort uses the Tsukihime-specific Knife engine, with a native NSP/XCI reader as an additional package-first asset source. It is not a Nintendo Switch emulator and does not use P5R's NCE/Maxwell runtime.

## Data paths

- **Direct package path:** Android SAF opens the selected base NSP/XCI, optional matching update parts, and local key files. JNI hands seekable descriptors to the Rust engine's native C++ RomFS reader. RomFS files are read on demand.
- **Extracted fallback:** the existing importer still decrypts, verifies and extracts RomFS to app-private storage. It remains available as a fallback and for diagnosis.
- **Writable data:** saves, configuration and backups remain in app-private writable storage; they are not written into NSP/XCI files.
- **External media/path consumers:** only individual files that require a filesystem path are materialized into a private cache. The full RomFS is not copied by direct package mode.

## Implemented in source

- SAF selection and persisted URI grants for game parts and keys.
- JNI descriptor handoff before GameActivity starts the Winit native thread.
- Native package open/clear C ABI and package lifetime management.
- Rust `PackageImage` adapter and package-aware `Roots`.
- RomFS directory enumeration for language font discovery.
- Package-aware archive/script/font/audio/movie asset access with lazy path cache where required.
- Existing extraction importer and extraction fallback retained.

## Validation still required

- Compile Rust with the repository's pinned toolchain.
- Build and link the Android ARM64 APK with NDK/Gradle.
- Test base NSP, XCI, split XCI, matching update overlay, and a missing/invalid key case.
- Test language font enumeration, opening/reading scenes and archives, movie/audio playback, save/load, controls and returning to the launcher.
- Compare package-first results against the already-working extracted build before making direct mode the default release path.
