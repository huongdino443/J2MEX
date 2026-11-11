package org.recompile.freej2me.lite;
import java.io.*;

public final class ArchiveSmoke {
    public static void main(String[] args) throws Exception {
        File directory = new File(args[1]);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create test output");
        RuntimeArchive.unpack(new FileInputStream(args[0]), directory);
        System.out.println("PASS: runtime archive extraction");
    }
}
