/*
 * This file is part of FreeJ2ME.
 *
 * FreeJ2ME is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.recompile.mobile;

import java.util.Arrays;

/** Parent-loader registry for guest classes whose static initialization has completed. */
public final class ManagedMemoryDiscovery {
    private static final Object LOCK = new Object();
    private static final int MAX_CLASSES = 4096;
    private static ClassLoader activeLoader;
    private static Class<?>[] initializedClasses = new Class<?>[64];
    private static int initializedCount;

    private ManagedMemoryDiscovery() { }

    public static void attach(ClassLoader loader) {
        synchronized (LOCK) {
            activeLoader = loader;
            initializedCount = 0;
            Arrays.fill(initializedClasses, null);
        }
    }

    /** Called from each guest class initializer after its static values have been assigned. */
    public static void classInitialized(String binaryName) {
        ClassLoader loader = activeLoader;
        if (loader == null || binaryName == null) return;
        try {
            Class<?> type = Class.forName(binaryName, false, loader);
            if (type.getClassLoader() != loader) return;
            synchronized (LOCK) {
                if (activeLoader != loader) return;
                for (int index = 0; index < initializedCount; index++) {
                    if (initializedClasses[index] == type) return;
                }
                if (initializedCount >= MAX_CLASSES) return;
                if (initializedCount == initializedClasses.length) {
                    initializedClasses = Arrays.copyOf(initializedClasses,
                            Math.min(MAX_CLASSES, initializedClasses.length * 2));
                }
                initializedClasses[initializedCount++] = type;
            }
        } catch (Throwable ignored) {
            // Tracking is optional and must never prevent a MIDlet class from initializing.
        }
    }

    public static Class<?>[] snapshot(ClassLoader loader) {
        synchronized (LOCK) {
            if (loader == null || loader != activeLoader) return new Class<?>[0];
            return Arrays.copyOf(initializedClasses, initializedCount);
        }
    }
}
