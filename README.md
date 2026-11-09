# TsukiPort 0.3.0-dev

🌙 **Tsukihime — Android Port**

An independent ARM64 Android port built around the Tsukihime-specific Knife engine.

TsukiPort adds a native package-first NSP/XCI asset path while retaining the existing extraction importer as a fallback and preserving the Knife gameplay engine and Android in-game controls.

## Project Highlights

- Native Android ARM64 application.
- Rust-powered Knife engine.
- Customizable touchscreen controls.
- Backlog navigation and opacity settings.
- Save management and backup support.
- Native NSP/XCI package importer.
- Optional matching game update support.

## Architecture

- Select a legally dumped base-game NSP/XCI and optional matching update.
- Select your own `prod.keys` and optional `title.keys`.
- Select the required Switch shared system font.
- Direct mode passes SAF file descriptors to the native C++ RomFS reader and serves game assets to Knife on demand.
- The extraction importer remains available as a fallback and diagnostic route.
- Only individual files that require filesystem paths are lazily cached; direct mode does not extract the full RomFS.

This is not a general-purpose Nintendo Switch emulator. Knife remains the Tsukihime-specific engine and WGPU renderer.

## Installation Requirements

You need:

- Your own legally dumped Tsukihime Switch base-game NSP/XCI.
- Your own `prod.keys` and, if required by your dump, `title.keys`.
- The Switch shared system font:
  `nintendo_udsg-r_std_003.bfttf`
- An ARM64 Android device.

A matching official update package is optional. DLC is not supported in this development milestone.

No game ROMs, firmware, keys or copyrighted game assets are included.

## Build and Signing

The GitHub Actions workflow builds the ARM64 Rust engine, native C++ importer and unsigned Android APK.

Builds are manually triggered through GitHub Actions.

The APK is signed separately using the developer's private signing key. See `TERMUX-SIGNING.md`.

Application ID: `com.dev.tsukihimeandroid.dev`

Gradle namespace: `com.dev.tsukihimeandroid`

## Project Status

This is an experimental development build.

Host importer tests, strict GCC/Clang C++ checks, UBSan and a host compile of the JNI shim have passed. Rust compilation, Android NDK/Gradle packaging and real-device direct-package testing are still pending.

Do not consider this a verified playable release yet.

## Credits and Acknowledgements

- [AuroraWright](https://github.com/AuroraWright) — Original Knife engine and foundational work.
- Type-Moon — Creators of Tsukihime -A piece of blue glass moon-.
- Persona 5 Royal Android importer contributors — MIT-licensed importer components used in this project. See `THIRD_PARTY_NOTICES`.

## Development Disclosure

This project was developed through independent coding, technical decisions, experimentation and implementation, with AI tools assisting with coding support, debugging, research and refinement.

The integration, direction and development decisions remain part of this project's work.

---

An independent community project. Not affiliated with or endorsed by Type-Moon.
