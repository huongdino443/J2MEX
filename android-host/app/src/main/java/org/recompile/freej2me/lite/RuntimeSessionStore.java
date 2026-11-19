package org.recompile.freej2me.lite;

import android.content.Context;
import android.content.Intent;
import android.util.AtomicFile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Small cross-process registry for the five-runtime session pool. */
final class RuntimeSessionStore {
    static final int SLOT_COUNT = 5;
    static final String EXTRA_SESSION_ID = "org.recompile.freej2me.lite.SESSION_ID";
    static final String EXTRA_SESSION_SLOT = "org.recompile.freej2me.lite.SESSION_SLOT";
    static final String EXTRA_OPEN_SESSION_MANAGER =
            "org.recompile.freej2me.lite.OPEN_SESSION_MANAGER";
    static final String EXTRA_RETURN_TO_SESSION =
            "org.recompile.freej2me.lite.RETURN_TO_SESSION";
    static final String EXTRA_CLOSE_SESSION =
            "org.recompile.freej2me.lite.CLOSE_SESSION";
    static final String ACTION_AUDIO_OWNER_CHANGED =
            "org.recompile.freej2me.lite.AUDIO_OWNER_CHANGED";
    static final String EXTRA_AUDIO_OWNER_ID =
            "org.recompile.freej2me.lite.AUDIO_OWNER_ID";
    static final String ACTION_HOTSPOT_MEMORY_QUERY =
            "org.recompile.freej2me.lite.HOTSPOT_MEMORY_QUERY";
    static final String ACTION_HOTSPOT_MEMORY_UPDATE =
            "org.recompile.freej2me.lite.HOTSPOT_MEMORY_UPDATE";
    static final String EXTRA_HOTSPOT_MEMORY_USED =
            "org.recompile.freej2me.lite.HOTSPOT_MEMORY_USED";
    static final String EXTRA_HOTSPOT_MEMORY_MAX =
            "org.recompile.freej2me.lite.HOTSPOT_MEMORY_MAX";

    private static final int FORMAT_VERSION = 1;
    private static final String STORE_FILE = "runtime-sessions.bin";
    private static final String LOCK_FILE = "runtime-sessions.lock";
    private static final Object PROCESS_LOCK = new Object();

    static final class Session {
        final String id;
        final int slot;
        final String jarPath;
        final String title;
        final int processId;
        final long startedAt;
        final boolean foreground;

        Session(String id, int slot, String jarPath, String title, int processId,
                long startedAt, boolean foreground) {
            this.id = id;
            this.slot = slot;
            this.jarPath = jarPath;
            this.title = title;
            this.processId = processId;
            this.startedAt = startedAt;
            this.foreground = foreground;
        }
    }

    private interface Operation<T> {
        T run(ArrayList<Session> sessions) throws IOException;
    }

    private RuntimeSessionStore() { }

