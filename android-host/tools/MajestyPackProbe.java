import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.microedition.lcdui.Image;
import org.recompile.mobile.*;

/** Offline differential diagnosis; never instantiates or starts a MIDlet. */
public final class MajestyPackProbe {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        for (boolean adapted : new boolean[]{false, true}) {
            URL[] urls = {file.toURI().toURL()};
            ClassLoader loader = adapted ? new MIDletLoader(urls) : new URLClassLoader(urls);
            MobilePlatform platform = new MobilePlatform(240, 320);
            Mobile.setPlatform(platform);
            if (adapted) platform.loader = (MIDletLoader)loader;
            Class<?> packClass = loader.loadClass("ep");
            Constructor<?> constructor = packClass.getDeclaredConstructor(InputStream.class);
            constructor.setAccessible(true);
            Method decode = null;
            for (Method method : packClass.getDeclaredMethods())
                if (method.getName().equals("a") && method.getReturnType() == Image.class
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class})) decode = method;
            decode.setAccessible(true);
            Field count = null;
            for (Field field : packClass.getDeclaredFields())
                if (field.getName().equals("a") && field.getType() == int.class) count = field;
            count.setAccessible(true);
            int images = 0;
            boolean failed = false;
            try (JarFile jar = new JarFile(file)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements() && !failed) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".pak")) continue;
                    byte[] bytes;
                    try (InputStream input = jar.getInputStream(entry)) { bytes = read(input); }
                    if (adapted) bytes = platform.loader.getMIDletResourceAsByteArray("/" + entry.getName());
                    if (bytes.length < 8 || new DataInputStream(new ByteArrayInputStream(bytes)).readInt() != 1212631373)
                        continue;
                    Object pack = constructor.newInstance(new ByteArrayInputStream(bytes));
                    for (int i = 0; i < count.getInt(pack); i++) {
                        try {
                            Image image = (Image)decode.invoke(pack, i);
                            if (image == null) throw new AssertionError("null image");
                            images++;
                        } catch (InvocationTargetException ex) {
                            System.out.println("FAIL adapted=" + adapted + " pack=" + entry.getName()
                                    + " index=" + i + " priorImages=" + images + " cause=" + ex.getCause());
                            Class<?> decoder = loader.loadClass("ew");
                            for (Field field : decoder.getDeclaredFields()) {
                                if (field.getName().equals("a") && field.getType() == byte[].class) {
                                    field.setAccessible(true);
                                    byte[] png = (byte[])field.get(null);
                                    if (png != null) {
                                        Files.write(Paths.get("/tmp/majesty-failing-" + adapted + ".png"), png);
                                        Image reference = Image.createImage(png, 0, png.length);
                                        System.out.println("Native PNG reference=" + reference.getWidth()
                                                + "x" + reference.getHeight() + " bytes=" + png.length);
                                    }
                                }
                            }
                            failed = true;
                            break;
                        }
                    }
                }
            }
            System.out.println("Probe adapted=" + adapted + " decoded=" + images + " failed=" + failed);
            if (args.length > 1 && args[1].equals("--expect-success") && failed)
                throw new AssertionError("pack decode regression");
            if (args.length > 1 && args[1].equals("--expect-adapted-success")
                    && (adapted ? failed : !failed))
                throw new AssertionError("raw baseline/adapted resource comparison failed");
        }
    }
    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }
}
