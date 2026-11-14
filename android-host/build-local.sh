#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the existing Android SDK}"
export PATH="$JAVA_HOME/bin:$PATH"
NDK="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"
rm -rf build/engine build/bridge
mkdir -p build/{engine,bridge,sample,network-test,fileconnection-test,lcdui-test,lcdui-sample,lcdui-matrix,native-input-test,clone-owner-test,clone-jar-test,canvas-visibility-test,audio-test,framebuffer-test,font-mask-test,frame-compat-test,m3g-test,micro3d-test,game-key-test} app/src/main/{assets,jniLibs/arm64-v8a}
python3 tools/test_prepare_runtime.py
if [ ! -f build/runtime/lib/aarch64/server/libjvm.so ]; then
    : "${RUNTIME_TAR:?Set RUNTIME_TAR to the Android ARM64 Java 8 archive}"
    mkdir -p build/runtime
    tar -xJf "$RUNTIME_TAR" -C build/runtime
fi
if [ ! -f build/runtime/lib/aarch64/libfreetype.so ]; then
    : "${FREETYPE_SO:?Set FREETYPE_SO to the Android ARM64 FreeType library}"
    cp "$FREETYPE_SO" build/runtime/lib/aarch64/libfreetype.so
fi
find ../src -name '*.java' | sort > build/engine-sources.txt
javac --release 8 -encoding UTF-8 -d build/engine @build/engine-sources.txt
jar cf app/src/main/assets/engine.jar -C build/engine . -C ../resources .
mkdir -p build/manifest-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/manifest-test tools/MIDletManifestPropertySmoke.java
java -cp app/src/main/assets/engine.jar:build/manifest-test MIDletManifestPropertySmoke
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/manifest-test tools/MIDletPlatformPropertySmoke.java
java -cp app/src/main/assets/engine.jar:build/manifest-test MIDletPlatformPropertySmoke
mkdir -p build/update-version-test
javac --release 8 -encoding UTF-8 -d build/update-version-test \
    app/src/main/java/org/recompile/freej2me/lite/ReleaseVersionPolicy.java \
    tools/ReleaseVersionPolicySmoke.java
java -cp build/update-version-test \
    org.recompile.freej2me.lite.ReleaseVersionPolicySmoke
mkdir -p build/storage-policy-test
javac --release 8 -encoding UTF-8 -d build/storage-policy-test \
    app/src/main/java/org/recompile/freej2me/lite/StorageAccessPolicy.java \
    tools/StorageAccessPolicySmoke.java
java -cp build/storage-policy-test org.recompile.freej2me.lite.StorageAccessPolicySmoke
python3 tools/test_storage_manifest.py
mkdir -p build/native-list-viewport-test
javac --release 8 -encoding UTF-8 -d build/native-list-viewport-test \
    app/src/main/java/org/recompile/freej2me/lite/nativeui/NativeListViewportPolicy.java \
    tools/NativeListViewportPolicySmoke.java
java -cp build/native-list-viewport-test \
    org.recompile.freej2me.lite.nativeui.NativeListViewportPolicySmoke
mkdir -p build/runtime-timing-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/runtime-timing-test tools/RuntimeTimingSmoke.java
java -cp app/src/main/assets/engine.jar:build/runtime-timing-test \
    org.recompile.mobile.RuntimeTimingSmoke
mkdir -p build/memory-editor-test/fixture-classes build/memory-editor-test/test-classes
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/memory-editor-test/fixture-classes tools/MemoryEditorFixture.java
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/memory-editor-test/test-classes tools/ManagedMemoryEditorSmoke.java
java -cp app/src/main/assets/engine.jar:build/memory-editor-test/test-classes \
    org.recompile.mobile.ManagedMemoryEditorSmoke build/memory-editor-test/fixture-classes
rm -rf build/bridge build/bluetooth-diagnostic
mkdir -p build/bridge build/bluetooth-diagnostic ../generated-artifacts
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/bridge bridge/org/recompile/android/HeadlessMain.java
jar cf app/src/main/assets/bridge.jar -C build/bridge .
javap -classpath app/src/main/assets/bridge.jar:app/src/main/assets/engine.jar \
    -c org.recompile.android.HeadlessMain > build/bluetooth-bridge-check.txt
grep -q 'BluetoothBridge.setTransport' build/bluetooth-bridge-check.txt
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/bluetooth-diagnostic sample/BluetoothDiagnosticMIDlet.java
jar cfm ../generated-artifacts/v2-bluetooth-diagnostic.jar \
    sample/BLUETOOTH-DIAGNOSTIC.MF -C build/bluetooth-diagnostic .
