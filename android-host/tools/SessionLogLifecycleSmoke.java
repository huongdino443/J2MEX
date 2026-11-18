package org.recompile.freej2me.lite;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class SessionLogLifecycleSmoke {
    private static String readLog(File root) throws IOException {
        return new String(Files.readAllBytes(new File(root, "latestlog.txt").toPath()),
                StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("j2mex-lifecycle-log-").toFile();
        try {
            File work = new File(root, "games/test/data");
            if (!work.mkdirs()) throw new IOException("Could not create game data folder");

            SessionLog.begin(root, work, "SESSION_ONE\n", 101);
            if (SessionLog.wasReplacedWithoutCleanEnd(root, 101)
                    || !SessionLog.wasReplacedWithoutCleanEnd(root, 202)
                    || SessionLog.activeRuntimeProcessId(root) != 101) {
                throw new AssertionError("Active runtime PID was not tracked correctly");
            }
            SessionLog.begin(root, work, "SESSION_TWO\n", 202);
            String processChanged = readLog(root);
            if (!processChanged.contains("[previous-session] process changed from pid=101 to pid=202")) {
                throw new AssertionError("A replaced runtime process was not reported");
            }

            SessionLog.markRuntimeEnded(root, 202, "normal-test-exit");
            if (SessionLog.wasReplacedWithoutCleanEnd(root, 303)
                    || SessionLog.activeRuntimeProcessId(root) != -1) {
                throw new AssertionError("Clean runtime exit was reported as process death");
            }
            SessionLog.begin(root, work, "SESSION_THREE\n", 303);
            if (readLog(root).contains("[previous-session]")) {
                throw new AssertionError("A cleanly ended runtime was reported as interrupted");
            }

            SessionLog.begin(root, work, "SESSION_FOUR\n", 303);
            String sameProcess = readLog(root);
            if (!sameProcess.contains("previous runtime had no clean end marker")
                    || sameProcess.contains("process changed from pid=")) {
                throw new AssertionError("Same-process recreation was misclassified");
            }

            SessionLog.beginRuntimeMarker(root, work, 404);
            if (!SessionLog.wasReplacedWithoutCleanEnd(root, 405)) {
                throw new AssertionError("Runtime marker without debug logging was not saved");
            }
            System.out.println("PASS: clean exits and replaced runtime processes are distinguished");
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths =
                    Files.walk(root.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException error) {
                        throw new java.io.UncheckedIOException(error);
                    }
                });
            }
        }
    }
}
