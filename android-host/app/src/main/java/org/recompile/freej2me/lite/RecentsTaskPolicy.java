package org.recompile.freej2me.lite;

/** Keeps one app task visible in Android Recents at a time. */
final class RecentsTaskPolicy {
    private RecentsTaskPolicy() { }

    static boolean shouldExcludeTask(int taskId, int visibleTaskId) {
        return taskId != visibleTaskId;
    }
}
