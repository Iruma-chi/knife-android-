# Tsukihime Android port — Phase 2 source update

## Changes completed in this source snapshot
- Added native backlog-state reporting from Rust to the Android overlay:
  - `Ui::android_backlog_active()` reports the real foreground engine screen.
  - Rust stores that state in an `AtomicBool`.
  - JNI getter `nativeIsBacklogActive()` exposes the state to Kotlin using a one-byte return matching JNI `jboolean`.
  - Kotlin polls the getter and only displays the two backlog navigation arrows while the engine backlog is active.
- Added backlog arrow actions routed through the existing native event queue:
  - previous/older backlog page -> `Pad::Up`
  - next/newer backlog page -> `Pad::Down`
- Existing game controls remain otherwise unchanged.
- Launcher save import/export remains in the launcher only.
- Save data remains in the app-private `files/knife/...` profile paths; no Downloads or shared-storage save relocation.
- Import is profile/size/hash checked, staged, atomically committed, and keeps a rollback copy.

## Static checks actually run in this environment
- All 13 XML files parsed successfully.
- GitHub Actions workflow YAML parsed successfully.
- Source-level assertions passed for:
  - native state flag and JNI getter names
  - JNI action IDs 13/14
  - engine backlog-state getter
  - backlog-only visibility condition
  - launcher document-picker registration
  - profile IDs, SHA-256 check, AtomicFile replacement, rollback file
  - no save-path relocation to Downloads

## Build checks attempted but BLOCKED (not passed)
- `./gradlew :app:compileDebugKotlin :app:processDebugResources --no-daemon`
  - Failed before Gradle configuration because the wrapper could not resolve `services.gradle.org` (`UnknownHostException`).
- This environment has no installed Android SDK/NDK, Gradle distribution, Rust toolchain, Cargo or Kotlin compiler.
- Therefore the Android Gradle build, ARM64 Rust build, APK packaging, JNI link verification and device runtime tests have NOT run.
- Do not treat this source snapshot as a verified installable APK.

## Next required verification
Run the repository's GitHub Actions `android-arm64.yml` workflow in a network-enabled runner with the configured signing secrets. Then install the resulting APK over the prior stable-signed version and test:
1. Story screen: backlog arrows absent.
2. Open backlog via touch, game menu and any native route: arrows appear.
3. Close backlog with Back / jump / controller: arrows disappear.
4. Arrows navigate older/newer entries correctly.
5. Export a save, import it back, verify continue/load.
6. Reject a backup from a different profile or with a modified checksum.
7. Confirm old save remains recoverable after a failed import.


## Final visual asset update
- Replaced the earlier generated launcher background with the exact user-uploaded `launcher_background.png` (892x1536 RGB; copied byte-for-byte).
- The background drawable is referenced only by `MainActivity` (asset-access launcher). No background resource is referenced by `GameActivity` or the Rust renderer.
- The launcher header now uses the official TSUKIHIME logo from `https://tsukihime-en.com/assets/img/main/logo.png`.
- The official logo is fetched at build time by `scripts/fetch-official-tsukihime-logo.sh`; this environment could not download the remote binary directly. The script validates the PNG and uses a small Java/AWT utility to prepare a square adaptive-icon foreground. GitHub Actions runs the script before Gradle.
- Launcher app name remains `Tsukihime Android port`; version remains `0.1.5` / versionCode 5.
- Backlog arrows are now vertically stacked on the right edge, arrow glyphs only, with transparent backgrounds. They repeat while held and are visible only when the native backlog state is active.
- Added a separate settings checkbox (`backlog_arrows`) and opacity slider (`backlog_arrow_opacity`); existing controls retain their existing settings and actions.

## Additional checks for this visual update
- User background PNG checksum checked against the uploaded file.
- Launcher background resource reference appears only in `MainActivity`.
- Launcher header and adaptive icon both reference the fetched official logo asset.
- Shell script syntax checked with `bash -n`.
- Java icon-preparation helper compiled with `javac` and executed in headless mode against a test PNG, producing a valid 432x432 PNG.
- Full Android Gradle and ARM64 Rust builds still require the network-enabled GitHub Actions runner.

## Final repository packaging correction
- The user-provided PNG is installed as `drawable-nodpi/launcher_background.png`, matching `R.drawable.launcher_background` in MainActivity.
- Workflow copies the built APK to `Tsukihime-Android-port-0.1.5.apk` before uploading, so the downloaded APK filename is friendly instead of `app-debug.apk`.
