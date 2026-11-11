package org.recompile.freej2me.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class GameCollectionStore {
    private static final String PREFERENCES = "j2mex_collections";
    private static final String COLLECTIONS_KEY = "collections";

    static final class Collection {
        final String id;
        String name;
        final ArrayList<String> gamePaths = new ArrayList<>();
        boolean expanded;

        Collection(String name) {
            this(UUID.randomUUID().toString(), name);
        }

        Collection(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private GameCollectionStore() { }

    static ArrayList<Collection> load(Context context) throws IOException {
        ArrayList<Collection> collections = new ArrayList<>();
        File file = privateLibraryFile(context);
        File legacyFile = GameStorage.libraryFile(context);
        String serialized;
        if (GameStorage.hasAtomicFile(file)) {
            serialized = readText(file);
            deleteLegacyFile(legacyFile);
        } else if (GameStorage.hasAtomicFile(legacyFile)) {
            if (legacyCollectionsPrecedeInstall(context, legacyFile)) {
                // A pre-install shared copy means the app was removed before this
                // version had a chance to migrate it; honor the user's uninstall choice.
                deleteLegacyFile(legacyFile);
                serialized = preferences(context).getString(COLLECTIONS_KEY, "[]");
            } else {
                serialized = readText(legacyFile);
                GameStorage.writeAtomic(file, serialized.getBytes(StandardCharsets.UTF_8));
                deleteLegacyFile(legacyFile);
            }
        } else {
            serialized = preferences(context).getString(COLLECTIONS_KEY, "[]");
        }
        try {
            JSONArray entries = new JSONArray(serialized);
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) continue;
                String id = entry.optString("id", "");
                String name = entry.optString("name", "").trim();
                if (id.isEmpty() || name.isEmpty()) continue;
                Collection collection = new Collection(id, name);
                collection.expanded = entry.optBoolean("expanded", false);
                JSONArray paths = entry.optJSONArray("games");
                if (paths != null) {
                    for (int j = 0; j < paths.length(); j++) {
                        String path = paths.optString(j, "");
                        if (!path.isEmpty() && !collection.gamePaths.contains(path)) {
                            collection.gamePaths.add(path);
                        }
                    }
                }
                collections.add(collection);
            }
        } catch (Exception ignored) {
            // A malformed saved value should not prevent the game library from opening.
        }
        if (!GameStorage.hasAtomicFile(file) && !collections.isEmpty()) save(context, collections);
        preferences(context).edit().remove(COLLECTIONS_KEY).apply();
        return collections;
    }

    static void save(Context context, List<Collection> collections) throws IOException {
        JSONArray entries = new JSONArray();
        for (Collection collection : collections) {
            JSONObject entry = new JSONObject();
            JSONArray paths = new JSONArray();
            for (String path : collection.gamePaths) paths.put(path);
            try {
                entry.put("id", collection.id);
                entry.put("name", collection.name);
                entry.put("expanded", collection.expanded);
                entry.put("games", paths);
                entries.put(entry);
            } catch (Exception ignored) {
                // Primitive JSON values are not expected to fail serialization.
            }
        }
        GameStorage.writeAtomic(privateLibraryFile(context),
                entries.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static File privateLibraryFile(Context context) {
        return new File(context.getFilesDir(), "library.json");
    }

    private static boolean legacyCollectionsPrecedeInstall(Context context, File file)
            throws IOException {
        long lastModified = Math.max(file.lastModified(),
                Math.max(new File(file.getPath() + ".bak").lastModified(),
                        new File(file.getPath() + ".new").lastModified()));
        if (lastModified <= 0) return false;
        try {
            long firstInstall = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).firstInstallTime;
            return firstInstall > 0 && lastModified < firstInstall;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            throw new IOException("Không thể kiểm tra dữ liệu bộ sưu tập cũ.", error);
        }
    }

    private static void deleteLegacyFile(File file) throws IOException {
        File[] legacyFiles = {file, new File(file.getPath() + ".bak"),
                new File(file.getPath() + ".new")};
        for (File legacy : legacyFiles) {
            if (legacy.exists() && !legacy.delete()) {
                throw new IOException("Không thể xóa bản bộ sưu tập cũ khỏi thư mục làm việc.");
            }
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream input = new AtomicFile(file).openRead();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
