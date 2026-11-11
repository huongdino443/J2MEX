package org.recompile.mobile;

import java.io.File;
import java.net.URL;

public final class ManagedMemoryEditorSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("fixture directory required");
        URL fixtureUrl = new File(args[0]).toURI().toURL();
        try (MIDletLoader loader = new MIDletLoader(new URL[] { fixtureUrl })) {
            Class<?> fixtureClass = Class.forName("fixture.MemoryEditorFixture", true, loader);
            Object fixture = fixtureClass.getDeclaredConstructor().newInstance();
            if (ManagedMemoryDiscovery.snapshot(loader).length != 1)
                throw new AssertionError("Initialized guest class was not registered");

            ManagedMemoryEditor editor = new ManagedMemoryEditor((ClassLoader) loader, fixture);
            ManagedMemoryEditor.Result first = editor.search("10000", false);
            if (!first.success || first.total != 3)
                throw new AssertionError("Expected static, instance, and array candidates, got "
                        + first.total);

            int staticId = -1;
            boolean sawInstance = false;
            boolean sawUntraceableArray = false;
            for (ManagedMemoryEditor.Entry entry : first.entries) {
                if (entry.location.endsWith(".wallet")) {
                    staticId = entry.id;
                    if (!"Int32".equals(entry.type)
                            || !"Tham chiếu tĩnh".equals(entry.referenceKind))
                        throw new AssertionError("Static field metadata was incorrect");
                } else if (entry.location.endsWith(".currentValue")) {
                    sawInstance = "Tham chiếu đối tượng".equals(entry.referenceKind);
                } else if (entry.location.startsWith("int[")) {
                    sawUntraceableArray = "Chưa truy được nguồn".equals(entry.referenceKind);
                }
            }
            if (staticId < 0 || !sawInstance || !sawUntraceableArray)
                throw new AssertionError("Reference source labels were not returned correctly");
            ManagedMemoryEditor.Result edited = editor.edit(staticId, "12000");
            if (!edited.success || fixtureClass.getField("wallet").getInt(null) != 12000)
                throw new AssertionError("Static field edit did not reach the MIDlet heap");

            ManagedMemoryEditor.Result refined = editor.search("10000", true);
            if (!refined.success || refined.total != 2)
                throw new AssertionError("Refine did not retain the remaining candidates");
            int instanceId = -1;
            for (ManagedMemoryEditor.Entry entry : refined.entries)
                if (entry.location.endsWith(".currentValue")
                        && "Tham chiếu đối tượng".equals(entry.referenceKind))
                    instanceId = entry.id;
            if (instanceId < 0)
                throw new AssertionError("Refined instance source metadata was lost");
            editor.edit(instanceId, "9000");
            if (fixtureClass.getField("currentValue").getInt(fixture) != 9000)
                throw new AssertionError("Instance field edit did not reach the MIDlet heap");
        }
        System.out.println("ManagedMemoryEditorSmoke passed");
    }
}
