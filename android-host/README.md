# Android host

This module provides J2MEX's Android launcher, runtime process, native input and
display layers, and JNI entry point for the bundled Java 8 ARM64 runtime.

## Architecture

- Android imports MIDlet JARs through the system file picker and keeps per-game
  settings and RMS data in app-managed storage.
- A separate `:runtime` process starts the embedded JVM and the FreeJ2ME engine.
- The engine's framebuffer and Android text/font bridge use authenticated
  loopback connections scoped to a game session.
- Android scales and presents game frames, forwards touch and physical input,
  and provides native text entry and supported LCDUI screens.
- Runtime output and startup failures are recorded in the latest-run log.

The runtime package contains the Java class library, headless AWT components,
font libraries, TLS support, and locale data. The desktop launcher, X11 bridge,
Nashorn, and Java Flight Recorder are omitted. The full class library remains
because arbitrary MIDlets may load Java classes dynamically.

## Build prerequisites

- JDK 21
- Android SDK Platform 34
- Android NDK 28.2.13676358
- Android ARM64 Java 8 runtime archive and compatible ARM64 FreeType library

Set `JAVA_HOME`, `ANDROID_HOME`, and optionally `ANDROID_NDK_HOME`. If the
runtime has not already been prepared under `build/runtime`, also set
`RUNTIME_TAR` and `FREETYPE_SO`:

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
export RUNTIME_TAR=/path/to/android-arm64-jre8.tar.xz
export FREETYPE_SO=/path/to/android-arm64/libfreetype.so
bash build-local.sh
```

The script compiles the engine and bridge JARs, builds the native host library,
runs host-side compatibility checks, packages the runtime, and assembles a
debug-signed APK at `app/build/outputs/apk/debug/app-debug.apk`.

Runtime archives, generated JARs, native build outputs, and APKs are not checked
into the repository. The JRE used for the release is based on OpenJDK tag
`jdk8u482-ga` with Android patches from the build repository revision listed in
`app/src/main/assets/NOTICE.txt`. Use those source references when preparing or
replacing the local runtime inputs.

## Compatibility and verification

- Minimum Android API: 21 (Android 5.0).
- Packaged ABI: `arm64-v8a` only; 32-bit Android is unsupported.
- Android 11+ native heap pointer tagging is disabled for compatibility with
  the legacy embedded HotSpot 8 runtime. Do not remove this manifest setting
  without validating JVM startup on device.
- API 21 is the declared install floor, not a claim that every API 21 device has
  been tested. Verify installation, first-run runtime extraction, game launch,
  networking, audio, and clean shutdown on physical devices before distribution.

`build-local.sh` runs the available JVM, graphics, input, networking, storage,
and UI smoke checks. Host-side tests do not replace Android device testing.
The standalone framebuffer protocol test requires a working Android font bridge.

## Release signing

Release APKs are signed with `keys/j2mex-release.p12` and the alias
`j2mex-release`. The keystore is intentionally excluded from version control.
Keep an offline backup of the keystore and its password; losing either prevents
users from installing future updates over this release.

Set `J2MEX_RELEASE_KEYSTORE_PASSWORD` through a secret manager, then build from
this directory with an incremented version code and the release version name:

```sh
./gradlew :app:assembleRelease \
  -PbuildVersionCode=263 \
  -PbuildVersionName=0.1.0
```

The release keystore is not included in this repository. A release build must
fail rather than fall back to the debug signing key.

## License notes

See `app/src/main/assets/NOTICE.txt` and `app/src/main/assets/licenses/` for
component notices. Review the applicable source and binary licenses before
creating a public build or release.
