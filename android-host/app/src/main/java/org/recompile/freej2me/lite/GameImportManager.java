package org.recompile.freej2me.lite;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.recompile.freej2me.protocol.JarManifestReader;

/** Imports MIDlet archives without launching them. */
final class GameImportManager {
    private static final int MAX_INPUT_MIB = 512;
    private static final long MAX_INPUT_BYTES = MAX_INPUT_MIB * 1024L * 1024L;

    static final class Result {
        int imported;
        int duplicates;
        final ArrayList<GameLibrary.Game> games = new ArrayList<>();
        final ArrayList<String> errors = new ArrayList<>();

        String summary() {
            StringBuilder text = new StringBuilder();
            text.append("Đã thêm ").append(imported).append(" game");
            if (duplicates > 0) text.append(", ").append(duplicates).append(" game đã có sẵn");
            text.append('.');
            if (!errors.isEmpty()) {
                text.append("\n\nĐã bỏ qua ").append(errors.size()).append(" mục bị lỗi:");
                int shown = Math.min(errors.size(), 12);
                for (int i = 0; i < shown; i++) text.append("\n• ").append(errors.get(i));
                if (errors.size() > shown) {
                    text.append("\n• Và ").append(errors.size() - shown).append(" lỗi khác.");
                }
            }
            return text.toString();
        }

        String summary(Context context) {
            if (!AppLocalization.isEnglish(context)) return summary();
            StringBuilder text = new StringBuilder();
            text.append("Added ").append(imported).append(imported == 1 ? " game" : " games");
            if (duplicates > 0) {
                text.append(", ").append(duplicates)
                        .append(duplicates == 1 ? " already in the library" : " already in the library");
            }
            text.append('.');
            if (!errors.isEmpty()) {
                text.append("\n\nSkipped ").append(errors.size()).append(" item(s) with errors:");
                int shown = Math.min(errors.size(), 12);
                for (int i = 0; i < shown; i++) {
                    text.append("\n• ").append(AppLocalization.text(context, errors.get(i)));
                }
                if (errors.size() > shown) {
                    text.append("\n• And ").append(errors.size() - shown).append(" more errors.");
                }
            }
            return text.toString();
        }
    }

    private static final class SourceFile {
        final Uri uri;
        final File file;
        final String name;

        SourceFile(Uri uri, String name) {
            this.uri = uri;
            this.file = null;
            this.name = name;
        }

        SourceFile(File file) {
            this.uri = null;
            this.file = file;
            this.name = file.getName();
        }

        InputStream open(Context context) throws IOException {
            return file != null ? new java.io.FileInputStream(file)
                    : context.getContentResolver().openInputStream(uri);
        }
    }

    private GameImportManager() { }

    static Result importDocuments(Context context, List<Uri> uris) {
        Result result = new Result();
        ArrayList<SourceFile> files = new ArrayList<>();
        for (Uri uri : uris) {
            try {
                String name = displayName(context, uri);
                if (!isSupported(name)) name = nameFromMimeType(context, uri, name);
                if (isSupported(name)) files.add(new SourceFile(uri, name));
                else result.errors.add(name + ": chỉ hỗ trợ JAR, JAD hoặc ZIP.");
            } catch (Exception error) {
                result.errors.add(uri.toString() + ": " + message(error));
            }
        }
        importSources(context, files, result);
        if (files.isEmpty() && result.errors.isEmpty()) {
            result.errors.add("Không có tệp JAR, JAD hoặc ZIP để nhập.");
        }
        return result;
    }

    static Result importFiles(Context context, List<File> selectedFiles) {
        Result result = new Result();
        ArrayList<SourceFile> files = new ArrayList<>();
        for (File file : selectedFiles) {
            if (file == null || !file.isFile() || !file.canRead()) {
                result.errors.add((file == null ? "Tệp không xác định" : file.getName())
                        + ": không thể đọc tệp.");
            } else if (isSupported(file.getName())) {
                files.add(new SourceFile(file));
            } else {
                result.errors.add(file.getName() + ": chỉ hỗ trợ JAR, JAD hoặc ZIP.");
            }
        }
        importSources(context, files, result);
        if (files.isEmpty() && result.errors.isEmpty()) {
            result.errors.add("Không có tệp JAR, JAD hoặc ZIP để nhập.");
        }
        return result;
    }

