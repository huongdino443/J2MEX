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
import java.util.LinkedHashSet;
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
    public static final int MAX_BATCH_EDIT_VALUES = 20000;
    private static final int MAX_OBJECTS = 30000;
    private static final int MAX_CANDIDATES = MAX_BATCH_EDIT_VALUES;
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
        public final int pageIndex;
        public final int pageCount;
        public final List<Entry> entries;

        private Result(boolean success, String message, int total, int pageIndex,
                int pageCount, List<Entry> entries) {
            this.success = success;
            this.message = message;
            this.total = total;
            this.pageIndex = pageIndex;
            this.pageCount = pageCount;
            this.entries = entries;
        }
    }

    public static Result notice(boolean success, String message) {
        return new Result(success, message == null ? "" : message, 0, 0, 1,
                Collections.<Entry>emptyList());
    }

    private static final class Candidate {
        final int id;
        final Field field;
        final WeakReference<Object> owner;
        final WeakReference<Object> array;
        final int index;
        final Class<?> valueType;
        final boolean unsigned16;
        final String location;

        Candidate(int id, Field field, Object owner, Object array, int index,
                Class<?> valueType, boolean unsigned16, String location) {
            this.id = id;
            this.field = field;
            this.owner = owner == null ? null : new WeakReference<>(owner);
            this.array = array == null ? null : new WeakReference<>(array);
            this.index = index;
            this.valueType = valueType;
            this.unsigned16 = unsigned16;
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
            writeValue(parseEditValue(text));
        }

        Object parseEditValue(String text) {
            return unsigned16 && valueType == short.class
                    ? Short.valueOf((short) parseUnsigned16(text))
                    : parseValue(valueType, text);
        }

        void writeValue(Object value) throws Exception {
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
            if (unsigned16 || valueType == char.class) return "UInt16";
            if (valueType == byte.class) return "Int8";
            if (valueType == short.class) return "Int16";
            if (valueType == int.class) return "Int32";
            if (valueType == long.class) return "Int64";
            if (valueType == float.class) return "Float32";
            if (valueType == double.class) return "Float64";
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
                if (unsigned16 && value instanceof Short)
                    return Integer.toString(Short.toUnsignedInt(((Short) value).shortValue()));
                return value instanceof Character
                        ? Integer.toString(((Character) value).charValue())
                        : String.valueOf(value);
            } catch (Exception ignored) {
                return "unavailable";
            }
        }

        boolean matches(BigDecimal target) throws Exception {
            Object value = read();
            if (unsigned16 && value instanceof Short)
                return BigDecimal.valueOf(Short.toUnsignedInt(((Short) value).shortValue()))
                        .compareTo(target) == 0;
            return ManagedMemoryEditor.matches(value, target);
        }
    }

    private final ClassLoader loader;
    private final MIDletLoader midletLoader;
    private final Object rootOverride;
    private final LinkedHashMap<Integer, Candidate> candidates =
            new LinkedHashMap<Integer, Candidate>();
    private int nextId = 1;
    private int currentPage;

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
        return search(query, refine, "All", 0);
    }

    public synchronized Result search(String query, boolean refine, String typeFilter,
            int pageIndex) {
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
                    if (candidate.matches(target)) kept.put(candidate.id, candidate);
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
            scan(root, initializedClasses, target, normalizeFilter(typeFilter));
        }
        currentPage = clampPage(pageIndex);
        return currentResult();
    }

    public synchronized Result page(int pageIndex) {
        currentPage = clampPage(pageIndex);
        return currentResult();
    }

    public synchronized Result edit(int id, String value) {
        Candidate candidate = candidates.get(id);
        if (candidate == null) return failure("Giá trị này không còn khả dụng.");
        try {
            candidate.write(value == null ? "" : value.trim());
            return currentResult();
        } catch (Throwable error) {
            return failure("Không thể thay đổi giá trị này.");
        }
    }

    public synchronized Result editAll(int[] ids, String value) {
        if (ids == null || ids.length == 0)
            return failure("Chọn ít nhất một giá trị.");
        LinkedHashMap<Candidate, Object> previousValues =
                new LinkedHashMap<Candidate, Object>();
        LinkedHashMap<Candidate, Object> newValues =
                new LinkedHashMap<Candidate, Object>();
        try {
            LinkedHashSet<Integer> uniqueIds = new LinkedHashSet<Integer>();
            for (int id : ids) {
                if (!uniqueIds.add(id)) continue;
                Candidate candidate = candidates.get(id);
                if (candidate == null)
                    return failure("Một hoặc nhiều giá trị đã chọn không còn khả dụng.");
                previousValues.put(candidate, candidate.read());
                newValues.put(candidate, candidate.parseEditValue(value == null ? "" : value.trim()));
            }
            ArrayList<Candidate> written = new ArrayList<Candidate>();
            try {
                for (Map.Entry<Candidate, Object> update : newValues.entrySet()) {
                    update.getKey().writeValue(update.getValue());
                    written.add(update.getKey());
                }
            } catch (Throwable writeFailure) {
                for (int index = written.size() - 1; index >= 0; index--) {
                    Candidate candidate = written.get(index);
                    try {
                        candidate.writeValue(previousValues.get(candidate));
                    } catch (Throwable ignored) { }
                }
                return failure("Không thể sửa đồng loạt: giá trị không phù hợp hoặc đã mất.");
            }
            return currentResult();
        } catch (Throwable invalidValue) {
            return failure("Không thể sửa đồng loạt: giá trị không phù hợp hoặc đã mất.");
        }
    }

    private void scan(Object root, Class<?>[] initializedClasses, BigDecimal target,
            String typeFilter) {
        ArrayDeque<Object> pending = new ArrayDeque<Object>();
        Set<Object> visited = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        Set<Class<?>> staticScanned = Collections.newSetFromMap(
                new IdentityHashMap<Class<?>, Boolean>());
        if (root != null) pending.add(root);
        for (Class<?> ownerType : initializedClasses) {
            if (ownerType == null || ownerType.getClassLoader() != loader
                    || !staticScanned.add(ownerType)) continue;
            scanFields(ownerType, null, true, pending, target, typeFilter);
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
                if (component.isPrimitive() && isSelectedType(component, typeFilter)) {
                    for (int index = 0; index < length && candidates.size() < MAX_CANDIDATES; index++) {
                        try {
                            Object element = Array.get(value, index);
                            boolean unsigned16 = isUnsigned16(component, typeFilter);
                            if (matchesValue(element, target, unsigned16)) {
                                addArrayCandidate(value, index, component, unsigned16);
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
                    scanFields(ownerType, null, true, pending, target, typeFilter);
                scanFields(ownerType, value, false, pending, target, typeFilter);
            }
        }
    }

    private void scanFields(Class<?> ownerType, Object owner, boolean statics,
            ArrayDeque<Object> pending, BigDecimal target, String typeFilter) {
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
                if (valueType.isPrimitive() && isSelectedType(valueType, typeFilter)) {
                    boolean unsigned16 = isUnsigned16(valueType, typeFilter);
                    if (!Modifier.isFinal(modifiers)
                            && matchesValue(value, target, unsigned16))
                        addFieldCandidate(field, owner, valueType, unsigned16);
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

    private void addFieldCandidate(Field field, Object owner, Class<?> type,
            boolean unsigned16) {
        if (candidates.size() >= MAX_CANDIDATES) return;
        int id = nextId++;
        String location = field.getDeclaringClass().getName() + "." + field.getName();
        candidates.put(id, new Candidate(id, field, owner, null, -1, type, unsigned16, location));
    }

    private void addArrayCandidate(Object array, int index, Class<?> type,
            boolean unsigned16) {
        if (candidates.size() >= MAX_CANDIDATES) return;
        int id = nextId++;
        String location = type.getSimpleName() + "[" + index + "]";
        candidates.put(id, new Candidate(id, null, null, array, index, type,
                unsigned16, location));
    }

    private List<Entry> visiblePage() {
        ArrayList<Entry> result = new ArrayList<Entry>(
                Math.min(MAX_VISIBLE_RESULTS,
                        Math.max(0, candidates.size() - currentPage * MAX_VISIBLE_RESULTS)));
        int skipped = 0;
        for (Candidate candidate : candidates.values()) {
            if (skipped++ < currentPage * MAX_VISIBLE_RESULTS) continue;
            if (result.size() >= MAX_VISIBLE_RESULTS) break;
            result.add(new Entry(candidate));
        }
        return result;
    }

    private Result currentResult() {
        int pages = Math.max(1,
                (candidates.size() + MAX_VISIBLE_RESULTS - 1) / MAX_VISIBLE_RESULTS);
        return new Result(true, candidates.isEmpty()
                ? "Không tìm thấy giá trị phù hợp." : "", candidates.size(),
                currentPage, pages, visiblePage());
    }

    private static Result failure(String message) {
        return new Result(false, message, 0, 0, 1, Collections.<Entry>emptyList());
    }

    private int clampPage(int requestedPage) {
        int pages = Math.max(1,
                (candidates.size() + MAX_VISIBLE_RESULTS - 1) / MAX_VISIBLE_RESULTS);
        return Math.max(0, Math.min(requestedPage, pages - 1));
    }

    private static String normalizeFilter(String typeFilter) {
        if ("UInt16".equals(typeFilter) || "Int8".equals(typeFilter)
                || "Int16".equals(typeFilter) || "Int32".equals(typeFilter)
                || "Int64".equals(typeFilter) || "Float32".equals(typeFilter)
                || "Float64".equals(typeFilter)) return typeFilter;
        return "All";
    }

    private static boolean isSelectedType(Class<?> type, String typeFilter) {
        if ("All".equals(typeFilter)) return isNumeric(type);
        if ("UInt16".equals(typeFilter)) return type == short.class || type == char.class;
        if ("Int8".equals(typeFilter)) return type == byte.class;
        if ("Int16".equals(typeFilter)) return type == short.class;
        if ("Int32".equals(typeFilter)) return type == int.class;
        if ("Int64".equals(typeFilter)) return type == long.class;
        if ("Float32".equals(typeFilter)) return type == float.class;
        if ("Float64".equals(typeFilter)) return type == double.class;
        return false;
    }

    private static boolean isUnsigned16(Class<?> type, String typeFilter) {
        return type == char.class
                || ("UInt16".equals(typeFilter) && type == short.class);
    }

    private static boolean matchesValue(Object value, BigDecimal target,
            boolean unsigned16) {
        if (unsigned16 && value instanceof Short)
            return BigDecimal.valueOf(Short.toUnsignedInt(((Short) value).shortValue()))
                    .compareTo(target) == 0;
        return matches(value, target);
    }

    private static int parseUnsigned16(String text) {
        int value = Integer.parseInt(text);
        if (value < 0 || value > 0xFFFF)
            throw new IllegalArgumentException("UInt16 value is out of range");
        return value;
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
