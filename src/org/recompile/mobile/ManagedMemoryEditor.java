/*
 * This file is part of FreeJ2ME.
 *
 * FreeJ2ME is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.recompile.mobile;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Bounded editor for numeric values reachable from the active MIDlet's managed Java heap.
 * This deliberately does not inspect native memory or other Android processes.
 */
public final class ManagedMemoryEditor {
    public static final int MAX_VISIBLE_RESULTS = 100;
    private static final int MAX_OBJECTS = 30000;
    private static final int MAX_CANDIDATES = 20000;
    private static final int MAX_ARRAY_ELEMENTS = 100000;

    public static final class Entry {
        public final int id;
        public final String type;
        public final String value;
        public final String location;
        public final String referenceKind;

        private Entry(Candidate candidate) {
            id = candidate.id;
            type = candidate.typeName();
            value = candidate.displayValue();
            location = candidate.location;
            referenceKind = candidate.referenceKind();
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;
        public final int total;
        public final List<Entry> entries;

        private Result(boolean success, String message, int total, List<Entry> entries) {
            this.success = success;
            this.message = message;
            this.total = total;
            this.entries = entries;
        }
    }

    public static Result notice(boolean success, String message) {
        return new Result(success, message == null ? "" : message, 0,
                Collections.<Entry>emptyList());
    }

    private static final class Candidate {
        final int id;
        final Field field;
        final WeakReference<Object> owner;
        final WeakReference<Object> array;
        final int index;
        final Class<?> valueType;
        final String location;

        Candidate(int id, Field field, Object owner, Object array, int index,
                Class<?> valueType, String location) {
            this.id = id;
            this.field = field;
            this.owner = owner == null ? null : new WeakReference<>(owner);
            this.array = array == null ? null : new WeakReference<>(array);
            this.index = index;
            this.valueType = valueType;
            this.location = location;
        }

        Object owner() {
            return owner == null ? null : owner.get();
        }

        Object array() {
            return array == null ? null : array.get();
        }

        Object read() throws Exception {
            if (field != null) {
                if (!Modifier.isStatic(field.getModifiers()) && owner() == null)
                    throw new IllegalStateException("MIDlet object is no longer reachable");
                return field.get(Modifier.isStatic(field.getModifiers()) ? null : owner());
            }
            Object target = array();
            if (target == null || index >= Array.getLength(target))
                throw new IllegalStateException("MIDlet array is no longer reachable");
            return Array.get(target, index);
        }

        void write(String text) throws Exception {
            Object value = parseValue(valueType, text);
            if (field != null) {
                if (!Modifier.isStatic(field.getModifiers()) && owner() == null)
                    throw new IllegalStateException("MIDlet object is no longer reachable");
                field.set(Modifier.isStatic(field.getModifiers()) ? null : owner(), value);
                return;
            }
            Object target = array();
            if (target == null || index >= Array.getLength(target))
                throw new IllegalStateException("MIDlet array is no longer reachable");
            Array.set(target, index, value);
        }

        String typeName() {
            if (valueType == byte.class) return "Int8";
            if (valueType == short.class) return "Int16";
            if (valueType == int.class) return "Int32";
            if (valueType == long.class) return "Int64";
            if (valueType == float.class) return "Float32";
            if (valueType == double.class) return "Float64";
            if (valueType == char.class) return "Char16";
            return valueType.getSimpleName();
        }

        String referenceKind() {
            if (field == null) return "Chưa truy được nguồn";
            if (Modifier.isStatic(field.getModifiers())) return "Tham chiếu tĩnh";
            return owner() == null
                    ? "Đối tượng không còn khả dụng"
                    : "Tham chiếu đối tượng";
        }

        String displayValue() {
            try {
                Object value = read();
                return value instanceof Character
                        ? Integer.toString(((Character) value).charValue())
                        : String.valueOf(value);
            } catch (Exception ignored) {
                return "unavailable";
            }
        }
    }

    private final ClassLoader loader;
    private final MIDletLoader midletLoader;
    private final Object rootOverride;
    private final LinkedHashMap<Integer, Candidate> candidates =
            new LinkedHashMap<Integer, Candidate>();
    private int nextId = 1;

    public ManagedMemoryEditor(MIDletLoader loader) {
        if (loader == null) throw new NullPointerException("loader");
        this.loader = loader;
        this.midletLoader = loader;
        this.rootOverride = null;
    }