    static Result importDirectory(Context context, File selectedDirectory) {
        ArrayList<String> scanErrors = new ArrayList<>();
        ArrayList<File> archives = new ArrayList<>();
        File root;
        try {
            root = selectedDirectory.getCanonicalFile();
        } catch (Exception error) {
            Result result = new Result();
            result.errors.add("Không thể mở thư mục: " + message(error));
            return result;
        }
        if (!root.isDirectory() || !root.canRead()) {
            Result result = new Result();
            result.errors.add(root.getName() + ": không thể đọc thư mục.");
            return result;
        }

        String rootPath = root.getAbsolutePath();
        ArrayDeque<File> pending = new ArrayDeque<>();
        HashSet<String> visited = new HashSet<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            File directory = pending.removeFirst();
            String directoryPath;
            try {
                directoryPath = directory.getCanonicalPath();
            } catch (Exception error) {
                scanErrors.add(directory.getAbsolutePath() + ": " + message(error));
                continue;
            }
            if (!visited.add(directoryPath)) continue;
            File[] children;
            try {
                children = directory.listFiles();
            } catch (Exception error) {
                scanErrors.add(directory.getName() + ": " + message(error));
                continue;
            }
            if (children == null) {
                scanErrors.add(directory.getName() + ": không thể đọc nội dung thư mục.");
                continue;
            }
            for (File child : children) {
                try {
                    if (child.isDirectory()) {
                        if (child.getName().startsWith(".")) continue;
                        File canonicalChild = child.getCanonicalFile();
                        String childPath = canonicalChild.getAbsolutePath();
                        if (isWithinRoot(rootPath, childPath) && child.canRead()) {
                            pending.addLast(canonicalChild);
                        }
                    } else if (isSupported(child.getName())) {
                        File canonicalChild = child.getCanonicalFile();
                        if (isWithinRoot(rootPath, canonicalChild.getAbsolutePath())) {
                            archives.add(canonicalChild);
                        }
                    }
                } catch (Exception error) {
                    scanErrors.add(child.getName() + ": " + message(error));
                }
            }
        }

