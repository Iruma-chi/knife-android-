# Android port stage 2: touch routing and dependency isolation

This patch is based on upstream knife 0.9.10.

## Implemented in this stage
- Winit `WindowEvent::Touch` is routed to the existing `Ui::mouse_move`, `mouse_down`, and `mouse_up` logic.
- Interrupted/cancelled touches now clear press/drag state without being treated as a tap.
- Only the first active finger is used, matching the engine's single-finger touch model. Additional fingers are ignored.
- Cancelled Android touches are discarded without becoming accidental taps.
- Android no longer selects the desktop GStreamer video backend or `gilrs` HID gamepad backend.
- Winit's `android-native-activity` feature is enabled for the later Android entry-point integration.
- Android has a compile-time movie-decoder placeholder that reports MediaCodec is not yet implemented.

## Not completed / not verified
- This is NOT a complete Android APK or complete Android port. The Android NativeActivity entry point, JNI/Kotlin launcher bridge, assets path wiring, MediaCodec decoder, Android app packaging, and actual device test are still outstanding.
- Physical Bluetooth/gamepad polling is disabled in this stage. Android keyboard events still follow Winit's existing key mapping where delivered.
- Android story movies will fail until the MediaCodec decoder is implemented. The ffmpeg fallback is not bundled and should not be assumed available.
- No Cargo or Android NDK toolchain was available in the preparation environment, so this patch has not been compiled.

## User game data layout planned for the Android app
The app should provide a folder picker for the user's already-extracted RomFS directory (including the shared system font file at its root), then copy its contents into the app's private `files/assets/` directory. This preserves the engine's expected `assets` folder name without requiring Shizuku or manual writes to Android/data. The native entry point must be wired to that exact absolute path before launching the engine.
