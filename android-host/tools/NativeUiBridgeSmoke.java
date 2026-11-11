import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.recompile.freej2me.protocol.NativeScreen;

/** Real HeadlessMain process; emulates Android wire events, not Android Views. */
public final class NativeUiBridgeSmoke {
    private static final int FONT_HANDSHAKE = 0x4A324D41;
    private static DataInputStream in;
    private static DataOutputStream out;
    private static long nextSequence;
    private static final class Packet {
        int kind, request;
        int defaultCommand;
        String[] inputCommands;
        int[] inputCommandTypes;
        NativeScreen screen;
        byte[] pixels;
    }
    private static Packet packet() throws Exception {
        Packet p = new Packet(); p.kind = in.readInt();
        if (p.kind == 1) {
            int w = in.readInt(), h = in.readInt();
            if (w < 1 || h < 1 || w > 1024 || h > 1024) throw new AssertionError("Invalid frame");
            p.pixels = new byte[w * h * 4]; in.readFully(p.pixels);
        } else if (p.kind == 2 || p.kind == 3 || p.kind == 5) {
            String text = in.readUTF(); if (p.kind == 3) throw new AssertionError(text);
        } else if (p.kind == 4) {
            p.request = in.readInt(); in.readUTF();
            int size = in.readInt();
            if (size < 0 || size > 1048576) throw new AssertionError("Invalid text");
            byte[] text = new byte[size]; in.readFully(text); in.readInt(); in.readInt();
            int count = in.readInt();
            if (count < 0 || count > 256) throw new AssertionError("Invalid TextBox command count");
            p.inputCommands = new String[count];
            for (int i = 0; i < count; i++) p.inputCommands[i] = in.readUTF();
            int typeCount = in.readInt();
            if (typeCount != count) throw new AssertionError("Invalid TextBox command type count");
            p.inputCommandTypes = new int[typeCount];
            for (int i = 0; i < typeCount; i++) p.inputCommandTypes[i] = in.readInt();
            p.defaultCommand = in.readInt();
        } else if (p.kind == 7) p.screen = NativeScreen.read(in);
        else if (p.kind != 6) throw new AssertionError("Unknown packet " + p.kind);
        return p;
    }
    private static NativeScreen nativeScreen(int kind) throws Exception {
        for (int i = 0; i < 100; i++) {
            Packet p = packet(); if (p.kind == 7 && p.screen.kind == kind) return p.screen;
        }
        throw new AssertionError("Missing native screen " + kind);
    }
    private static NativeScreen.Event event(NativeScreen screen, int action, int target) {
        NativeScreen.Event event = new NativeScreen.Event();
        event.epoch = screen.epoch; event.sequence = ++nextSequence;
        event.action = action; event.target = target;
        return event;
    }
    private static void send(NativeScreen.Event event) throws Exception {
        out.writeByte(11); NativeScreen.writeEvent(out, event); out.flush();
    }
    public static void main(String[] args) throws Exception {
        File log = new File("build/bridge-test/bridge.log");
        File data = Files.createTempDirectory("native-lcdui-").toFile();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                ServerSocket fontServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(15000);
            fontServer.setSoTimeout(15000);
            String token = "native-lcdui-test";
            AtomicReference<Throwable> fontFailure = new AtomicReference<>();
            Thread fontEndpoint = new Thread(
                    () -> serveFontBridge(fontServer, token, fontFailure), "FakeAndroidPaint");
            fontEndpoint.setDaemon(true);
            fontEndpoint.start();
            Process process = new ProcessBuilder(args[0], "-Djava.awt.headless=true", "-cp",
                    "app/src/main/assets/engine.jar:app/src/main/assets/bridge.jar",
                    "org.recompile.android.HeadlessMain", new File("build/bridge-test/input.jar").toURI().toString(),
                    "240", "320", Integer.toString(server.getLocalPort()), token, data.getAbsolutePath(),
                      new File("build/bridge-test/audio").getAbsolutePath(), "120", "vi-VN",
                      Integer.toString(fontServer.getLocalPort()))
                    .redirectErrorStream(true).redirectOutput(log).start();
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(15000);
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                if (!in.readUTF().equals(token)) throw new AssertionError("Invalid session token");
                NativeScreen menu = nativeScreen(NativeScreen.LIST);
                NativeScreen.Event select = event(menu, NativeScreen.Event.SELECT, menu.items.get(1).id);
                for (int i = 0; i < menu.items.size(); i++) {
                    NativeScreen.State state = new NativeScreen.State();
                    state.id = menu.items.get(i).id; state.value = i == 1 ? 1 : 0; select.states.add(state);
                }
                send(select);
                int request = 0, defaultCommand = -1;
                String[] inputCommands = null;
                int[] inputCommandTypes = null;
                for (int i = 0; i < 100; i++) {
                    Packet p = packet();
                    if (p.kind == 4) {
                        request = p.request; defaultCommand = p.defaultCommand;
                        inputCommands = p.inputCommands; inputCommandTypes = p.inputCommandTypes; break;
                    }
                }
                if (request == 0) throw new AssertionError("Missing TextBox keyboard request");
                if (inputCommands == null || inputCommands.length != 2
                        || !"Save".equals(inputCommands[0]) || defaultCommand != 0
                        || inputCommandTypes == null || inputCommandTypes.length != 2
                        || inputCommandTypes[0] != 4 || inputCommandTypes[1] != 3)
                    throw new AssertionError("TextBox command labels, types or default action mismatch");
                byte[] text = "Tiếng Việt ✓".getBytes("UTF-8");
                out.writeByte(12); out.writeInt(request); out.writeInt(text.length); out.write(text);
                out.writeInt(defaultCommand); out.flush();
                NativeScreen form = nativeScreen(NativeScreen.FORM);
                if (form.items.size() != 2
                        || form.items.get(1).kind != NativeScreen.STRING
                        || !form.items.get(1).interactive
                        || form.items.get(1).actionId == 0)
                    throw new AssertionError("StringItem button style or existing item action was lost");
                send(event(form, NativeScreen.Event.ITEM, form.items.get(1).id));
                send(select); // Old List event must not affect the Form.
                NativeScreen.Event edit = event(form, NativeScreen.Event.CHANGE, 0);
                NativeScreen.State state = new NativeScreen.State();
                state.id = form.items.get(0).id; state.text = "Form Unicode ✓"; state.caret = state.text.length();
                edit.states.add(state); send(edit);
                boolean canvas = false, nativeHidden = false;
                for (int i = 0; i < 150; i++) {
                    Packet p = packet();
                    if (p.kind == 7 && p.screen.kind == NativeScreen.FRAMEBUFFER) nativeHidden = true;
                    if (p.kind == 1 && p.pixels[0] == (byte)255 && p.pixels[1] == 0x33
                            && p.pixels[2] == 0x66 && p.pixels[3] == (byte)0x99) { canvas = true; break; }
                }
                if (!canvas || !nativeHidden) throw new AssertionError("Alert / Canvas mode switch failed");
                out.writeByte(3); out.writeInt(11); out.writeInt(19);
                out.writeByte(1); out.writeInt(77); out.flush();
                out.writeByte(10); out.flush();
                while (packet().kind != 5) { }
                out.writeByte(9); out.flush();
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0)
                    throw new AssertionError("Bridge did not stop");
                fontEndpoint.join(2000);
                if (fontFailure.get() != null)
                    throw new AssertionError("Fake Android Paint endpoint failed", fontFailure.get());
                String output = new String(Files.readAllBytes(log.toPath()), "UTF-8");
                if (!output.contains("PASS_BRIDGE_TEXTBOX_SAVED_BEFORE_COMMAND")
                        || !output.contains("PASS_BRIDGE_FORM")
                        || !output.contains("PASS_STRING_ITEM_BUTTON_ACTION"))
                    throw new AssertionError(output);
                if (!output.contains("MIDlet pointer callback failed; input event ignored")
                        || !output.contains("PASS_POINTER_CALLBACK_SURVIVED"))
                    throw new AssertionError("Pointer exception stopped subsequent input processing:\n" + output);
                System.out.println("PASS: TextBox submit, Form navigation and pointer-exception isolation");
            } finally {
                if (process.isAlive()) process.destroyForcibly();
                for (File file : data.listFiles() == null ? new File[0] : data.listFiles()) file.delete();
                data.delete();
            }
        }
    }

    private static void serveFontBridge(ServerSocket server, String token,
            AtomicReference<Throwable> failure) {
        try (Socket client = server.accept();
                DataInputStream input = new DataInputStream(new BufferedInputStream(client.getInputStream()));
                DataOutputStream output = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()))) {
            if (!token.equals(input.readUTF())) throw new AssertionError("Invalid font bridge token");
            output.writeInt(FONT_HANDSHAKE);
            output.flush();
            while (true) {
                int operation = input.readInt();
                input.readInt(); // face
                input.readInt(); // style
                int pointSize = input.readInt();
                if (operation == 1) {
                    writeMetrics(output);
                } else if (operation == 2) {
                    String text = readText(input);
                    input.readInt(); // UTF-16 char count
                    output.writeFloat(text.length() * Math.max(1, pointSize) * 0.5f);
                } else if (operation == 3) {
                    String text = readText(input);
                    output.writeFloat(text.length() * Math.max(1, pointSize) * 0.5f);
                    writeMetrics(output);
                    output.writeInt(0); output.writeInt(-Math.max(1, pointSize));
                    output.writeInt(1); output.writeInt(1); output.writeInt(0xffffffff);
                } else {
                    throw new AssertionError("Unexpected font operation " + operation);
                }
                output.flush();
            }
        } catch (EOFException closed) {
            // The bridge closes its font socket when the MIDlet exits.
        } catch (Throwable error) {
            failure.set(error);
        }
    }

    private static void writeMetrics(DataOutputStream output) throws IOException {
        output.writeInt(10); output.writeInt(3); output.writeInt(1); output.writeInt(2);
    }

    private static String readText(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 1_048_576) throw new IOException("Invalid font text size");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
