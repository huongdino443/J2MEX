package org.recompile.freej2me.lite;

final class NativeHost {
    static { System.loadLibrary("freej2me_host"); }
    static native int launch(String home, String work, String cache, String classpath,
                             String log, String[] arguments);
}
