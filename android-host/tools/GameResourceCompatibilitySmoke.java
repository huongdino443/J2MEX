package org.recompile.mobile;

import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import javax.microedition.lcdui.Image;

/** Real original resources versus the independently repaired, device-tested JAR. */
public final class GameResourceCompatibilitySmoke {
    private static final String FONT = "fntCrystalSmall40_0.pak";
    private static int checks;

    public static void main(String[] args) throws Exception {
        File originalJar = new File(args[0]), oracleJar = new File(args[1]);
        byte[] originalHash = digest(Files.readAllBytes(originalJar.toPath()));
        byte[] original, expected;
        try (JarFile jar = new JarFile(originalJar); JarFile oracle = new JarFile(oracleJar)) {
            original = read(jar.getInputStream(jar.getJarEntry(FONT)));
            expected = read(oracle.getInputStream(oracle.getJarEntry(FONT)));
        }
        URL[] urls = {originalJar.toURI().toURL()};
        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform);
        MIDletLoader loader = new MIDletLoader(urls);
        platform.loader = loader;
        byte[] actual = loader.getMIDletResourceAsByteArray("/" + FONT);
        require(!Arrays.equals(original, actual), "known original asset is adapted");
        for (String path : new String[]{FONT, "/" + FONT}) {
            require(Arrays.equals(actual, read(loader.getResourceAsStream(path))), "classloader stream");
            require(Arrays.equals(actual, read(loader.getMIDletResourceAsStream(path))), "MIDlet stream");
            require(Arrays.equals(actual, loader.getMIDletResourceAsByteArray(path)), "byte array");
        }
        actual[0] = 0;
        actual = loader.getMIDletResourceAsByteArray(FONT);
        require(actual[0] == 'H', "cached data is protected from caller mutation");
        require(loader.getMIDletResourceAsStream("/absent-compatibility-test") == null, "missing stays missing");
        try (JarFile jar = new JarFile(originalJar)) {
            for (String path : new String[]{"ep.class", "ew.class", "META-INF/MANIFEST.MF", "anims0.pak"}) {
                require(Arrays.equals(read(jar.getInputStream(jar.getJarEntry(path))),
                        loader.getMIDletResourceAsByteArray(path)), "unrelated bytes unchanged: " + path);
            }
        }

        URL decoder = loader.getResource("ew.class"), packReader = loader.getResource("ep.class");
        GameResourceCompatibility profile = GameResourceCompatibility.forClasses(decoder, packReader);
        require(profile != null, "exact decoder and pack reader recognized");
        require(GameResourceCompatibility.forClasses(packReader, decoder) == null, "wrong class hashes rejected");
        require(GameResourceCompatibility.forClasses(null, packReader) == null, "absent class rejected");
        require(profile.adapt("other.pak", original) == original, "other path has zero transformation");
        byte[] tampered = original.clone();
        tampered[tampered.length - 1] ^= 1;
        require(profile.adapt(FONT, tampered) == tampered, "unverified asset hash rejected");
        require(profile.adapt(FONT, expected) == expected, "already repaired asset remains unchanged");
        require(Arrays.equals(profile.adapt(FONT, original), actual), "direct adapter matches loader");

        Path unrecognizedJar = Files.createTempFile("unrecognized-game-profile-", ".jar");
        try {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            manifest.getMainAttributes().putValue("MIDlet-1", "Unrecognized,,Other");
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(unrecognizedJar), manifest)) {
                output.putNextEntry(new JarEntry(FONT));
                output.write(original);
                output.closeEntry();
            }
            try (MIDletLoader unrelated = new MIDletLoader(new URL[]{unrecognizedJar.toUri().toURL()})) {
                require(Arrays.equals(original, unrelated.getMIDletResourceAsByteArray(FONT)),
                        "same resource in unrelated game is not adapted");
            }
        } finally { Files.deleteIfExists(unrecognizedJar); }

        // This loader is deliberately raw: both packs must work in the game's
        // own decoder without invoking any resource adapter during decoding.
        try (URLClassLoader raw = new URLClassLoader(urls)) {
            Class<?> pack = raw.loadClass("ep");
            Constructor<?> constructor = pack.getDeclaredConstructor(InputStream.class);
            constructor.setAccessible(true);
            Field count = null;
            Method decode = null;
            for (Field field : pack.getDeclaredFields())
                if (field.getName().equals("a") && field.getType() == int.class) count = field;
            for (Method method : pack.getDeclaredMethods())
                if (method.getName().equals("a") && method.getReturnType() == Image.class
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class})) decode = method;
            count.setAccessible(true);
            decode.setAccessible(true);
            Object actualPack = constructor.newInstance(new ByteArrayInputStream(actual));
            Object expectedPack = constructor.newInstance(new ByteArrayInputStream(expected));
            require(count.getInt(actualPack) == count.getInt(expectedPack), "same image count");
            for (int i = 0; i < count.getInt(actualPack); i++) {
                Image a = (Image)decode.invoke(actualPack, i), e = (Image)decode.invoke(expectedPack, i);
                require(a.getWidth() == e.getWidth() && a.getHeight() == e.getHeight(), "same dimensions");
                int[] aPixels = new int[a.getWidth() * a.getHeight()], ePixels = new int[aPixels.length];
                a.getRGB(aPixels, 0, a.getWidth(), 0, 0, a.getWidth(), a.getHeight());
                e.getRGB(ePixels, 0, e.getWidth(), 0, 0, e.getWidth(), e.getHeight());
                require(Arrays.equals(aPixels, ePixels), "same ARGB pixels as device-tested JAR");
            }
        }
        require(Arrays.equals(originalHash, digest(Files.readAllBytes(originalJar.toPath()))),
                "original JAR SHA-256 unchanged");
        try (JarFile jar = new JarFile(originalJar)) {
            require(Arrays.equals(original, read(jar.getInputStream(jar.getJarEntry(FONT)))),
                    "original resource still filtered on disk");
        }
        System.out.println("GameResourceCompatibilitySmoke: " + checks
                + " checks passed; original JAR untouched, fingerprint guards, resource APIs/cache, ARGB oracle; no MIDlet startup/network");
    }

    private static byte[] digest(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }
    private static byte[] read(InputStream source) throws IOException {
        if (source == null) throw new AssertionError("null stream");
        try (InputStream input = source) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