mkdir -p build/graphics-anchor-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar:app/src/main/assets/bridge.jar \
    -d build/graphics-anchor-test tools/PlatformGraphicsBaselineSmoke.java \
    tools/PlatformGraphicsCopyAreaSmoke.java \
    tools/DirectGraphicsAlphaSmoke.java tools/CreateRGBImageAlphaModeSmoke.java \
    tools/BluetoothOptionPresenceSmoke.java tools/SystemFontRenderingSmoke.java \
    tools/AndroidFontBridgeSmoke.java \
    tools/MIDletLocaleSmoke.java
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/graphics-anchor-test \
    org.recompile.mobile.PlatformGraphicsBaselineSmoke
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/graphics-anchor-test \
    org.recompile.mobile.PlatformGraphicsCopyAreaSmoke
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/graphics-anchor-test \
    org.recompile.mobile.SystemFontRenderingSmoke \
    "${SYSTEM_FONT_TEST_PATH:-/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf}" \
    "${SYSTEM_MONOSPACE_FONT_TEST_PATH:-/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf}"
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/graphics-anchor-test \
    org.recompile.mobile.AndroidFontBridgeSmoke
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:app/src/main/assets/bridge.jar:build/graphics-anchor-test \
    org.recompile.mobile.MIDletLocaleSmoke
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/graphics-anchor-test \
    org.recompile.mobile.DirectGraphicsAlphaSmoke
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/graphics-anchor-test \
    org.recompile.mobile.CreateRGBImageAlphaModeSmoke
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/graphics-anchor-test \
    BluetoothOptionPresenceSmoke
