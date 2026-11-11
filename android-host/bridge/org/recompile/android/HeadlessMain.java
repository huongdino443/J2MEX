package org.recompile.android;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;
import org.recompile.mobile.RuntimeTiming;
import org.recompile.mobile.AudioBridge;
import org.recompile.mobile.AndroidFontBridge;
import org.recompile.mobile.ManagedMemoryEditor;
import org.recompile.mobile.MemoryEditorGuard;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.NativeUi;
import org.recompile.freej2me.protocol.NativeScreen;
import org.recompile.freej2me.protocol.FramebufferProtocol;
import org.recompile.freej2me.protocol.BluetoothProtocol;
import org.recompile.freej2me.protocol.JarManifestReader;
import org.recompile.mobile.BluetoothBridge;

/** Java SE side. No desktop Window, Cacio, Android classes, or JAR-to-DEX. */
public final class HeadlessMain {
    private static DataOutputStream output;
    private static volatile boolean running = true;
    private static final AtomicBoolean exitRequested = new AtomicBoolean();
    private static final AtomicLong revision = new AtomicLong();
    private static volatile Thread frameWriterThread;
    private static final String TOUCH_INPUT_ARGUMENT = "--j2mex-touch-input=";
    private static final String UI_LANGUAGE_ARGUMENT = "--j2mex-ui-language=";

    private static boolean parseTouchInputEnabled(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Invalid touch input setting: " + value);
    }

