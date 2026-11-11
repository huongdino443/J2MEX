package org.recompile.freej2me.lite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.recompile.freej2me.protocol.JarManifestReader;

final class GameLibrary {
    private static final String TAG = "J2MEX-Library";

    static final class Game {
        final File jar;
        final String title;
        final String sourceName;
        final Bitmap icon;
        final String previousJarPath;

        Game(File jar, String title, String sourceName, Bitmap icon) {
            this(jar, title, sourceName, icon, null);
        }

        Game(File jar, String title, String sourceName, Bitmap icon, String previousJarPath) {
            this.jar = jar;
            this.title = title;
            this.sourceName = sourceName;
            this.icon = icon;
            this.previousJarPath = previousJarPath;
        }
    }

    private GameLibrary() { }

    static List<Game> scan(Activity activity) throws IOException {
        ArrayList<Game> games = new ArrayList<>();
        File root = GameStorage.gamesDirectory(activity);
        File[] directories = root.listFiles();
        if (directories == null)
            throw new IOException("Không thể đọc thư mục thư viện game.");

        for (File directory : directories) {
            File jar = new File(directory, "game.jar");
            if (!jar.isFile()) continue;
            File nameFile = new File(directory, "display-name.txt");
            String sourceName = readName(nameFile);
            if (sourceName.isEmpty()) sourceName = "MIDlet.jar";
            Game game;
            try {
                game = readGame(jar, sourceName);
            } catch (IOException error) {
                Log.w(TAG, "Could not read game metadata: " + jar.getName(), error);
                game = new Game(jar, withoutExtension(sourceName), sourceName, null);
            }

            File renamedDirectory = migrateHashOnlyDirectory(root, directory, game.title);
            if (renamedDirectory != directory) {
                String previousJarPath = jar.getAbsolutePath();
                jar = new File(renamedDirectory, "game.jar");
                game = new Game(jar, game.title, game.sourceName, game.icon, previousJarPath);
            }
            games.add(game);
        }
        return games;
    }

    static String directoryName(String title, String hash) {
        StringBuilder readable = new StringBuilder();
        boolean separator = false;
        String value = title == null ? "" : title.trim();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                readable.appendCodePoint(codePoint);
                separator = false;
            } else if (!separator) {
                readable.append('_');
                separator = true;
            }
        }
        String slug = readable.toString().replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) slug = "Game";
        int maxCharacters = 48;
        int count = slug.codePointCount(0, slug.length());
        if (count > maxCharacters) {
            slug = slug.substring(0, slug.offsetByCodePoints(0, maxCharacters));
        }
        return slug + "--" + hash;
    }

    private static File migrateHashOnlyDirectory(File root, File directory, String title) {
        String hash = directory.getName();
        if (!isSha256(hash)) return directory;
        File target = new File(root, directoryName(title, hash));
        if (target.exists()) {
            Log.w(TAG, "Keeping legacy game folder because its readable folder already exists: "
                    + directory.getName());
            return directory;
        }
        if (!directory.renameTo(target)) {
            Log.w(TAG, "Could not rename legacy game folder: " + directory.getName());
            return directory;
        }
        return target;
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= '0' && character <= '9')
                    && !(character >= 'a' && character <= 'f')) return false;
        }
        return true;
    }

    static Game readGame(File jarFile, String sourceName) throws IOException {
        String fallback = withoutExtension(sourceName);
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = JarManifestReader.read(jar);
            Attributes attributes = manifest == null ? null : manifest.getMainAttributes();
            String descriptor = attributes == null ? null : attributes.getValue("MIDlet-1");
            String title = firstNonEmpty(
                    attributes == null ? null : attributes.getValue("MIDlet-Name"),
                    descriptorTitle(descriptor),
                    fallback);
            String iconPath = attributes == null ? null : attributes.getValue("MIDlet-Icon");
            if (iconPath == null || iconPath.trim().isEmpty()) iconPath = descriptorIcon(descriptor);
            Bitmap icon = readIcon(jar, iconPath);
            return new Game(jarFile, title, sourceName, icon);
        }
    }

    static void saveDisplayName(File gameDirectory, String displayName) throws IOException {
        HostFiles.writeFile(new File(gameDirectory, "display-name.txt"),
                displayName.getBytes(StandardCharsets.UTF_8));
    }

    private static Bitmap readIcon(JarFile jar, String path) throws IOException {
        if (path == null || path.trim().isEmpty()) return null;
        String normalized = path.trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        JarEntry entry = jar.getJarEntry(normalized);
        if (entry == null) return null;
        try (InputStream input = jar.getInputStream(entry)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sampleSize = 1;
            while (Math.max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 512) {
                sampleSize *= 2;
            }
            try (InputStream decoded = jar.getInputStream(entry)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                options.inScaled = false;
                return BitmapFactory.decodeStream(decoded, null, options);
            }
        }
    }

    private static String descriptorTitle(String descriptor) {
        if (descriptor == null) return null;
        String[] parts = descriptor.split(",", -1);
        return parts.length > 0 ? parts[0].trim() : null;
    }

    private static String descriptorIcon(String descriptor) {
        if (descriptor == null) return null;
        String[] parts = descriptor.split(",", -1);
        return parts.length > 1 ? parts[1].trim() : null;
    }

    private static String readName(File file) {
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) Math.min(file.length(), 4096)];
            int count = input.read(bytes);
            return count <= 0 ? "" : new String(bytes, 0, count, StandardCharsets.UTF_8).trim();
        } catch (IOException error) {
            Log.w(TAG, "Could not read saved display name", error);
            return "";
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "MIDlet";
    }

    private static String withoutExtension(String value) {
        if (value == null || value.trim().isEmpty()) return "MIDlet";
        String name = new File(value.trim()).getName();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.isEmpty() ? "MIDlet" : name;
    }
}
