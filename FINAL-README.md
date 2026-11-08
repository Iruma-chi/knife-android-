# Tsukihime Android port — consolidated Phase 1 + Phase 2 source

This archive consolidates the Phase 1 launcher identity/background work and Phase 2 native backlog bridge and launcher save backup work, with the project owner's supplied launcher artwork.

## Visual identity
- The asset-access launcher screen uses `app/src/main/res/drawable-nodpi/launcher_background_user.png`, copied byte-for-byte from the project owner's upload.
- Only `MainActivity` references this background. The game renderer and `GameActivity` do not.
- Launcher heading and adaptive app icon use the official TSUKIHIME logo image from the official product website.
- The official logo binary is fetched at build time by `scripts/fetch-official-tsukihime-logo.sh`. The build workflow runs this script before Gradle. For a local build, run the script from the repository root before building.
- App label: `Tsukihime Android port`.
- Version: `0.1.5`, versionCode `5`.

## Backlog controls
- Rust reports the real active backlog screen through an atomic state and JNI getter.
- Android displays two arrow-only controls vertically stacked at the right edge, only while backlog is active.
- Tap scrolls one step; holding repeats.
- Settings include a separate backlog-arrow enable checkbox and independent opacity slider.
- Existing eight controls and their existing preferences/actions are retained.

## Save backups
- Import/export is available from the launcher, not while the game is running.
- Saves remain in the engine's normal app-private `files/knife/...` profile directories; no migration to Downloads or shared storage.
- Backup ZIP contains a manifest, profile ID, expected byte size and SHA-256.
- Import rejects unexpected ZIP entries, profile mismatches, size mismatches and checksum failures.
- Existing saves are copied to a rollback file before atomic replacement.

## Build and verification status
Static checks performed for this archive:
- 13 XML resources parsed successfully.
- GitHub Actions workflow YAML parsed successfully.
- Shell download script passed `bash -n`.
- Java logo preparation helper compiled and ran headlessly against a test PNG.
- Source assertions passed for launcher-only background use, app label/version, logo fetch integration, backlog JNI bridge, right-side arrow placement, arrow settings and save backup checks.

Not yet proven in this environment:
- Full Android Gradle build and APK packaging.
- ARM64 Rust build and JNI link.
- Device runtime testing of arrows and save import/export.

The GitHub Actions `android-arm64.yml` workflow is the required full build verification. Do not uninstall the currently installed working APK until that workflow succeeds and the resulting APK installs.
