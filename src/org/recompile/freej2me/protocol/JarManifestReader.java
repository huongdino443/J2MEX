package org.recompile.freej2me.protocol;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** Parses a JAR manifest while tolerating a UTF-8 BOM before its first header. */
public final class JarManifestReader {
    private JarManifestReader() { }

    public static Manifest read(JarFile jar) throws IOException {
        JarEntry entry = jar.getJarEntry("META-INF/MANIFEST.MF");
        if (entry == null) return null;

        try (BufferedInputStream input = new BufferedInputStream(jar.getInputStream(entry))) {
            input.mark(3);
            int first = input.read();
            int second = input.read();
            int third = input.read();
            if (first != 0xef || second != 0xbb || third != 0xbf) input.reset();
            return new Manifest(input);
        }
    }
}
