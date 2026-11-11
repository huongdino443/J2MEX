package org.recompile.mobile;

import java.io.File;
import java.net.URL;

public final class MidletLoaderClassLoadSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Pass a JAR and one or more class names");
        }
        URL jar = new File(args[0]).toURI().toURL();
        MIDletLoader loader = new MIDletLoader(new URL[]{jar});
        for (int i = 1; i < args.length; i++) {
            Class<?> loaded = loader.loadClass(args[i]);
            require(loaded.getClassLoader() == loader,
                    args[i] + " was defined by MIDletLoader");
            require(loaded.getDeclaredMethods().length > 0,
                    args[i] + " passed bytecode verification");
            System.out.println("MidletLoaderClassLoadSmoke: " + args[i] + " loaded and verified");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