    static String processName(Context context, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT)
            throw new IllegalArgumentException("Unsupported runtime slot " + slot);
        return context.getPackageName() + (slot == 0 ? ":runtime" : ":runtime" + (slot + 1));
    }

    static List<Session> list(Context context) {
        try {
            return access(context, sessions -> {
                Collections.sort(sessions, Comparator.comparingInt(value -> value.slot));
                return new ArrayList<>(sessions);
            }, false);
        } catch (IOException error) {
            android.util.Log.e("J2MEX", "Cannot read runtime session registry", error);
            return Collections.emptyList();
        }
    }

    static Session find(Context context, String id) {
        if (id == null || id.isEmpty()) return null;
        for (Session session : list(context)) {
            if (id.equals(session.id)) return session;
        }
        return null;
    }

    static Session foreground(Context context) {
        for (Session session : list(context)) {
            if (session.foreground) return session;
        }
        return null;
    }

    static void publishAudioOwner(Context context, String sessionId) {
        Intent intent = new Intent(ACTION_AUDIO_OWNER_CHANGED)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_AUDIO_OWNER_ID, sessionId == null ? "" : sessionId);
        context.sendBroadcast(intent);
    }

    static Session findForJar(Context context, String jarPath) {
        if (jarPath == null) return null;
        String canonical = canonicalPath(jarPath);
        for (Session session : list(context)) {
            if (canonical.equals(session.jarPath)) return session;
        }
        return null;
    }

    static Session reserve(Context context, String jarPath, String title) throws IOException {
        if (jarPath == null || jarPath.isEmpty()) throw new IOException("Missing game JAR path");
        String canonical = canonicalPath(jarPath);
        return access(context, sessions -> {
            for (Session session : sessions) {
                if (canonical.equals(session.jarPath)) return session;
            }
            boolean[] occupied = new boolean[SLOT_COUNT];
            for (Session session : sessions) {
                if (session.slot >= 0 && session.slot < occupied.length)
                    occupied[session.slot] = true;
            }
            int slot = -1;
            for (int index = 0; index < occupied.length; index++) {
                if (!occupied[index]) {
                    slot = index;
                    break;
                }
            }
            if (slot < 0) return null;
            Session created = new Session(UUID.randomUUID().toString(), slot, canonical,
                    title == null || title.trim().isEmpty()
                            ? new File(canonical).getName() : title.trim(),
                    -1, System.currentTimeMillis(), false);
            sessions.add(created);
            return created;
        }, true);
    }

    static void setForeground(Context context, String id) throws IOException {
        access(context, sessions -> {
            for (int index = 0; index < sessions.size(); index++) {
                Session old = sessions.get(index);
                boolean foreground = old.id.equals(id);
                if (old.foreground != foreground) {
                    sessions.set(index, new Session(old.id, old.slot, old.jarPath,
                            old.title, old.processId, old.startedAt, foreground));
                }
            }
            return null;
        }, true);
    }

    static void updateProcessId(Context context, String id, int processId) throws IOException {
        if (id == null || processId <= 0) return;
        access(context, sessions -> {
            for (int index = 0; index < sessions.size(); index++) {
                Session old = sessions.get(index);
                if (!old.id.equals(id)) continue;
                sessions.set(index, new Session(old.id, old.slot, old.jarPath, old.title,
                        processId, old.startedAt, old.foreground));
                break;
            }
            return null;
        }, true);
    }

    static void remove(Context context, String id) {
        if (id == null || id.isEmpty()) return;
        try {
            access(context, sessions -> {
                boolean removedForeground = false;
                for (int index = sessions.size() - 1; index >= 0; index--) {
                    Session session = sessions.get(index);
                    if (!id.equals(session.id)) continue;
                    removedForeground |= session.foreground;
                    sessions.remove(index);
                }
                if (removedForeground && !sessions.isEmpty()) {
                    Session replacement = sessions.get(0);
                    sessions.set(0, new Session(replacement.id, replacement.slot,
                            replacement.jarPath, replacement.title, replacement.processId,
                            replacement.startedAt, true));
                }
                return null;
            }, true);
        } catch (IOException error) {
            android.util.Log.e("J2MEX", "Cannot remove runtime session", error);
        } finally {
            RuntimeSessionPreviewStore.delete(context, id);
        }
    }

    private static String canonicalPath(String path) {
        try { return new File(path).getCanonicalPath(); }
        catch (IOException ignored) { return new File(path).getAbsolutePath(); }
    }

    private static <T> T access(Context context, Operation<T> operation, boolean write)
            throws IOException {
        synchronized (PROCESS_LOCK) {
            File lockFile = new File(context.getFilesDir(), LOCK_FILE);
            File parent = lockFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                throw new IOException("Cannot create session registry directory");
            try (RandomAccessFile lockAccess = new RandomAccessFile(lockFile, "rw");
                    FileLock ignored = lockAccess.getChannel().lock()) {
                AtomicFile file = new AtomicFile(new File(context.getFilesDir(), STORE_FILE));
                ArrayList<Session> sessions = read(file);
                T result = operation.run(sessions);
                if (write) write(file, sessions);
                return result;
            }
        }
    }

    private static ArrayList<Session> read(AtomicFile file) throws IOException {
        ArrayList<Session> sessions = new ArrayList<>();
        if (!file.getBaseFile().exists()) return sessions;
        try (FileInputStream raw = file.openRead();
                DataInputStream input = new DataInputStream(raw)) {
            if (input.readInt() != FORMAT_VERSION)
                throw new IOException("Unsupported runtime session registry version");
            int count = input.readInt();
            if (count < 0 || count > SLOT_COUNT)
                throw new IOException("Invalid runtime session count");
            for (int index = 0; index < count; index++) {
                String id = input.readUTF();
                int slot = input.readInt();
                String path = input.readUTF();
                String title = input.readUTF();
                int processId = input.readInt();
                long startedAt = input.readLong();
                boolean foreground = input.readBoolean();
                if (id.isEmpty() || slot < 0 || slot >= SLOT_COUNT || path.isEmpty())
                    throw new IOException("Invalid runtime session record");
                sessions.add(new Session(id, slot, path, title, processId,
                        startedAt, foreground));
            }
        } catch (java.io.FileNotFoundException missing) {
            return sessions;
        } catch (java.io.EOFException truncated) {
            throw new IOException("Runtime session registry is truncated", truncated);
        }
        return sessions;
    }

    private static void write(AtomicFile file, List<Session> sessions) throws IOException {
        FileOutputStream raw = file.startWrite();
        try {
            DataOutputStream output = new DataOutputStream(raw);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(sessions.size());
            for (Session session : sessions) {
                output.writeUTF(session.id);
                output.writeInt(session.slot);
                output.writeUTF(session.jarPath);
                output.writeUTF(session.title);
                output.writeInt(session.processId);
                output.writeLong(session.startedAt);
                output.writeBoolean(session.foreground);
            }
            output.flush();
            raw.getFD().sync();
            file.finishWrite(raw);
        } catch (IOException | RuntimeException error) {
            file.failWrite(raw);
            if (error instanceof IOException) throw (IOException) error;
            throw error;
        }
    }
}