    private static void writeText(DataOutputStream stream, String text) throws IOException {
        byte[] bytes = (text == null ? "" : text).getBytes("UTF-8");
        if (bytes.length > 1048576) throw new IOException("Text input exceeds 1 MiB");
        stream.writeInt(bytes.length);
        stream.write(bytes);
    }
    private static String readText(DataInputStream stream) throws IOException {
        int length = stream.readInt();
        if (length < 0 || length > 1048576) throw new IOException("Invalid text input length");
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private static synchronized void status(int type, String text) {
        System.out.println(text);
        if (output == null) return;
        try { output.writeInt(type); output.writeUTF(text); output.flush(); }
        catch (IOException e) { running = false; }
    }

    private static void memoryReply(int requestId, int operation,
            ManagedMemoryEditor.Result result) {
        synchronized (HeadlessMain.class) {
            if (output == null || !running) return;
            try {
                output.writeInt(9);
                output.writeInt(requestId);
                output.writeInt(operation);
                output.writeBoolean(result.success);
                output.writeUTF(result.message == null ? "" : result.message);
                output.writeInt(result.total);
                int count = Math.min(ManagedMemoryEditor.MAX_VISIBLE_RESULTS,
                        result.entries.size());
                output.writeInt(count);
                for (int index = 0; index < count; index++) {
                    ManagedMemoryEditor.Entry entry = result.entries.get(index);
                    output.writeInt(entry.id);
                    output.writeUTF(entry.type);
                    output.writeUTF(entry.value);
                    output.writeUTF(entry.location);
                    output.writeUTF(entry.referenceKind);
                }
                output.flush();
            } catch (IOException error) {
                running = false;
            }
        }
    }

    public static String applyDeviceLocale(String localeTag) {
        String requested = localeTag == null ? "" : localeTag.trim();
        if (requested.isEmpty()) requested = Locale.getDefault().toLanguageTag();
        Locale locale = Locale.forLanguageTag(requested);
        if (locale.getLanguage().isEmpty())
            throw new IllegalArgumentException("Invalid device locale: " + requested);
        Locale.setDefault(locale);
        String resolved = locale.toLanguageTag();
        System.setProperty("microedition.locale", resolved);
        return resolved;
    }

    public static void main(String[] args) throws Exception {
        // jar URL, width, height, localhost port, token, RMS directory, audio cache,
        // refresh Hz, locale, required Android Paint port, optional legacy font paths,
        // and optional --j2mex-touch-input= and --j2mex-ui-language= flags.
        int launchArgumentCount = args.length;
        boolean touchInputEnabled = true;
        String uiLanguage = "en";
        if (launchArgumentCount > 10
                && args[launchArgumentCount - 1].startsWith(UI_LANGUAGE_ARGUMENT)) {
            uiLanguage = args[launchArgumentCount - 1]
                    .substring(UI_LANGUAGE_ARGUMENT.length());
            launchArgumentCount--;
        }
        if (launchArgumentCount > 10
                && args[launchArgumentCount - 1].startsWith(TOUCH_INPUT_ARGUMENT)) {
            String value = args[launchArgumentCount - 1]
                    .substring(TOUCH_INPUT_ARGUMENT.length());
            touchInputEnabled = parseTouchInputEnabled(value);
            launchArgumentCount--;
        }
        if (args.length < 10 || launchArgumentCount > 13)
            throw new IllegalArgumentException("Expected ten to thirteen launch arguments, "
                    + "optionally followed by touch input and app language flags");
        System.setProperty("j2mex.ui.language", "vi".equalsIgnoreCase(uiLanguage) ? "vi" : "en");
        Canvas.setPointerInputEnabled(touchInputEnabled);
        String deviceLocale = applyDeviceLocale(args.length >= 9
                ? args[8] : Locale.getDefault().toLanguageTag());
        String textEncoding = System.getProperty("file.encoding", "ISO-8859-1");
        System.setProperty("microedition.encoding", textEncoding);
        int androidFontPort = Integer.parseInt(args[9]);
        if (androidFontPort <= 0)
            throw new IOException("Android Paint/Canvas font bridge is required");
        AndroidFontBridge.connect(androidFontPort, args[4]);
        if (!AndroidFontBridge.isConnected())
            throw new IOException("Android Paint/Canvas font bridge did not connect");
        String systemFontPath = applySystemFontPath("freej2me.android.defaultFont",
                launchArgumentCount >= 11 ? args[10] : "");
        String fallbackFontPath = applySystemFontPath("freej2me.android.defaultFallbackFont",
                launchArgumentCount >= 12 ? args[11] : "");
        String monospaceFontPath = applySystemFontPath("freej2me.android.monospaceFont",
                launchArgumentCount >= 13 ? args[12] : "");
        int width = Integer.parseInt(args[1]), height = Integer.parseInt(args[2]);
        Font.configureScreenSize(width, height);
        Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), Integer.parseInt(args[3]));
        socket.setTcpNoDelay(true);
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65536));
        output.writeUTF(args[4]);
        output.flush();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            status(3, thread.getName() + ": " + error);
        });
        try {
            status(2, "Android device locale=" + deviceLocale);
            status(2, "MIDlet text encoding=" + System.getProperty("microedition.encoding")
                    + "; JVM default=" + System.getProperty("file.encoding"));
            status(2, "Android font route="
                    + (AndroidFontBridge.isConnected()
                            ? "Paint/Canvas direct; MIDP screen=" + width + "x" + height
                            : "Java2D/AWT; Android system font files"));
            status(2, "AWT font drawing disabled; Android Paint/Canvas handles MIDP text; "
                    + "FACE_MONOSPACE uses Typeface.MONOSPACE");
            status(2, "Java " + System.getProperty("java.version") + " connected; initializing headless FreeJ2ME");
            status(2, "MIDlet pointer input=" + (touchInputEnabled ? "enabled" : "disabled"));
            try (JarFile jar = new JarFile(new File(new URI(args[0])))) {
                Manifest manifest = JarManifestReader.read(jar);
                String midlet = manifest == null ? null : manifest.getMainAttributes().getValue("MIDlet-1");
                if (midlet == null || midlet.split(",").length != 3)
                    throw new IOException("MIDlet-1 missing or invalid");
                String className = midlet.split(",")[2].trim().replace('.', '/') + ".class";
                if (jar.getJarEntry(className) == null) throw new IOException("Missing class " + className);
                status(2, "MIDlet manifest: " + midlet);
            }
            MobilePlatform platform = new MobilePlatform(width, height);
            platform.dataPath = args[5] + File.separator;
            Mobile.setPlatform(platform);
            RuntimeTiming.reset();
            MemoryEditorGuard.resetSession();
            int displayRefreshHz = args.length >= 8 ? Integer.parseInt(args[7]) : 0;
            Canvas.setFrameRateLimit(displayRefreshHz);
            platform.setMidletExitHandler(() -> {
                if (!exitRequested.compareAndSet(false, true)) return;
                status(5, "MIDlet sent Destroyed Notification");
            });
            BluetoothBridge.setTransport((id, operation, flags, number, value, extra, payload) -> {
                synchronized (HeadlessMain.class) {
                    if (output == null || !running) throw new IOException("Bluetooth host disconnected");
                    output.writeInt(BluetoothProtocol.HOST_REQUEST);
                    output.writeInt(id);
                    output.writeInt(operation);
                    output.writeInt(flags);
                    output.writeInt(number);
                    output.writeUTF(value == null ? "" : value);
                    output.writeUTF(extra == null ? "" : extra);
                    byte[] data = payload == null ? new byte[0] : payload;
                    if (data.length > 1048576) throw new IOException("Bluetooth payload exceeds 1 MiB");
                    output.writeInt(data.length);
                    output.write(data);
                    output.flush();
                }
            });
            status(2, "[bluetooth] host transport registered; bridgeLoader="
                    + BluetoothBridge.class.getClassLoader());
            AudioBridge.configure(new File(args[6]), new AudioBridge.Transport() {
                public void send(int action, int playerId, String text, String extra,
                        long value, int number, int flags) throws IOException {
                    synchronized (HeadlessMain.class) {
                        if (output == null || !running) throw new IOException("Audio host disconnected");
                        output.writeInt(8);
                        output.writeInt(action);
                        output.writeInt(playerId);
                        output.writeUTF(text == null ? "" : text);
                        output.writeUTF(extra == null ? "" : extra);
                        output.writeLong(value);
                        output.writeInt(number);
                        output.writeInt(flags);
                        output.flush();
                    }
                }
            });
            platform.setPainter(() -> {
                revision.incrementAndGet();
                Thread writer = frameWriterThread;
                if (writer != null) LockSupport.unpark(writer);
            });
            platform.setTextInputHandler(new MobilePlatform.TextInputHandler() {
                public void request(int requestId, String title, String value, int maxSize, int constraints) {
                    request(requestId, title, value, maxSize, constraints,
                            new String[0], new int[0], -1);
                }
                public void request(int requestId, String title, String value, int maxSize, int constraints,
                        String[] commands, int defaultCommand) {
                    int[] commandTypes = new int[commands.length];
                    java.util.Arrays.fill(commandTypes, -1);
                    request(requestId, title, value, maxSize, constraints, commands, commandTypes, defaultCommand);
                }
                public void request(int requestId, String title, String value, int maxSize, int constraints,
                        String[] commands, int[] commandTypes, int defaultCommand) {
                    synchronized (HeadlessMain.class) {
                        try {
                            output.writeInt(4);
                            output.writeInt(requestId);
                            output.writeUTF(title == null ? "" : title);
                            writeText(output, value);
                            output.writeInt(maxSize);
                            output.writeInt(constraints);
                            output.writeInt(commands.length);
                            for (String command : commands) output.writeUTF(command == null ? "" : command);
                            output.writeInt(commands.length);
                            for (int i = 0; i < commands.length; i++)
                                output.writeInt(commandTypes != null && i < commandTypes.length ? commandTypes[i] : -1);
                            output.writeInt(defaultCommand);
                            output.flush();
                        } catch (IOException error) { running = false; }
                    }
                }
                public void dismiss() {
                    synchronized (HeadlessMain.class) {
                        try { output.writeInt(6); output.flush(); }
                        catch (IOException error) { running = false; }
                    }
                }
            });
            NativeUi.setPresenter(screen -> {
                synchronized (HeadlessMain.class) {
                    try {
                        output.writeInt(7);
                        screen.write(output);
                        output.flush();
                    } catch (IOException error) {
                        running = false;
                        throw new IllegalStateException("Native LCDUI transport failed", error);
                    }
                }
            });

            Thread frames = new Thread(() -> {
                long sent = 0;
                int[] pixels = null;
                byte[] bytes = null;
                IntBuffer frameBuffer = null;
                long sampleFrames = 0, lastSentAt = 0, gapTotal = 0, gapMax = 0;
                long snapshotTotal = 0, snapshotMax = 0, encodeTotal = 0, encodeMax = 0;
                long socketTotal = 0, socketMax = 0, coalescedRevisions = 0;
                try {
                    while (running) {
                        long current = revision.get();
                        if (current == sent) {
                            LockSupport.park();
                            continue;
                        }
                        long frameStartedAt = System.nanoTime();
                        if (current != sent) {
                            long snapshotStartedAt = System.nanoTime();
                            int w, h;
                            synchronized (platform) {
                                BufferedImage lcd = platform.getLCD();
                                w = lcd.getWidth(); h = lcd.getHeight();
                                if (w < 1 || h < 1
                                        || w > FramebufferProtocol.MAX_EDGE
                                        || h > FramebufferProtocol.MAX_EDGE)
                                    throw new IOException("Invalid framebuffer " + w + "x" + h
                                            + " (maximum " + FramebufferProtocol.MAX_EDGE
                                            + " px per edge)");
                                if (pixels == null || pixels.length != w * h) {
                                    pixels = new int[w * h];
                                    bytes = new byte[w * h * 4];
                                    frameBuffer = ByteBuffer.wrap(bytes).asIntBuffer();
                                }
                                platform.copyFrameTo(pixels);
                            }
                            long snapshotFinishedAt = System.nanoTime();
                            frameBuffer.clear();
                            frameBuffer.put(pixels);
                            long encodeFinishedAt = System.nanoTime();
                            long previousSentRevision = sent;
                            synchronized (HeadlessMain.class) {
                                output.writeInt(1); output.writeInt(w); output.writeInt(h);
                                output.write(bytes); output.flush();
                            }
                            sent = current;
                            long sendFinishedAt = System.nanoTime();
                            if (lastSentAt != 0) {
                                long gap = sendFinishedAt - lastSentAt;
                                gapTotal += gap;
                                gapMax = Math.max(gapMax, gap);
                            }
                            lastSentAt = sendFinishedAt;
                            snapshotTotal += snapshotFinishedAt - snapshotStartedAt;
                            snapshotMax = Math.max(snapshotMax, snapshotFinishedAt - snapshotStartedAt);
                            encodeTotal += encodeFinishedAt - snapshotFinishedAt;
                            encodeMax = Math.max(encodeMax, encodeFinishedAt - snapshotFinishedAt);
                            socketTotal += sendFinishedAt - encodeFinishedAt;
                            socketMax = Math.max(socketMax, sendFinishedAt - encodeFinishedAt);
                            coalescedRevisions += Math.max(0L, current - previousSentRevision - 1L);
                            sampleFrames++;
                            if (sampleFrames >= 300) {
                                status(2, String.format(Locale.US,
                                        "[frame-host] sent=%d gapAvg/Max=%.2f/%.2fms " +
                                        "snapshotAvg/Max=%.2f/%.2fms encodeAvg/Max=%.2f/%.2fms " +
                                        "socketAvg/Max=%.2f/%.2fms coalesced=%d",
                                        sampleFrames,
                                        gapTotal / ((sampleFrames - 1) * 1_000_000.0), gapMax / 1_000_000.0,
                                        snapshotTotal / (sampleFrames * 1_000_000.0), snapshotMax / 1_000_000.0,
                                        encodeTotal / (sampleFrames * 1_000_000.0), encodeMax / 1_000_000.0,
                                        socketTotal / (sampleFrames * 1_000_000.0), socketMax / 1_000_000.0,
                                        coalescedRevisions));
                                sampleFrames = gapTotal = gapMax = snapshotTotal = snapshotMax = 0;
                                encodeTotal = encodeMax = socketTotal = socketMax = coalescedRevisions = 0;
                            }
                        }
                    }
                } catch (Throwable error) {
                    error.printStackTrace();
                    status(3, "Framebuffer: " + error);
                    running = false;
                }
            }, "FramebufferWriter");
            frameWriterThread = frames;
            frames.setDaemon(true);
            frames.start();

            if (!platform.loadJar(args[0])) throw new IOException("FreeJ2ME could not load MIDlet JAR");
            ManagedMemoryEditor memoryEditor = new ManagedMemoryEditor(platform.loader);
            status(2, "JAR loaded; invoking MIDlet.startApp()");
            new Thread(platform::runJar, "MIDletStarter").start();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            boolean pointerFailureReported = false;
            while (running) {
                int command = input.readUnsignedByte();
                if (command == 9) break;
                if (command == 1 || command == 2) {
                    int key = input.readInt();
                    if (Mobile.getDisplay() != null && Mobile.getDisplay().getCurrent() != null) {
                        if (command == 1) platform.keyPressed(key); else platform.keyReleased(key);
                    }
                } else if (command >= 3 && command <= 5) {
                    int x = input.readInt(), y = input.readInt();
                    if (Mobile.getDisplay() != null && Mobile.getDisplay().getCurrent() != null) {
                        try {
                            if (command == 3) platform.pointerPressed(x, y);
                            else if (command == 4) platform.pointerDragged(x, y);
                            else platform.pointerReleased(x, y);
                        } catch (RuntimeException inputFailure) {
                            // A broken MIDlet pointer callback must not kill the engine event pump.
                            if (!pointerFailureReported) {
                                System.err.println("MIDlet pointer callback failed; input event ignored");
                                inputFailure.printStackTrace();
                                pointerFailureReported = true;
                            }
                        }
                    }
                } else if (command == 6) {
                    int requestId = input.readInt();
                    platform.updateTextInput(requestId, readText(input));
                } else if (command == 12) {
                    int requestId = input.readInt();
                    String text = readText(input);
                    platform.submitTextInput(requestId, text, input.readInt());
                } else if (command == 8) {
                    platform.cancelTextInput(input.readInt());
                } else if (command == 10) {
                    if (!platform.handleBackPressed()) status(5, "Back requests exit");
                } else if (command == 11) {
                    NativeUi.apply(NativeScreen.readEvent(input));
                } else if (command == 13) {
                    AudioBridge.dispatchEvent(input.readInt(), input.readInt(), input.readLong(), input.readUTF());
                } else if (command == 14) {
                    Canvas.setFrameRateLimit(input.readInt());
                } else if (command == 15) {
                    RuntimeTiming.setSpeedPercent(input.readInt());
                } else if (command == 16) {
                    int operation = input.readInt();
                    int requestId = input.readInt();
                    String query = input.readUTF();
                    int candidateId = input.readInt();
                    String newValue = input.readUTF();
                    ManagedMemoryEditor.Result result;
                    if (operation == 0) {
                        result = MemoryEditorGuard.openEditor()
                                ? ManagedMemoryEditor.notice(true, "")
                                : ManagedMemoryEditor.notice(false,
                                        "Không thể mở trình sửa bộ nhớ sau khi game đã dùng kết nối mạng.");
                    } else if (operation == 1 || operation == 2) {
                        result = MemoryEditorGuard.isEditorOpen()
                                ? memoryEditor.search(query, operation == 2)
                                : ManagedMemoryEditor.notice(false,
                                        "Trình sửa bộ nhớ phải được mở trước.");
                    } else if (operation == 3) {
                        result = MemoryEditorGuard.isEditorOpen()
                                ? memoryEditor.edit(candidateId, newValue)
                                : ManagedMemoryEditor.notice(false,
                                        "Trình sửa bộ nhớ phải được mở trước.");
                    } else if (operation == 4) {
                        MemoryEditorGuard.closeEditor();
                        result = ManagedMemoryEditor.notice(true, "");
                    } else {
                        result = ManagedMemoryEditor.notice(false,
                                "Không xác định thao tác trình sửa bộ nhớ.");
                    }
                    memoryReply(requestId, operation, result);
                } else if (command == BluetoothProtocol.ENGINE_RESPONSE) {
                    int requestId = input.readInt();
                    boolean accepted = input.readBoolean();
                    int number = input.readInt();
                    String detail = input.readUTF();
                    int length = input.readInt();
                    if (length < 0 || length > 1048576) throw new IOException("Invalid Bluetooth response length");
                    byte[] data = new byte[length];
                    input.readFully(data);
                    BluetoothBridge.complete(requestId, accepted, number, detail, data);
                } else if (command == BluetoothProtocol.ENGINE_EVENT) {
                    int event = input.readInt();
                    String address = input.readUTF();
                    String name = input.readUTF();
                    int deviceClass = input.readInt();
                    int result = input.readInt();
                    BluetoothBridge.dispatchEvent(event, address, name, deviceClass, result);
                } else throw new IOException("Unknown command " + command);
            }
        } catch (Throwable error) {
            error.printStackTrace();
            status(3, "Engine: " + error);
        } finally {
            AndroidFontBridge.close();
            AudioBridge.closeAll();
            running = false;
            socket.close();
        }
        System.out.println("[engine-shutdown] Java command loop ended; requesting System.exit(0)");
        System.exit(0);
    }

    private static String applySystemFontPath(String property, String rawPath)
            throws IOException {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty()) {
            System.clearProperty(property);
            return "";
        }
        File fontFile = new File(path);
        if (!fontFile.isFile() || !fontFile.canRead())
            throw new IOException("Android system font is not readable: " + path);
        String canonicalPath = fontFile.getCanonicalPath();
        System.setProperty(property, canonicalPath);
        return canonicalPath;
    }

    private static String fontFileName(String path) {
        return path == null || path.isEmpty() ? "not provided" : new File(path).getName();
    }
}
