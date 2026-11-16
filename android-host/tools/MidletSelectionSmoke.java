package org.recompile.mobile;

import java.io.File;
import java.net.URL;

public final class MidletSelectionSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length < 3)
            throw new IllegalArgumentException("Pass JAR, selected class, and expected name");
        System.setProperty("j2mex.midlet.class", args[1]);
        MIDletLoader selected = new MIDletLoader(
                new URL[]{new File(args[0]).toURI().toURL()});
        require(args[1].equals(selected.getMIDletClassName()),
                "loader did not select the requested manifest MIDlet");
        require(args[2].equals(selected.name),
                "loader did not apply the selected MIDlet display name");

        System.clearProperty("j2mex.midlet.class");
        MIDletLoader defaultLoader = new MIDletLoader(
                new URL[]{new File(args[0]).toURI().toURL()});
        require("zjq.Main".equals(defaultLoader.getMIDletClassName()),
                "without a selection, the manifest's first MIDlet must remain the default");
        System.out.println("MidletSelectionSmoke: selected MIDlet override and default passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