if [ -n "${BLUETOOTH_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/graphics-anchor-test tools/BluetoothJarLinkageSmoke.java
    java -Djava.awt.headless=true \
        -cp app/src/main/assets/engine.jar:build/graphics-anchor-test \
        BluetoothJarLinkageSmoke "$BLUETOOTH_TEST_JAR"
fi
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/m3g-test tools/M3GRendererSmoke.java tools/M3GCompositingSmoke.java \
    tools/M3GSprite3DSmoke.java tools/M3GTransformableSemanticsSmoke.java \
    tools/M3GSkinnedMeshSmoke.java tools/M3GSplineInterpolationSmoke.java \
    tools/FigureLayoutSmoke.java \
    tools/M3GVertexBufferNullPositionsSmoke.java tools/M3GLightingSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test \
    com.mascotcapsule.micro3d.v3.FigureLayoutSmoke
mkdir -p build/micro3d-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/micro3d-test tools/Micro3DModelSmoke.java tools/Micro3DTextureSmoke.java \
    tools/Micro3DRendererSmoke.java tools/Micro3DActionTableSmoke.java
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/micro3d-test \
    com.mascotcapsule.micro3d.v3.Micro3DRendererSmoke
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/micro3d-test \
    com.mascotcapsule.micro3d.v3.Micro3DActionTableSmoke
if [ -n "${MICRO3D_TEST_JAR:-}" ]; then
    java -Djava.awt.headless=true \
        -cp app/src/main/assets/engine.jar:build/micro3d-test \
        com.mascotcapsule.micro3d.v3.Micro3DModelSmoke "$MICRO3D_TEST_JAR"
    java -Djava.awt.headless=true \
        -cp app/src/main/assets/engine.jar:build/micro3d-test \
        com.mascotcapsule.micro3d.v3.Micro3DTextureSmoke "$MICRO3D_TEST_JAR"
fi
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test M3GRendererSmoke "${M3G_TEST_JAR:-}"
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test M3GVertexBufferNullPositionsSmoke
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test M3GSprite3DSmoke
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test M3GTransformableSemanticsSmoke
if [ -n "${NFS_MOST_WANTED_TEST_JAR:-}" ]; then
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test \
        javax.microedition.m3g.M3GSplineInterpolationSmoke "$NFS_MOST_WANTED_TEST_JAR"
else
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test \
        javax.microedition.m3g.M3GSplineInterpolationSmoke
fi
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/m3g-test tools/RgbBlitSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test RgbBlitSmoke
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test M3GCompositingSmoke
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test M3GLightingSmoke
if [ -n "${SKINNED3D_TEST_JAR:-}" ]; then
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test \
        javax.microedition.m3g.M3GSkinnedMeshSmoke "$SKINNED3D_TEST_JAR"
fi
if [ -n "${BATTLE3D_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/m3g-test tools/M3GLoaderSmoke.java
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test \
        M3GLoaderSmoke "$BATTLE3D_TEST_JAR" build/m3g-test
fi
if [ -n "${CITYBLOXX_TEST_JAR:-}" ] && [ -n "${SNAKE3D_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/m3g-test tools/M3GGameCompatibilitySmoke.java
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test \
        javax.microedition.m3g.M3GGameCompatibilitySmoke \
        "$CITYBLOXX_TEST_JAR" "$SNAKE3D_TEST_JAR" build/m3g-test
fi
if [ -n "${CITYBLOXX_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/m3g-test tools/CityBloxxFirstRunSmoke.java
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test \
        CityBloxxFirstRunSmoke "$CITYBLOXX_TEST_JAR"
fi
if [ -n "${M3G_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/m3g-test tools/MinecraftWorldRenderSmoke.java
    java -Xmx768m -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/m3g-test \
        MinecraftWorldRenderSmoke "$M3G_TEST_JAR" build/minecraft-world.png
fi
if [ -n "${NLTB_TEST_JAR:-}" ]; then
    mkdir -p build/nltb-test
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/nltb-test tools/NltbBytecodeSmoke.java
    java -Xverify:all -cp app/src/main/assets/engine.jar:build/nltb-test \
        NltbBytecodeSmoke "$NLTB_TEST_JAR"
fi
if [ -n "${MIDLET_CLASSLOAD_TEST_JAR:-}" ]; then
    : "${MIDLET_CLASSLOAD_TEST_CLASSES:?Set class names separated by spaces}"
    read -r -a classload_test_classes <<< "$MIDLET_CLASSLOAD_TEST_CLASSES"
    mkdir -p build/class-load-test
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/class-load-test tools/MidletLoaderClassLoadSmoke.java \
        tools/LegacyCodeAttributeVersionSmoke.java
    java -Xverify:all -Djava.awt.headless=true \
        -cp app/src/main/assets/engine.jar:build/class-load-test \
        org.recompile.mobile.MidletLoaderClassLoadSmoke \
        "$MIDLET_CLASSLOAD_TEST_JAR" "${classload_test_classes[@]}"
    java -Xverify:all -Djava.awt.headless=true \
        -cp app/src/main/assets/engine.jar:build/class-load-test \
        org.recompile.mobile.LegacyCodeAttributeVersionSmoke \
        "$MIDLET_CLASSLOAD_TEST_JAR" "${classload_test_classes[@]}"
fi
mkdir -p build/package-factory-compat-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/package-factory-compat-test tools/PackageQualifiedFactoryCompatibilitySmoke.java
java -Xverify:all -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/package-factory-compat-test \
    org.recompile.mobile.PackageQualifiedFactoryCompatibilitySmoke
if [ -n "${MISSING_STACKMAP_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/frame-compat-test tools/MissingStackMapSmoke.java
    java -Xverify:all -cp app/src/main/assets/engine.jar:build/frame-compat-test \
        org.recompile.mobile.MissingStackMapSmoke \
        "$MISSING_STACKMAP_TEST_JAR" "${MISSING_STACKMAP_TEST_CLASS:-de}"
fi
mkdir -p build/legacy-class-literal-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/legacy-class-literal-test tools/Legacy45ClassLiteralSmoke.java
java -Xverify:all -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/legacy-class-literal-test \
    org.recompile.mobile.Legacy45ClassLiteralSmoke
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/fileconnection-test tools/FileConnectionSmoke.java
if [ -n "${PVTK_FILE_TEST_JAR:-}" ]; then
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/fileconnection-test \
        FileConnectionSmoke "$PVTK_FILE_TEST_JAR"
else
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/fileconnection-test \
        FileConnectionSmoke
fi
if [ -n "${AVATAR_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/lcdui-test tools/RmsEmptyRecordSmoke.java
    java -Djava.security.manager=allow -Djava.awt.headless=true \
        -cp app/src/main/assets/engine.jar:build/lcdui-test RmsEmptyRecordSmoke "$AVATAR_TEST_JAR"
fi
mkdir -p build/rms-large-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/rms-large-test tools/RmsLargeRecordSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/rms-large-test \
    RmsLargeRecordSmoke
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/audio-test tools/AudioBridgeSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/audio-test AudioBridgeSmoke
mkdir -p build/framebuffer-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/framebuffer-test tools/FramebufferSnapshotSmoke.java
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/framebuffer-test FramebufferSnapshotSmoke
mkdir -p build/game-key-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/game-key-test tools/GameCanvasKeyStateSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/game-key-test \
    GameCanvasKeyStateSmoke
if [ -n "${CANVAS_RESOLUTION_TEST_JAR:-}" ]; then
    mkdir -p build/canvas-resolution-test
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/canvas-resolution-test tools/CanvasResolutionSmoke.java
    java -cp app/src/main/assets/engine.jar:build/canvas-resolution-test \
        org.recompile.mobile.CanvasResolutionSmoke "$CANVAS_RESOLUTION_TEST_JAR"
fi
mkdir -p build/sprite-layer-dimensions-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/sprite-layer-dimensions-test tools/SpriteLayerDimensionsSmoke.java
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/sprite-layer-dimensions-test \
    SpriteLayerDimensionsSmoke
mkdir -p build/midlet-exit-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/midlet-exit-test tools/MidletExitSmoke.java
exit_smoke_output=$(java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/midlet-exit-test MidletExitSmoke)
printf '%s\n' "$exit_smoke_output"
grep -q 'MidletExitSmoke: 5 checks passed' <<<"$exit_smoke_output"
mkdir -p build/sms-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/sms-test tools/SmsConnectionSmoke.java
java -cp app/src/main/assets/engine.jar:build/sms-test SmsConnectionSmoke
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/network-test tools/NetworkConnectorSmoke.java
java -cp app/src/main/assets/engine.jar:build/network-test NetworkConnectorSmoke
if [ -n "${SECURE_CONNECTION_TEST_URL:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/network-test tools/SecureConnectionSmoke.java
    java -Djavax.net.ssl.trustStore=build/runtime/lib/security/cacerts \
        -Djavax.net.ssl.trustStorePassword=changeit \
        -cp app/src/main/assets/engine.jar:build/network-test \
        org.recompile.mobile.SecureConnectionSmoke "$SECURE_CONNECTION_TEST_URL"
fi
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/network-test tools/SocketConnectorSmoke.java
if [ -n "${ONLINE_TEST_JAR:-}" ]; then
    java -cp app/src/main/assets/engine.jar:build/network-test SocketConnectorSmoke "$ONLINE_TEST_JAR"
else
    java -cp app/src/main/assets/engine.jar:build/network-test SocketConnectorSmoke
fi
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/lcdui-test tools/LcdUiSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/lcdui-test LcdUiSmoke
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/lcdui-test tools/NativeUiModelSmoke.java tools/DateFieldModelSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/lcdui-test NativeUiModelSmoke
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/lcdui-test DateFieldModelSmoke
javac --release 8 -encoding UTF-8 \
    -cp "$ANDROID_HOME/platforms/android-34/android.jar:app/src/main/assets/engine.jar" \
    -d build/native-input-test \
    app/src/main/java/org/recompile/freej2me/lite/nativeui/NativeInputTypes.java \
    tools/NativeInputTypesSmoke.java
java -cp build/native-input-test NativeInputTypesSmoke
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/clone-owner-test tools/CloneDisplayOwnerSmoke.java
java -cp app/src/main/assets/engine.jar:build/clone-owner-test CloneDisplayOwnerSmoke
mkdir -p build/caught-exception-test
mkdir -p build/caught-exception-fixture
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/caught-exception-fixture tools/MIDletCaughtThrowableFixture.java
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/caught-exception-test tools/MIDletCaughtThrowableSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/caught-exception-test \
    MIDletCaughtThrowableSmoke build/caught-exception-fixture/MIDletCaughtThrowableFixture.class
mkdir -p build/resource-diagnostic-fixture build/resource-diagnostic-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/resource-diagnostic-fixture tools/MIDletResourceDiagnosticFixture.java \
    tools/EnvironmentAssetsS34.java tools/HellMapLoadTrace.java
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/resource-diagnostic-test tools/MIDletResourceDiagnosticSmoke.java
java -Djava.awt.headless=true \
    -cp app/src/main/assets/engine.jar:build/resource-diagnostic-test \
    MIDletResourceDiagnosticSmoke build/resource-diagnostic-fixture
mkdir -p build/clone-restart-test
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/clone-restart-test tools/CloneRestartSmoke.java
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/clone-restart-test CloneRestartSmoke
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/canvas-visibility-test tools/CanvasVisibilitySmoke.java tools/CanvasReentrancySmoke.java
java -cp app/src/main/assets/engine.jar:build/canvas-visibility-test CanvasVisibilitySmoke
java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/canvas-visibility-test CanvasReentrancySmoke
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/clone-jar-test tools/MIDletLoaderTransformSmoke.java
if [ -n "${CLONE_TEST_JAR:-}" ]; then
    java -cp app/src/main/assets/engine.jar:build/clone-jar-test \
        MIDletLoaderTransformSmoke "$CLONE_TEST_JAR"
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/clone-jar-test tools/CloneJarIsolationSmoke.java
    java -cp app/src/main/assets/engine.jar:build/clone-jar-test \
        org.recompile.mobile.CloneJarIsolationSmoke "$CLONE_TEST_JAR"
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/clone-jar-test tools/CloneResourceSmoke.java
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/clone-jar-test \
        org.recompile.mobile.CloneResourceSmoke "$CLONE_TEST_JAR"
fi
if [ -n "${STARTUP_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/clone-jar-test tools/EntryResourceSmoke.java
    java -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/clone-jar-test \
        EntryResourceSmoke "$STARTUP_TEST_JAR"
fi
if [ -n "${MIDPLAY_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/canvas-visibility-test tools/MidplayScreenSmoke.java
    java -Xmx128m -Xss1m -Djava.security.manager=allow -Djava.awt.headless=true \
        -cp app/src/main/assets/engine.jar:build/canvas-visibility-test MidplayScreenSmoke "$MIDPLAY_TEST_JAR"
fi
if [ -n "${MAJESTY_TEST_JAR:-}" ]; then
    : "${MAJESTY_ORACLE_JAR:?Set MAJESTY_ORACLE_JAR to the device-tested repaired JAR}"
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/clone-jar-test tools/GameResourceCompatibilitySmoke.java tools/MajestyPackProbe.java
    java -Xmx512m -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/clone-jar-test \
        org.recompile.mobile.GameResourceCompatibilitySmoke "$MAJESTY_TEST_JAR" "$MAJESTY_ORACLE_JAR"
    java -Xmx512m -Djava.awt.headless=true -cp app/src/main/assets/engine.jar:build/clone-jar-test \
        MajestyPackProbe "$MAJESTY_TEST_JAR" --expect-adapted-success
fi
if [ -n "${PNG_TRANSPARENCY_TEST_JAR:-}" ]; then
    javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
        -d build/png-transparency-test tools/PngTransparencySmoke.java
    java -Djava.awt.headless=true \
        -cp app/src/main/assets/engine.jar:build/png-transparency-test \
        org.recompile.mobile.PngTransparencySmoke "$PNG_TRANSPARENCY_TEST_JAR"
fi
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar -d build/sample sample/DiagnosticMIDlet.java
jar cfm app/src/main/assets/sample.jar sample/MANIFEST.MF -C build/sample .
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/lcdui-sample sample/LcdUiDiagnosticMIDlet.java
jar cfm app/src/main/assets/lcdui-sample.jar sample/LCDUI.MF -C build/lcdui-sample .
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/lcdui-matrix/classes sample/LcdUiMatrixMIDlet.java
mkdir -p build/lcdui-matrix/stage/META-INF
cp sample/LCDUI-MATRIX.MF build/lcdui-matrix/stage/META-INF/MANIFEST.MF
cp build/lcdui-matrix/classes/*.class build/lcdui-matrix/stage/
rm -f build/lcdui-matrix/v1-lcdui-diagnostic-matrix.jar
(cd build/lcdui-matrix/stage && \
    zip -X -q ../v3-lcdui-diagnostic-matrix.jar META-INF/MANIFEST.MF *.class)
mkdir -p build/bridge-test/classes
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/bridge-test/classes tools/BridgeInputMIDlet.java
printf 'Manifest-Version: 1.0\nMIDlet-1: Input,,BridgeInputMIDlet\nMIDlet-Name: Input\nMIDlet-Version: 1.0\nMIDlet-Vendor: Test\n\n' > build/bridge-test/MANIFEST.MF
jar cfm build/bridge-test/input.jar build/bridge-test/MANIFEST.MF -C build/bridge-test/classes .
javac --release 8 -encoding UTF-8 -cp app/src/main/assets/engine.jar \
    -d build/bridge-test/classes tools/NativeUiBridgeSmoke.java
java -cp app/src/main/assets/engine.jar:build/bridge-test/classes NativeUiBridgeSmoke "$JAVA_HOME/bin/java"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang" \
    -std=c11 -D_GNU_SOURCE -O2 -shared -fPIC -Wall -Wextra -Wno-unused-parameter \
    -Wl,-z,max-page-size=16384 native/host.c -ldl \
    -o app/src/main/jniLibs/arm64-v8a/libfreej2me_host.so
if [ ! -f app/src/main/assets/runtime.tar.xz ] \
    || [ tools/prepare_runtime.py -nt app/src/main/assets/runtime.tar.xz ] \
    || [ -n "$(find build/runtime -type f -newer app/src/main/assets/runtime.tar.xz -print -quit)" ]; then
    python3 tools/prepare_runtime.py build/runtime app/src/main/assets/runtime.tar.xz
fi
./gradlew --no-daemon --rerun-tasks :app:assembleDebug