        Result result = importFiles(context, archives);
        if (!scanErrors.isEmpty()) result.errors.addAll(0, scanErrors);
        return result;
    }

    private static boolean isWithinRoot(String rootPath, String candidatePath) {
        return candidatePath.equals(rootPath)
                || candidatePath.startsWith(rootPath + File.separator);
    }

    private static void importSources(Context context, List<SourceFile> files, Result result) {
        if (files.isEmpty()) return;
        for (SourceFile source : files) {
            String lower = source.name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".jad")) continue;
            try {
                String companionName = jadJarName(context, source);
                boolean found = false;
                for (SourceFile candidate : files) {
                    if (!candidate.name.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                    if (candidate.name.equalsIgnoreCase(companionName)) {
                        if (source.file != null && candidate.file != null) {
                            File sourceParent = source.file.getParentFile().getCanonicalFile();
                            File candidateParent = candidate.file.getParentFile().getCanonicalFile();
                            if (!sourceParent.equals(candidateParent)) continue;
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    result.errors.add(source.name
                            + ": JAD chỉ là descriptor; hãy chọn tệp JAR đi kèm cùng lúc.");
                }
            } catch (Exception error) {
                result.errors.add(source.name + ": " + message(error));
            }
        }

        for (SourceFile source : files) {
            String lower = source.name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jad")) continue;
            try {
                importSource(context, source, result);
            } catch (Exception error) {
                result.errors.add(source.name + ": " + message(error));
            }
        }
    }

    private static String jadJarName(Context context, SourceFile source) throws IOException {
        String descriptor = readText(context, source);
        for (String line : descriptor.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0 || !line.substring(0, colon).trim()
                    .equalsIgnoreCase("MIDlet-Jar-URL")) continue;
            String value = line.substring(colon + 1).trim();
            int query = value.indexOf('?');
            if (query >= 0) value = value.substring(0, query);
            int slash = value.lastIndexOf('/');
            if (slash >= 0) value = value.substring(slash + 1);
            if (!value.isEmpty()) return value;
        }
        String name = source.name;
        return name.substring(0, name.length() - 4) + ".jar";
    }

    private static String readText(Context context, SourceFile source) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream input = source.open(context)) {
            if (input == null) throw new IOException("Không đọc được descriptor.");
            byte[] buffer = new byte[2048];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > 64 * 1024) throw new IOException("JAD vượt 64 KiB.");
                text.append(new String(buffer, 0, count, "ISO-8859-1"));
            }
        }
        return text.toString();
    }

    private static void importSource(Context context, SourceFile source, Result result)
            throws Exception {
        File temporary = File.createTempFile("game-import-", ".bin", context.getCacheDir());
        try {
            copyToFile(source.open(context), temporary, MAX_INPUT_BYTES);
            try {
                importJar(context, temporary, source.name, result);
                return;
            } catch (IOException notDirectJar) {
                if (!source.name.toLowerCase(Locale.ROOT).endsWith(".zip")) throw notDirectJar;
            }
            importNestedJars(context, temporary, source.name, result);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static void importNestedJars(Context context, File archive, String archiveName,
            Result result) throws IOException {
        int found = 0;
        int candidates = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                candidates++;
                String nestedName = new File(entry.getName()).getName();
                File nested = File.createTempFile("nested-game-", ".jar", context.getCacheDir());
                try {
                    try (InputStream input = zip.getInputStream(entry)) {
                        copyToFile(input, nested, MAX_INPUT_BYTES);
                    }
                    importJar(context, nested, nestedName, result);
                    found++;
                } catch (Exception error) {
                    result.errors.add(archiveName + " (" + nestedName + "): " + message(error));
                } finally {
                    if (nested.exists()) nested.delete();
                }
            }
        } catch (IOException error) {
            throw new IOException("Không đọc được tệp ZIP và không có JAR hợp lệ bên trong.", error);
        }
        if (candidates == 0) throw new IOException("ZIP không chứa tệp JAR.");
        // Invalid nested archives already have individual errors in the result.
    }

    private static void importJar(Context context, File jar, String sourceName, Result result)
            throws Exception {
        validateJar(jar);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(jar)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) {
            hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        String gameId = hash.toString();
        File gamesRoot = GameStorage.gamesDirectory(context);
        GameLibrary.Game importedGame = GameLibrary.readGame(jar, sourceName);
        File directory = new File(gamesRoot,
                GameLibrary.directoryName(importedGame.title, gameId));
        File legacyDirectory = new File(gamesRoot, gameId);
        if (!directory.exists() && legacyDirectory.isDirectory()) {
            directory = legacyDirectory;
        }
        HostFiles.mkdir(directory);
        File gameJar = new File(directory, "game.jar");
        if (gameJar.exists()) {
            result.duplicates++;
        } else {
            File staging = File.createTempFile("game-verified-", ".jar", context.getCacheDir());
            try {
                copyFile(jar, staging);
                HostFiles.moveFile(staging, gameJar);
            } finally {
                if (staging.exists()) staging.delete();
            }
            result.imported++;
        }
        GameLibrary.saveDisplayName(directory, sourceName);
        result.games.add(new GameLibrary.Game(
                gameJar, importedGame.title, sourceName, importedGame.icon));
    }

    private static void validateJar(File file) throws IOException {
        try (JarFile jar = new JarFile(file)) {
            Manifest manifest = JarManifestReader.read(jar);
            String entry = manifest == null ? null
                    : manifest.getMainAttributes().getValue("MIDlet-1");
            if (entry == null || entry.split(",", -1).length != 3) {
                throw new IOException("không có MIDlet-1 hợp lệ trong manifest.");
            }
            String className = entry.split(",", -1)[2].trim().replace('.', '/') + ".class";
            if (jar.getJarEntry(className) == null) {
                throw new IOException("không tìm thấy lớp MIDlet trong JAR.");
            }
        }
    }

    private static void copyToFile(InputStream input, File output, long limit) throws IOException {
        if (input == null) throw new IOException("Không mở được tệp.");
        try (InputStream source = input; OutputStream target = new FileOutputStream(output)) {
            byte[] buffer = new byte[65536];
            int count;
            long total = 0;
            while ((count = source.read(buffer)) != -1) {
                total += count;
                if (total > limit) {
                    throw new IOException("Tệp vượt giới hạn " + MAX_INPUT_MIB + " MiB.");
                }
                target.write(buffer, 0, count);
            }
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (InputStream input = new java.io.FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? uri.toString() : last;
    }

    private static String nameFromMimeType(Context context, Uri uri, String name) {
        String mimeType;
        try {
            mimeType = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {
            return name;
        }
        if (mimeType == null) return name;
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.equals("application/java-archive")
                || lower.equals("application/x-java-archive")
                || lower.equals("application/x-jar")) {
            return name + ".jar";
        }
        if (lower.equals("application/zip")
                || lower.equals("application/x-zip")
                || lower.equals("application/x-zip-compressed")) {
            return name + ".zip";
        }
        return name;
    }

    private static boolean isSupported(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".jad") || lower.endsWith(".zip");
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
                ? error.getClass().getSimpleName() : value;
    }
}
