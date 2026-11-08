# Android controls implementation (Build #16 source baseline)

This patch moves the native host from Android `NativeActivity` to AndroidX `GameActivity` so the Kotlin control overlay can be a real Android `View` layered above the game's `SurfaceView`. Winit remains the window/input library and Knife remains the owner of game actions.

## Why GameActivity is used

Winit 0.30.13 supports both `android-native-activity` and `android-game-activity`. Android's GameActivity hosts rendering in a `SurfaceView`, allowing normal Android views to be layered above the game surface. The earlier draft's attempt to overlay ordinary Android views on the NativeActivity host was not the right integration architecture and is discarded.

The selected `androidx.games:games-activity:4.4.0` dependency matches the compatibility documented by the locked `android-activity` 0.6.1 glue crate. Do not enable Gradle Prefab or link the upstream GameActivity C/C++ glue: `android-activity` supplies the Rust-compatible native glue itself.

## Controls and action routing

The Kotlin overlay calls a JNI method exported by `libmain.so`. Rust converts the action ID into an `AndroidControlEvent`, queues it through Winit's `EventLoopProxy`, and handles it on the Winit/game thread. This avoids synthesizing Android keyboard events and avoids touching game state from the UI thread.

- `NEXT` -> `Pad::A` (advance/confirm)
- `BACK` -> `Pad::B` (cancel/back)
- `BACKLOG` -> `Pad::X` (open backlog)
- `AUTO` -> `Pad::L` (toggle autoplay)
- `SKIP` -> `Pad::R` (toggle skip)
- `SAVE` -> Knife quick-save
- `LOAD` -> Knife quick-load
- `MENU` -> a themed Android mini-menu

The mini-menu provides Resume, Back, Save Screen, Load Screen, Configuration, Return to Title and Exit Game. Save Screen/Load Screen invoke the original Knife UI actions; the on-screen SAVE/LOAD buttons are quick-save/quick-load. Return to Title and Exit Game require confirmation.

The bridge buffers actions if the event loop or game UI has not finished initializing. It drains them onto the Winit thread once ready.

## Visual treatment

Controls use a restrained moonlit-oxblood treatment: ink-dark translucent gradient, warm ivory serif lettering, a muted silver/gold hairline border and a restrained crimson pressed state. This is deliberately quieter than generic bright gamepad buttons so the controls don't compete with the original visual novel artwork.

## Controls settings

The gear opens a separate `Controls` section with:

- master on-screen controls enable/disable;
- individual button visibility;
- opacity (20-100%);
- size (44-88 dp);
- long-press-and-drag positioning, saved per button;
- reset button positions;
- in-game swipe/flick gesture toggle;
- Android system Back gesture/button toggle.

The settings gear stays available while the gameplay overlay is disabled, so the user can re-enable it. Disabling in-game gestures leaves direct taps and normal list scrolling intact. Disabling Android system Back prevents it from closing the game; the visible BACK button remains available if enabled.

## Build path

The existing workflow remains the build entry point:

1. Cargo builds the ARM64 `knife` cdylib with Winit's `android-game-activity` feature.
2. Gradle resolves `androidx.games:games-activity:4.4.0` from Google's Maven repository.
3. Gradle packages `libmain.so`, the GameActivity AAR and the Kotlin launcher/overlay into the debug APK.

The Cargo lockfile records the `android-activity` 0.6.1 `game-activity` feature dependency. No CMake, Prefab, new native C++ target or Rust dependency version change is introduced.

## Validation status

Static cross-checks performed on this source patch:

- Android manifest parses as XML.
- MainActivity launches the declared GameActivity subclass.
- Manifest retains `android.app.lib_name=main`.
- Winit's `android-game-activity` feature is paired with the documented GameActivity 4.4.0 AAR for android-activity 0.6.1.
- The Winit custom-event API is used with `EventLoop::with_user_event`, `EventLoopProxy::send_event` and `ApplicationHandler::user_event`.
- The Kotlin native method compiles to the JNI name/signature expected by the Rust export (`nativeControl(int)`); `@Keep` protects it from shrinking/obfuscation.
- No Gradle Prefab configuration is enabled.
- Kotlin compiler parsing produced no syntax diagnostics; a full Kotlin compile could not run because this environment has no Android SDK/AndroidX classpath.
- Button action IDs, Rust JNI match arms and existing Knife actions were cross-checked.

**Not yet verified:** Rust compilation, full Kotlin/Android compilation, Gradle APK packaging and runtime overlay behaviour on the phone. This environment does not contain Cargo/Rust, Gradle or an Android SDK. The GitHub Actions workflow must perform the actual compile before the APK is installed. Do not describe this source patch as a compiled or device-tested APK.
