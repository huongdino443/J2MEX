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
            ManagedMemoryEditor.Result history = editor.historyPage(0);
            boolean foundStaticHistory = false;
            for (ManagedMemoryEditor.Entry entry : history.entries) {
                if (entry.location.endsWith(".wallet")) {
                    foundStaticHistory = "10000".equals(entry.originalValue)
                            && "12000".equals(entry.value);
                }
            }
            if (!foundStaticHistory || history.total != 1)
                throw new AssertionError("History did not retain the original and live values");

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
            fixtureClass.getField("currentValue").setInt(fixture, 9100);
            ManagedMemoryEditor.Result liveHistory = editor.historyPage(0);
            boolean foundLiveInstance = false;
            for (ManagedMemoryEditor.Entry entry : liveHistory.entries) {
                if (entry.location.endsWith(".currentValue"))
                    foundLiveInstance = "10000".equals(entry.originalValue)
                            && "9100".equals(entry.value);
            }
            if (!foundLiveInstance || liveHistory.total != 2)
                throw new AssertionError("Search/refine cleared or merged the edit history");

            ManagedMemoryEditor.Result unsigned = editor.search("60000", false, "UInt16", 0);
            if (!unsigned.success || unsigned.total != 3)
                throw new AssertionError("UInt16 search did not include short, char, and array values: "
                        + unsigned.total);
            int unsignedFieldId = -1;
            java.util.ArrayList<Integer> unsignedIds = new java.util.ArrayList<Integer>();
            for (ManagedMemoryEditor.Entry entry : unsigned.entries) {
                if (!"UInt16".equals(entry.type) || !"60000".equals(entry.value))
                    throw new AssertionError("Unsigned 16-bit value was displayed incorrectly: "
                            + entry.location + " " + entry.type + "=" + entry.value);
                unsignedIds.add(entry.id);
                if (entry.location.endsWith(".unsignedValue")) unsignedFieldId = entry.id;
            }
            if (unsignedFieldId < 0)
                throw new AssertionError("UInt16 short field was not found");
            ManagedMemoryEditor.Result editedUnsigned = editor.editAll(
                    toIntArray(unsignedIds), "50000");
            if (!editedUnsigned.success || editedUnsigned.total != 3)
                throw new AssertionError("Batch UInt16 edit failed");
            if (fixtureClass.getField("unsignedValue").getShort(fixture) != (short) 50000)
                throw new AssertionError("UInt16 batch edit did not preserve short bits");
            if (fixtureClass.getField("characterValue").getChar(fixture) != (char) 50000
                    || ((short[]) fixtureClass.getField("unsignedCounters").get(fixture))[0]
                            != (short) 50000)
                throw new AssertionError("UInt16 batch edit missed a char or array value");
            ManagedMemoryEditor.Result rejectedBatch =
                    editor.editAll(toIntArray(unsignedIds), "70000");
            if (rejectedBatch.success
                    || fixtureClass.getField("unsignedValue").getShort(fixture) != (short) 50000)
                throw new AssertionError("Invalid UInt16 batch partially changed values");
            ManagedMemoryEditor.Result unsignedHistory = editor.historyPage(0);
            java.util.ArrayList<Integer> unsignedHistoryIds = new java.util.ArrayList<Integer>();
            for (ManagedMemoryEditor.Entry entry : unsignedHistory.entries) {
                if ("60000".equals(entry.originalValue) && "50000".equals(entry.value))
                    unsignedHistoryIds.add(entry.id);
            }
            if (unsignedHistoryIds.size() != 3 || unsignedHistory.total != 5)
                throw new AssertionError("Bulk edits were not isolated in the history list");
            ManagedMemoryEditor.Result historyBatch = editor.editHistoryAll(
                    toIntArray(unsignedHistoryIds), "40000");
            if (!historyBatch.success
                    || fixtureClass.getField("unsignedValue").getShort(fixture) != (short) 40000)
                throw new AssertionError("Bulk editing from the history list failed");
            for (ManagedMemoryEditor.Entry entry : historyBatch.entries) {
                if (entry.originalValue.length() > 0
                        && entry.location.endsWith(".unsignedValue")
                        && (!"60000".equals(entry.originalValue)
                                || !"40000".equals(entry.value)))
                    throw new AssertionError("Re-editing replaced the first original value");
            }

            ManagedMemoryEditor.Result firstPage =
                    editor.search("7777", false, "Int32", 0);
            if (!firstPage.success || firstPage.total != 205 || firstPage.entries.size() != 100
                    || firstPage.pageIndex != 0 || firstPage.pageCount != 3)
                throw new AssertionError("First result page metadata was incorrect");
            ManagedMemoryEditor.Result secondPage = editor.page(1);
            if (secondPage.entries.size() != 100 || secondPage.pageIndex != 1)
                throw new AssertionError("Second result page was not returned");
            ManagedMemoryEditor.Result lastPage = editor.page(2);
            if (lastPage.entries.size() != 5 || lastPage.pageIndex != 2
                    || lastPage.pageCount != 3)
                throw new AssertionError("Last result page was not returned");

            int retainedCandidateId = firstPage.entries.get(0).id;
            editor.edit(retainedCandidateId, "200");
            ManagedMemoryEditor.Result finalHistory = editor.historyPage(0);
            boolean foundPageHistory = false;
            for (ManagedMemoryEditor.Entry entry : finalHistory.entries) {
                if ("200".equals(entry.value) && "7777".equals(entry.originalValue))
                    foundPageHistory = true;
            }
            if (!foundPageHistory || finalHistory.total != 6)
                throw new AssertionError("Editing a later search candidate was not recorded");
            ManagedMemoryEditor.Result repeatedSearch =
                    editor.search("200", false, "Int32", 0);
            if (!repeatedSearch.success || repeatedSearch.total != 1)
                throw new AssertionError("A fresh search could not find the edited value");
            String repeatedLocation = repeatedSearch.entries.get(0).location;
            editor.edit(repeatedSearch.entries.get(0).id, "250");
            ManagedMemoryEditor.Result repeatedEditHistory = editor.historyPage(0);
            boolean preservedFirstValue = false;
            for (ManagedMemoryEditor.Entry entry : repeatedEditHistory.entries) {
                if (entry.location.equals(repeatedLocation))
                    preservedFirstValue = "7777".equals(entry.originalValue)
                            && "250".equals(entry.value);
            }
            if (!preservedFirstValue || repeatedEditHistory.total != 6)
                throw new AssertionError("Re-editing created a duplicate or replaced original history");
            MemoryEditorGuard.resetSession();
            if (!MemoryEditorGuard.openEditor(false))
                throw new AssertionError("Offline memory editor did not open");
            MemoryEditorGuard.closeEditor();
            if (!MemoryEditorGuard.openEditor(false))
                throw new AssertionError("Memory editor did not reopen in the same game session");
            ManagedMemoryEditor.Result reopenedHistory = editor.historyPage(0);
            boolean retainedEditedValue = false;
            for (ManagedMemoryEditor.Entry entry : reopenedHistory.entries)
                if ("250".equals(entry.value) && "7777".equals(entry.originalValue))
                    retainedEditedValue = true;
            if (!retainedEditedValue)
                throw new AssertionError("Edit history was lost after close and reopen");
            MemoryEditorGuard.closeEditor();
        }
        System.out.println("ManagedMemoryEditorSmoke passed");
    }

    private static int[] toIntArray(java.util.List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) result[index] = values.get(index);
        return result;
    }
}