    ManagedMemoryEditor(ClassLoader loader, Object rootOverride) {
        if (loader == null) throw new NullPointerException("loader");
        this.loader = loader;
        this.midletLoader = null;
        this.rootOverride = rootOverride;
    }

    public synchronized Result search(String query, boolean refine) {
        if (query == null || query.trim().isEmpty())
            return failure("Nhập giá trị số cần tìm.");
        final BigDecimal target;
        try {
            target = new BigDecimal(query.trim());
        } catch (NumberFormatException invalid) {
            return failure("Giá trị nhập không hợp lệ.");
        }

        if (refine) {
            LinkedHashMap<Integer, Candidate> kept = new LinkedHashMap<Integer, Candidate>();
            for (Candidate candidate : candidates.values()) {
                try {
                    if (matches(candidate.read(), target)) kept.put(candidate.id, candidate);
                } catch (Throwable unavailable) {
                    // A collected object or replaced array invalidates this session candidate.
                }
            }
            candidates.clear();
            candidates.putAll(kept);
        } else {
            candidates.clear();
            nextId = 1;
            Object root = midletLoader == null ? rootOverride : midletLoader.getMainInstance();
            Class<?>[] initializedClasses = ManagedMemoryDiscovery.snapshot(loader);
            if (root == null && initializedClasses.length == 0)
                return failure("Game chưa khởi chạy xong.");
            scan(root, initializedClasses, target);
        }
        return success(candidates.size(), page());
    }

    public synchronized Result edit(int id, String value) {
        Candidate candidate = candidates.get(id);
        if (candidate == null) return failure("Giá trị này không còn khả dụng.");
        try {
            candidate.write(value == null ? "" : value.trim());
            return success(candidates.size(), page());
        } catch (Throwable error) {
            return failure("Không thể thay đổi giá trị này.");
        }
    }

    private void scan(Object root, Class<?>[] initializedClasses, BigDecimal target) {
        ArrayDeque<Object> pending = new ArrayDeque<Object>();
        Set<Object> visited = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        Set<Class<?>> staticScanned = Collections.newSetFromMap(
                new IdentityHashMap<Class<?>, Boolean>());
        if (root != null) pending.add(root);
        for (Class<?> ownerType : initializedClasses) {
            if (ownerType == null || ownerType.getClassLoader() != loader
                    || !staticScanned.add(ownerType)) continue;
            scanFields(ownerType, null, true, pending, target);
        }
        try {
            if (Mobile.getDisplay() != null)
                enqueueIfGameObject(pending, Mobile.getDisplay().getCurrent());
        } catch (Throwable ignored) { }
        int arrayElements = 0;
        while (!pending.isEmpty() && visited.size() < MAX_OBJECTS
                && candidates.size() < MAX_CANDIDATES) {
            Object value = pending.removeFirst();
            if (value == null || !visited.add(value)) continue;
            Class<?> type = value.getClass();
            if (type.isArray()) {
                int length = Math.min(Array.getLength(value),
                        Math.max(0, MAX_ARRAY_ELEMENTS - arrayElements));
                arrayElements += length;
                Class<?> component = type.getComponentType();
                if (component.isPrimitive() && isNumeric(component)) {
                    for (int index = 0; index < length && candidates.size() < MAX_CANDIDATES; index++) {
                        try {
                            Object element = Array.get(value, index);
                            if (matches(element, target)) {
                                addArrayCandidate(value, index, component);
                            }
                        } catch (Throwable ignored) { }
                    }
                } else if (!component.isPrimitive()) {
                    for (int index = 0; index < length; index++) {
                        try {
                            enqueueIfGameObject(pending, Array.get(value, index));
                        } catch (Throwable ignored) { }
                    }
                }
                continue;
            }
            if (value instanceof Vector) {
                try {
                    Object[] snapshot;
                    synchronized (value) {
                        snapshot = ((Vector<?>) value).toArray();
                    }
                    for (Object item : snapshot) enqueueIfGameObject(pending, item);
                } catch (Throwable ignored) { }
                continue;
            }
            if (value instanceof Map) {
                try {
                    Object[] snapshot = ((Map<?, ?>) value).values().toArray();
                    for (Object item : snapshot) enqueueIfGameObject(pending, item);
                } catch (Throwable ignored) { }
                continue;
            }
            if (value instanceof Collection) {
                try {
                    Object[] snapshot = ((Collection<?>) value).toArray();
                    for (Object item : snapshot) enqueueIfGameObject(pending, item);
                } catch (Throwable ignored) { }
                continue;
            }
            if (type.getClassLoader() != loader) continue;
            for (Class<?> ownerType = type; ownerType != null && ownerType != Object.class
                    && ownerType.getClassLoader() == loader; ownerType = ownerType.getSuperclass()) {
                if (staticScanned.add(ownerType))
                    scanFields(ownerType, null, true, pending, target);
                scanFields(ownerType, value, false, pending, target);
            }
        }
    }

