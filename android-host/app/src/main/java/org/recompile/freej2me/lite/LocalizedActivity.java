package org.recompile.freej2me.lite;

import android.app.Activity;
import android.content.Context;

/** Applies the selected app locale to Android-provided controls without changing MIDlet locale. */
abstract class LocalizedActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocalization.localizedContext(base));
    }
}
