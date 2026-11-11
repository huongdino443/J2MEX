package org.recompile.freej2me.protocol;

import java.io.File;
import java.io.IOException;

/** Canonical-path guard shared by the engine and Android audio host. */
public final class AudioCachePaths {
    private AudioCachePaths() { }

    public static boolean isPrivateFile(File cacheDirectory, File candidate) throws IOException {
        if (cacheDirectory == null || candidate == null) return false;
        File root = cacheDirectory.getCanonicalFile();
        File file = candidate.getCanonicalFile();
        return file.getPath().startsWith(root.getPath() + File.separator);
    }
}
