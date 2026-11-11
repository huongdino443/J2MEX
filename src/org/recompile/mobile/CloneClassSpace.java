package org.recompile.mobile;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fresh class generations for the recognized lib.MIDlet program-list launcher.
 * Only a clone's private namespace is isolated; MIDP, RMS, launcher and proxies
 * remain shared. No game-specific static fields are changed.
 */
final class CloneClassSpace {
    private final MIDletLoader parent;
    private final URL[] urls;
    private final Set<String> prefixes = new LinkedHashSet<String>();
    private final Map<String, Generation> generations = new HashMap<String, Generation>();
    final String factoryClass;

    CloneClassSpace(MIDletLoader parent, URL[] urls, Map<String, String> properties,
            String mainClass) {
        this.parent = parent;
        this.urls = urls.clone();
        int separator = mainClass == null ? -1 : mainClass.lastIndexOf('.');
        factoryClass = separator < 0 ? "" : mainClass.substring(0, separator) + ".a";
        try {
            int count = Integer.parseInt(properties.get("num"));
            if (count < 1 || count > 64 || parent.findResource(
                    factoryClass.replace('.', '/') + ".class") == null) return;
            for (int i = 0; i < count; i++) {
                String descriptor = properties.get(Integer.toString(i));
                String[] fields = descriptor == null ? new String[0] : descriptor.split(",", -1);
                if (fields.length < 3) throw new IllegalArgumentException("missing clone entry");
                String entry = fields[2].trim();
                int dot = entry.indexOf('.');
                if (dot < 1) throw new IllegalArgumentException("missing private clone namespace");
                String prefix = entry.substring(0, dot + 1);
                if (prefix.equals("java.") || prefix.equals("javax.") || prefix.equals("lib.")
                        || prefix.equals("org.") || prefix.equals("com.") || prefix.equals("sun.")
                        || mainClass.startsWith(prefix) || !prefixes.add(prefix))
                    throw new IllegalArgumentException("clone namespaces are not separate");
            }
            System.out.println("Clone restart isolation: " + prefixes);
        } catch (RuntimeException unsupported) {
            prefixes.clear();
            System.out.println("Clone restart isolation unavailable: " + unsupported.getMessage());
        }
    }

    boolean isFactory(String className) {
        return !prefixes.isEmpty() && factoryClass.equals(className);
    }

    Class<?> resolve(String name) throws ClassNotFoundException {
        return resolve(name, true);
    }

    Class<?> resolve(String name, boolean initialize) throws ClassNotFoundException {
        Generation generation = null;
        synchronized (this) {
            for (String prefix : prefixes) if (name.startsWith(prefix)) {
                generation = generations.get(prefix);
                if (generation == null) {
                    generation = new Generation(this, prefix);
                    generations.put(prefix, generation);
                }
                break;
            }
        }
        return Class.forName(name, initialize, generation == null ? parent : generation);
    }

    static boolean isRetired(Object owner) {
        if (owner == null) return false;
        ClassLoader loader = owner.getClass().getClassLoader();
        return loader instanceof Generation && ((Generation)loader).retired;
    }

    static void retire(Object owner) {
        if (owner == null) return;
        retireLoader(owner.getClass().getClassLoader());
    }

    static void retireLoader(ClassLoader loader) {
        if (!(loader instanceof Generation)) return;
        Generation generation = (Generation)loader;
        synchronized (generation.space) {
            generation.retired = true;
            if (generation.space.generations.get(generation.prefix) == generation)
                generation.space.generations.remove(generation.prefix);
        }
        try { generation.close(); }
        catch (IOException error) { error.printStackTrace(); }
    }

    private static final class Generation extends URLClassLoader {
        final CloneClassSpace space;
        final String prefix;
        volatile boolean retired;

        Generation(CloneClassSpace space, String prefix) {
            super(space.urls, space.parent);
            this.space = space;
            this.prefix = prefix;
        }

        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            URL resource = findResource(name.replace('.', '/') + ".class");
            if (resource == null) throw new ClassNotFoundException(name);
            byte[] code;
            try (InputStream input = resource.openStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
                byte[] original = ClassFileCompatibility.adaptMissingStackMapTable(output.toByteArray(), name);
                code = space.parent.adaptResourceCalls(original, name);
            } catch (Exception error) {
                throw new ClassNotFoundException("Unable to preserve clone resource access: " + name, error);
            }
            return ClassFileCompatibility.defineWithLegacy45CodeAttributeFallback(
                    name, code, classBytes -> defineClass(name, classBytes, 0, classBytes.length));
        }

        @Override protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.startsWith(prefix) && !"lib.Class".equals(name))
                return super.loadClass(name, resolve);
            Class<?> result = findLoadedClass(name);
            if (result == null) {
                if (retired) throw new ClassNotFoundException("Stopped clone generation: " + name);
                // Never fall back to the parent's stale copy of an owned class.
                result = findClass(name);
            }
            if (resolve) resolveClass(result);
            return result;
        }
    }
}
