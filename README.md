# J2MEX

J2MEX is an Android application for running classic Java ME (J2ME/MIDP) games.
It combines the FreeJ2ME emulator engine with an Android host, an embedded
ARM64 Java runtime, and touch and physical-input controls.

## Features

- Import and organize MIDlet `.jar` files into game collections.
- Configure display, audio, touch controls, gamepad layouts, and physical-key
  mappings per game.
- Render the game framebuffer in a resizable Android surface and support
  portrait and landscape play.
- Provide Android-native text entry and LCDUI screens where supported.
- Keep game data separate and export the latest runtime log for troubleshooting.

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
are deliberately not stored in this repository. Provide them with
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
source and binary redistribution terms before preparing a public release.

Only use MIDlet files from sources you trust.
