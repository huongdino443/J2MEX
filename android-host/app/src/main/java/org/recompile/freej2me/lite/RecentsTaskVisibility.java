package org.recompile.freej2me.lite;

import android.app.Activity;
import android.app.ActivityManager;
import android.util.Log;
import java.util.List;

/** Makes the current screen's task the only J2MEX task shown in system Recents. */
final class RecentsTaskVisibility {
    private static final String TAG = "J2MEX";

    private RecentsTaskVisibility() { }

    static void showOnlyTask(Activity activity, int visibleTaskId) {
        ActivityManager manager =
                (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
        if (manager == null) return;
        try {
            List<ActivityManager.AppTask> tasks = manager.getAppTasks();
            for (ActivityManager.AppTask task : tasks) {
                try {
                    ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                    if (info == null || info.id < 0) continue;
                    task.setExcludeFromRecents(
                            RecentsTaskPolicy.shouldExcludeTask(info.id, visibleTaskId));
                } catch (RuntimeException error) {
                    Log.w(TAG, "Could not update a Recents task visibility flag", error);
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not list J2MEX tasks for Recents", error);
        }
    }
}
