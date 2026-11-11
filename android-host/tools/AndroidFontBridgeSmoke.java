package org.recompile.mobile;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public final class AndroidFontBridgeSmoke {
    private static final int HANDSHAKE = 0x4A324D41;

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        AtomicInteger operations = new AtomicInteger();
        AtomicInteger renderedFace = new AtomicInteger(-1);
        Thread endpoint = new Thread(() -> serve(server, operations, renderedFace), "FakeAndroidPaint");
        endpoint.setDaemon(true);
        endpoint.start();
        AndroidFontBridge.connect(server.getLocalPort(), "font-bridge-smoke");

        Font.configureScreenSize(240, 320);
        Font font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        require(font.getHeight() == 14, "Android Paint font metrics should define MIDP height");
        require(font.getBaselinePosition() == 10, "Android Paint ascent should define MIDP baseline");
        require(font.charWidth('W') == 8,
                "Android Paint character advance should preserve MIDP integer truncation");
        require(font.stringWidth("Wi") == 16,
                "Paint should measure a whole UTF-16 run before truncating");

        PlatformImage image = new PlatformImage(80, 40);
        PlatformGraphics graphics = image.getGraphics();
        graphics.setColor(0x36a9e1);
        graphics.setFont(font);
        graphics.drawString("Wi", 40, 20, Graphics.HCENTER | Graphics.BASELINE);
        BufferedImage pixels = image.getCanvas();
        int minimumX = pixels.getWidth(), maximumX = -1, rendered = 0;
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++) {
                if ((pixels.getRGB(x, y) & 0xffffff) == 0x36a9e1) {
                    minimumX = Math.min(minimumX, x);
                    maximumX = Math.max(maximumX, x);
                    rendered++;
                }
            }
        }
        require(rendered > 0, "Canvas raster returned by Android should composite into the framebuffer");
        require(Math.abs((minimumX + maximumX) / 2.0 - 40.0) <= 1.0,
                "Android Paint advance should anchor the returned glyph raster");
        require(renderedFace.get() == Font.FACE_MONOSPACE,
                "Graphics.setFont must pass FACE_MONOSPACE through to Android Paint drawing");
        require(operations.get() >= 4, "metrics, measurement, and raster requests should reach Android");

        AndroidFontBridge.close();
        server.close();
        endpoint.join(2000);
        System.out.println("AndroidFontBridgeSmoke: Paint metrics, shaping width, glyph raster, "
                + "color, and MIDP centering passed");
    }

    private static void serve(ServerSocket server, AtomicInteger operations, AtomicInteger renderedFace) {
        try (Socket client = server.accept();
                DataInputStream input = new DataInputStream(
                        new BufferedInputStream(client.getInputStream(), 32768));
                DataOutputStream output = new DataOutputStream(
                        new BufferedOutputStream(client.getOutputStream(), 32768))) {
            require("font-bridge-smoke".equals(input.readUTF()), "font bridge token should authenticate");
            output.writeInt(HANDSHAKE);
            output.flush();
            while (true) {
                int operation = input.readInt();
                int face = input.readInt();
                input.readInt(); // style
                input.readInt(); // MIDP point size
                operations.incrementAndGet();
                if (operation == 1) {
                    writeMetrics(output);
                } else if (operation == 2) {
                    String text = readText(input);
                    int charCount = input.readInt();
                    output.writeFloat(charCount * 8.25f);
                } else if (operation == 3) {
                    renderedFace.set(face);
                    String text = readText(input);
                    output.writeFloat(text.length() * 8.25f);
                    writeMetrics(output);
                    int width = Math.max(1, text.length() * 8 + 2);
                    int height = 10;
                    output.writeInt(-1); // ink left bearing
                    output.writeInt(-8); // ink top from baseline
                    output.writeInt(width);
                    output.writeInt(height);
                    for (int i = 0; i < width * height; i++)
                        output.writeInt(0xffffffff);
                } else {
                    throw new IllegalStateException("Unexpected operation " + operation);
                }
                output.flush();
            }
        } catch (java.io.EOFException closed) {
            // The test closes the client after its final request.
        } catch (Exception error) {
            throw new AssertionError("Fake Android Paint endpoint failed", error);
        }
    }

    private static void writeMetrics(DataOutputStream output) throws Exception {
        output.writeInt(10);
        output.writeInt(3);
        output.writeInt(1);
        output.writeInt(2);
    }

    private static String readText(DataInputStream input) throws Exception {
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
