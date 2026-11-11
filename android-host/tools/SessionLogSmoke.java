package org.recompile.freej2me.lite;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class SessionLogSmoke {
    private static String export(File root) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        SessionLog.export(root, result);
        return result.toString("UTF-8");
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("freej2me-session-").toFile();
        try {
            File work = new File(root, "games/test/data");
            if (!work.mkdirs()) throw new IOException("Test directory creation failed");
            File stale = new File(work, "hs_err_pid1.log");
            Files.write(stale.toPath(), "STALE_REPORT".getBytes(StandardCharsets.UTF_8));
            if (!stale.setLastModified(System.currentTimeMillis() - 60000)) throw new IOException("Cannot set timestamp");
            SessionLog.begin(root, work, "CURRENT_CONSOLE\n");
            String normal = export(root);
            if (!normal.contains("CURRENT_CONSOLE") || normal.contains("STALE_REPORT"))
                throw new AssertionError("Normal export included an old report");
            File crash = new File(work, "hs_err_pid2.log");
            Files.write(crash.toPath(), "CURRENT_NATIVE_BACKTRACE".getBytes(StandardCharsets.UTF_8));
            String withCrash = export(root);
            if (!withCrash.contains("CURRENT_NATIVE_BACKTRACE") || withCrash.contains("STALE_REPORT"))
                throw new AssertionError("Current native report missing or stale report included");
            File differentGame = new File(root, "games/other/data");
            if (!differentGame.mkdirs()) throw new IOException("Test directory creation failed");
            SessionLog.begin(root, differentGame, "OTHER_GAME\n");
            if (export(root).contains("CURRENT_NATIVE_BACKTRACE"))
                throw new AssertionError("Export mixed different game sessions");
            System.out.println("PASS: console export; current native crash included; stale/other-game reports excluded");
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
                });
            }
        }
    }
}
