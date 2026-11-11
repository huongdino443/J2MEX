package org.recompile.freej2me.lite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Persists runtime-process preference updates in the main app process. */
public final class AppPreferencesReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !AppPreferences.ACTION_RUNTIME_SETTINGS_CHANGED.equals(intent.getAction())) {
            return;
        }
        if (!AppPreferences.persistRuntimeSnapshot(context, intent)) {
            Log.e("J2MEX", "Could not persist runtime settings in the main process.");
            return;
        }
        MainActivity.onRuntimePreferencesUpdated();
    }
}
