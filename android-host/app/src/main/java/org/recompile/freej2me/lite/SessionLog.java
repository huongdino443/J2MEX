package org.recompile.freej2me.lite;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.util.Properties;

/** Export the last session's console and fatal JVM report, not another game's old crash. */
final class SessionLog {
    static File sessionDirectory(File appRoot, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return appRoot;
        return new File(new File(appRoot, "runtime-sessions"), sessionId);
    }

    static synchronized void append(File root, String line) throws IOException {
        String value = line == null ? "" : line;
        if (value.length() > 8192) value = value.substring(0, 8192);
        try (OutputStream output = new FileOutputStream(new File(root, "latestlog.txt"), true)) {
            output.write((value + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    static synchronized void appendUncaught(File root, Thread thread, Throwable error)
            throws IOException {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        String report = "\n\n===== Uncaught Android runtime exception =====\n"
                + "Thread=" + (thread == null ? "unknown" : thread.getName()) + "\n"
                + stack;
        byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
        int limit = 1024 * 1024;
        try (FileOutputStream output =
                     new FileOutputStream(new File(root, "latestlog.txt"), true)) {
            output.write(bytes, 0, Math.min(bytes.length, limit));
            if (bytes.length > limit) {
                output.write("\n[Exception report truncated at 1 MiB]\n"
                        .getBytes(StandardCharsets.UTF_8));
            }
            output.flush();
            output.getFD().sync();
        }
    }

    static void begin(File root, File work, String header) throws IOException {
        begin(root, work, header, -1);
    }

    static synchronized void begin(File root, File work, String header, int processId)
            throws IOException {
        File marker = new File(root, "last-session.properties");
        Properties previous = readSessionMarker(marker);
        Properties session = new Properties();
        session.setProperty("directory", work.getCanonicalPath());
        session.setProperty("startedAt", Long.toString(System.currentTimeMillis()));
        session.setProperty("processId", Integer.toString(processId));
        session.setProperty("runtimeEnded", "false");
        File[] existingReports = work.listFiles();
        if (existingReports != null) {
            int index = 0;
            for (File file : existingReports) {
                if (file.isFile() && file.getName().matches("hs_err_pid[0-9]+\\.log"))
                    session.setProperty("existingCrash." + index++, file.getName());
            }
        }
        StringBuilder sessionHeader = new StringBuilder(header == null ? "" : header);
        if ("false".equals(previous.getProperty("runtimeEnded", "true"))) {
            String previousPid = previous.getProperty("processId", "");
            if (!previousPid.isEmpty()
                    && !Integer.toString(processId).equals(previousPid)) {
                sessionHeader.append("[previous-session] process changed from pid=")
                        .append(previousPid).append(" to pid=").append(processId)
                        .append(" before a clean runtime end; check for an OS kill or JVM crash.\n");
            } else {
                sessionHeader.append("[previous-session] previous runtime had no clean end "
                        + "marker; current process pid=").append(processId).append(".\n");
            }
        }
        try (FileOutputStream output = new FileOutputStream(new File(root, "latestlog.txt"))) {
            output.write(sessionHeader.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        try (FileOutputStream output = new FileOutputStream(marker)) {
            session.store(output, "Last FreeJ2ME runtime session");
            output.flush();
            output.getFD().sync();
        }
    }

    static synchronized void beginRuntimeMarker(File root, File work, int processId)
            throws IOException {
        File marker = new File(root, "last-session.properties");
        Properties session = new Properties();
        session.setProperty("directory", work.getCanonicalPath());
        session.setProperty("startedAt", Long.toString(System.currentTimeMillis()));
        session.setProperty("processId", Integer.toString(processId));
        session.setProperty("runtimeEnded", "false");
        writeSessionMarker(marker, session);
    }

    static synchronized boolean wasReplacedWithoutCleanEnd(File root, int currentProcessId) {
        Properties session = readSessionMarker(new File(root, "last-session.properties"));
        if (!"false".equals(session.getProperty("runtimeEnded", "true"))) return false;
        String previousPid = session.getProperty("processId", "");
        return !previousPid.isEmpty()
                && !Integer.toString(currentProcessId).equals(previousPid);
    }

    static synchronized int activeRuntimeProcessId(File root) {
        Properties session = readSessionMarker(new File(root, "last-session.properties"));
        if (!"false".equals(session.getProperty("runtimeEnded", "true"))) return -1;
        try {
            int processId = Integer.parseInt(session.getProperty("processId", ""));
            return processId > 0 ? processId : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static synchronized void markRuntimeEnded(File root, int processId, String reason) {
        File marker = new File(root, "last-session.properties");
        if (!marker.isFile()) return;
        Properties session = readSessionMarker(marker);
        if (!Integer.toString(processId).equals(session.getProperty("processId", ""))) return;
        session.setProperty("runtimeEnded", "true");
        session.setProperty("endedAt", Long.toString(System.currentTimeMillis()));
        session.setProperty("endReason", reason == null ? "unknown" : reason);
        try { writeSessionMarker(marker, session); } catch (IOException ignored) { }
    }

    private static Properties readSessionMarker(File marker) {
        Properties session = new Properties();
        if (!marker.isFile()) return session;
        try (InputStream input = new FileInputStream(marker)) {
            session.load(input);
        } catch (IOException ignored) { }
        return session;
    }

    private static void writeSessionMarker(File marker, Properties session) throws IOException {
        try (FileOutputStream output = new FileOutputStream(marker)) {
            session.store(output, "Last FreeJ2ME runtime session");
            output.flush();
            output.getFD().sync();
        }
    }

    static void export(File root, File gamesRoot, OutputStream output) throws IOException {
        try (InputStream console = new FileInputStream(new File(root, "latestlog.txt"))) {
            transfer(console, output, Long.MAX_VALUE);
        }
        File marker = new File(root, "last-session.properties");
        if (!marker.isFile()) return;
        Properties session = new Properties();
        try (InputStream input = new FileInputStream(marker)) { session.load(input); }
        File directory = new File(session.getProperty("directory", "")).getCanonicalFile();
        if (!isChildOf(root, directory) && !isGameDataDirectory(gamesRoot, directory)) {
            output.write(("\n\n[Native JVM crash report not included: "
                    + "the run folder could not be verified]\n")
                    .getBytes(StandardCharsets.UTF_8));
            return;
        }
        long startedAt;
        try { startedAt = Long.parseLong(session.getProperty("startedAt", "")); }
        catch (NumberFormatException error) { throw new IOException("Invalid session timestamp", error); }
        File latest = null;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) {
            if (file.isFile() && file.getName().matches("hs_err_pid[0-9]+\\.log")
                    && file.lastModified() >= startedAt - 5000
                    && !existedBeforeSession(session, file.getName())
                    && file.getCanonicalFile().getParentFile().equals(directory)
                    && (latest == null || file.lastModified() > latest.lastModified())) latest = file;
        }
        if (latest == null) return;
        output.write(("\n\n===== Native JVM crash report: " + latest.getName() + " =====\n")
                .getBytes(StandardCharsets.UTF_8));
        try (InputStream report = new FileInputStream(latest)) {
            transfer(report, output, 1024 * 1024);
            if (report.read() != -1) output.write("\n[Crash report truncated at 1 MiB]\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    static void promote(File sessionRoot, File appRoot) throws IOException {
        copyIfPresent(new File(sessionRoot, "latestlog.txt"),
                new File(appRoot, "latestlog.txt"));
        copyIfPresent(new File(sessionRoot, "last-session.properties"),
                new File(appRoot, "last-session.properties"));
    }

    private static void copyIfPresent(File source, File destination) throws IOException {
        if (!source.isFile()) return;
        File parent = destination.getParentFile();
        if (parent == null) throw new IOException("Missing runtime log destination folder");
        File temporary = File.createTempFile("latestlog-", ".tmp", parent);
        try (InputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            output.getFD().sync();
        }
        if (!temporary.renameTo(destination)) {
            if (destination.exists() && !destination.delete()) {
                temporary.delete();
                throw new IOException("Cannot replace promoted runtime log");
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                throw new IOException("Cannot publish promoted runtime log");
            }
        }
    }

    private static boolean existedBeforeSession(Properties session, String filename) {
        for (String key : session.stringPropertyNames()) {
            if (key.startsWith("existingCrash.")
                    && filename.equals(session.getProperty(key))) return true;
        }
        return false;
    }

    private static boolean isChildOf(File parent, File child) throws IOException {
        String parentPath = parent.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        return childPath.startsWith(parentPath + File.separator);
    }

    private static boolean isGameDataDirectory(File gamesRoot, File directory)
            throws IOException {
        if (gamesRoot == null || !"data".equals(directory.getName())) return false;
        File gameDirectory = directory.getParentFile();
        if (gameDirectory == null) return false;
        File gameParent = gameDirectory.getParentFile();
        return gameParent != null
                && gameParent.getCanonicalFile().equals(gamesRoot.getCanonicalFile());
    }

    private static void transfer(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[32768];
        while (limit > 0) {
            int n = input.read(buffer, 0, (int)Math.min(buffer.length, limit));
            if (n == -1) return;
            output.write(buffer, 0, n); limit -= n;
        }
    }
}