    private void scanFields(Class<?> ownerType, Object owner, boolean statics,
            ArrayDeque<Object> pending, BigDecimal target) {
        Field[] fields;
        try {
            fields = ownerType.getDeclaredFields();
        } catch (Throwable ignored) {
            return;
        }
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) != statics || field.isSynthetic()) continue;
            Class<?> valueType = field.getType();
            try {
                field.setAccessible(true);
                Object value = field.get(statics ? null : owner);
                if (valueType.isPrimitive() && isNumeric(valueType)) {
                    if (!Modifier.isFinal(modifiers) && matches(value, target))
                        addFieldCandidate(field, owner, valueType);
                } else if (!valueType.isPrimitive()) {
                    enqueueIfGameObject(pending, value);
                }
            } catch (Throwable ignored) {
                // Inaccessible or concurrently replaced guest fields are skipped.
            }
        }
    }

    private void enqueueIfGameObject(ArrayDeque<Object> pending, Object value) {
        if (value == null) return;
        if (pending.size() >= MAX_OBJECTS) return;
        Class<?> type = value.getClass();
        if (type.isArray() || type.getClassLoader() == loader || value instanceof Vector
                || value instanceof Map || value instanceof Collection)
            pending.addLast(value);
    }

    private void addFieldCandidate(Field field, Object owner, Class<?> type) {
        if (candidates.size() >= MAX_CANDIDATES) return;
        int id = nextId++;
        String location = field.getDeclaringClass().getName() + "." + field.getName();
        candidates.put(id, new Candidate(id, field, owner, null, -1, type, location));
    }

    private void addArrayCandidate(Object array, int index, Class<?> type) {
        if (candidates.size() >= MAX_CANDIDATES) return;
        int id = nextId++;
        String location = type.getSimpleName() + "[" + index + "]";
        candidates.put(id, new Candidate(id, null, null, array, index, type, location));
    }

    private List<Entry> page() {
        ArrayList<Entry> result = new ArrayList<Entry>(
                Math.min(MAX_VISIBLE_RESULTS, candidates.size()));
        for (Candidate candidate : candidates.values()) {
            if (result.size() >= MAX_VISIBLE_RESULTS) break;
            result.add(new Entry(candidate));
        }
        return result;
    }

    private static Result success(int total, List<Entry> entries) {
        return new Result(true, total == 0 ? "Không tìm thấy giá trị phù hợp." : "", total, entries);
    }

    private static Result failure(String message) {
        return new Result(false, message, 0, Collections.<Entry>emptyList());
    }

    private static boolean isNumeric(Class<?> type) {
        return type == byte.class || type == short.class || type == int.class
                || type == long.class || type == float.class || type == double.class
                || type == char.class;
    }

    private static boolean matches(Object value, BigDecimal target) {
        if (value instanceof Character)
            return BigDecimal.valueOf(((Character) value).charValue()).compareTo(target) == 0;
        if (!(value instanceof Number)) return false;
        try {
            return new BigDecimal(value.toString()).compareTo(target) == 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Object parseValue(Class<?> type, String text) {
        if (type == byte.class) return Byte.valueOf(text);
        if (type == short.class) return Short.valueOf(text);
        if (type == int.class) return Integer.valueOf(text);
        if (type == long.class) return Long.valueOf(text);
        if (type == float.class) return Float.valueOf(text);
        if (type == double.class) return Double.valueOf(text);
        if (type == char.class) {
            int value = Integer.parseInt(text);
            if (value < Character.MIN_VALUE || value > Character.MAX_VALUE)
                throw new IllegalArgumentException("Character value is out of range");
            return Character.valueOf((char) value);
        }
        throw new IllegalArgumentException("Unsupported numeric type");
    }
}
