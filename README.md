# J2MEX

J2MEX is an Android Java ME emulator that runs original MIDlet `.jar` files
directly, without converting them to Android DEX. It uses the FreeJ2ME
emulation engine, built and optimized for Android, alongside an Android host
for display, audio, storage, and input.

## Features

- **Run original JARs directly.** Import MIDlet `.jar` files and run them with
  the FreeJ2ME-based engine; no source changes or JAR-to-DEX conversion are
  required.
- **Import games in batches or from ZIP files.** Select multiple JARs at once,
  or choose a ZIP archive and let J2MEX find and import its contained JARs;
  manual extraction is not needed.
- **Organize a game library.** Add games to collections, keep each game's saved
  data separate, and retain the original game files.
- **Tune the display per game.** Set virtual canvas dimensions, scaling,
  orientation, status-bar behavior, and other display options.
- **Choose touch controls.** Use the virtual gamepad, select or create layouts,
  and configure pointer-based touch support separately.
- **Map physical inputs.** Assign keyboard or controller keys to game actions
  for each game, alongside the on-screen controls.
- **Use Android-native support.** Enter text with the Android keyboard and use
  supported LCDUI compatibility screens.
- **Play with common Java ME APIs.** The Android build supports core MIDP
  graphics and input, RMS game storage, audio playback, and network features;
  compatibility varies by game and API.
- **Capture and diagnose.** View FPS, take screenshots, and export the latest
  runtime log when troubleshooting.

Compatibility depends on each MIDlet and its use of optional Java ME APIs.
J2MEX currently packages the `arm64-v8a` runtime and targets Android API 21
or later; 32-bit Android devices are not supported.

## Repository layout

```text
android-host/    Android app, JNI host, runtime bridge, build scripts and tests
src/             FreeJ2ME engine and Java ME API implementation
resources/       Engine resources
```

The Android implementation and build details are documented in
[`android-host/README.md`](android-host/README.md). UI and compatibility notes
are in the Markdown documents under `android-host/`.

## Build

The Android build uses JDK 21, Android SDK 34, and Android NDK 28.2.13676358.
The embedded Java runtime and its FreeType library are local build inputs and
are deliberately not stored in this repository. The runtime used by the
release is based on OpenJDK 8u482 with Android patches; its exact source and
build references are listed in the bundled
[`NOTICE.txt`](android-host/app/src/main/assets/NOTICE.txt). Provide them with
`RUNTIME_TAR` and `FREETYPE_SO` when the runtime has not already been unpacked
under `android-host/build/runtime`.

```sh
cd android-host
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
export RUNTIME_TAR=/path/to/android-arm64-jre8.tar.xz
export FREETYPE_SO=/path/to/android-arm64/libfreetype.so
bash build-local.sh
```

The script builds the engine assets, runs the available host-side checks, and
assembles a debug-signed APK. See `android-host/README.md` for runtime
preparation, device limitations, and additional test guidance.

## Third-party notices

FreeJ2ME and other incorporated components retain their applicable licenses.
See the root [`LICENSE`](LICENSE), `android-host/app/src/main/assets/NOTICE.txt`,
and the notices in `android-host/app/src/main/assets/licenses/`. Review the
source and binary redistribution terms before preparing a public release. The
NOTICE identifies the exact OpenJDK tag and Android patch/build commit used by
the embedded runtime.

Only use MIDlet files from sources you trust.
