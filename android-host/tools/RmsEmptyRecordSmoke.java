import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.security.Permission;
import java.util.Arrays;
import java.util.concurrent.*;
import javax.microedition.rms.*;
import org.recompile.mobile.*;

/** Empty records are live MIDP records, not tombstones. No network or device saves. */
public final class RmsEmptyRecordSmoke {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++; if (!ok) throw new AssertionError(message);
    }
    private static void deleted(RecordStore store, int id) throws Exception {
        boolean rejected = false;
        try { store.getRecord(id); } catch (InvalidRecordIDException expected) { rejected = true; }
        check(rejected, "deleted ID must stay invalid: " + id);
    }
    public static void main(String[] args) {
        try { run(args[0]); System.exit(0); }
        catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
    private static void run(String jar) throws Exception {
        System.setSecurityManager(new SecurityManager() {
            public void checkPermission(Permission p) { }
            public void checkConnect(String host, int port) { throw new SecurityException("Offline RMS test"); }
        });
        Path root = Files.createTempDirectory("rms-empty-test-");
        MobilePlatform platform = new MobilePlatform(240, 320); Mobile.setPlatform(platform);
        platform.dataPath = root.toString() + File.separator;
        platform.loader = new MIDletLoader(new URL[]{new File(jar).toURI().toURL()});
        RecordStore store = RecordStore.openRecordStore("empty-test", true);
        int first = store.addRecord(null, 0, 0);
        int second = store.addRecord(new byte[0], 0, 0);
        check(first == 1 && second == 2 && store.getNextRecordID() == 3, "empty record IDs advance");
        check(store.getNumRecords() == 2, "two empty records count as two live records");
        check(store.getRecord(first) == null && store.getRecordSize(first) == 0, "MIDP zero-length read and size");
        RecordEnumeration enumeration = store.enumerateRecords(null, null, false);
        check(enumeration.numRecords() == 2, "enumeration includes empty records");
        check(enumeration.nextRecordId() == first && enumeration.nextRecordId() == second
                && !enumeration.hasNextElement(), "nextRecordId advances and terminates");
        check(enumeration.previousRecordId() == second && enumeration.previousRecordId() == first
                && !enumeration.hasPreviousElement(), "previousRecordId advances backwards and terminates");
        boolean exhausted = false;
        try { enumeration.previousRecord(); } catch (InvalidRecordIDException expected) { exhausted = true; }
        check(exhausted, "previousRecord does not wrap around before the first record");
        enumeration.reset();
        check(enumeration.nextRecord() == null && enumeration.nextRecord() == null
                && !enumeration.hasNextElement(), "empty-record enumeration returns null and terminates");
        RecordEnumeration updated = store.enumerateRecords(null, null, true);
        check(updated.nextRecordId() == first && updated.nextRecordId() == second
                && !updated.hasNextElement(), "kept-updated enumeration stays exhausted without mutations");
        check(store.enumerateRecords(bytes -> bytes.length == 0, null, false).numRecords() == 2,
                "filter can select live empty records");
        store.setRecord(first, new byte[]{4, 5}, 0, 2);
        byte[] copy = store.getRecord(first); copy[0] = 99;
        check(store.getRecord(first)[0] == 4, "record reads are defensive copies");
        store.deleteRecord(first);
        check(store.getNumRecords() == 1, "delete excludes one record, not the empty sibling");
        deleted(store, first);
        store.closeRecordStore();
        store = RecordStore.openRecordStore("empty-test", false);
        check(store.getNumRecords() == 1 && store.getRecordSize(second) == 0, "live empty survives reopen");
        deleted(store, first);
        store.setRecord(second, new byte[]{9}, 0, 1);
        store.setRecord(second, null, 0, 0);
        check(store.getNumRecords() == 1 && store.getRecordSize(second) == 0, "setRecord to empty stays live");
        store.closeRecordStore();
        store = RecordStore.openRecordStore("empty-test", false);
        check(store.getNumRecords() == 1 && store.getRecordSize(second) == 0, "setRecord-empty persists");
        deleted(store, first);
        check(store.enumerateRecords(null, null, false).numRecords() == 1, "reopened enumeration skips tombstone");

        Path directory = root.resolve("rms").resolve(platform.loader.suitename);
        Path legacy = directory.resolve("legacy");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(legacy))) {
            out.writeShort(7); out.writeShort(4); out.writeShort(3);
            out.writeShort(2); out.write(new byte[]{12, 34});
            out.writeShort(0); // A deletion in the previous engine format.
            out.writeShort(1); out.writeByte(56);
        }
        RecordStore old = RecordStore.openRecordStore("legacy", false);
        check(old.getNumRecords() == 2 && old.getNextRecordID() == 4, "legacy file count and IDs retained");
        check(Arrays.equals(old.getRecord(1), new byte[]{12, 34}) && old.getRecord(3)[0] == 56,
                "legacy nonempty payloads unchanged");
        deleted(old, 2);
        check(old.addRecord(null, 0, 0) == 4, "new empty record appends without renumbering legacy IDs");
        old.closeRecordStore();
        old = RecordStore.openRecordStore("legacy", false);
        check(old.getNumRecords() == 3 && old.getRecordSize(4) == 0, "legacy plus new empty survives reopen");
        deleted(old, 2);

        RecordStore failure = RecordStore.openRecordStore("failure", true);
        failure.addRecord(new byte[]{42}, 0, 1);
        byte[] committed = Files.readAllBytes(directory.resolve("failure"));
        Path backup = directory.resolveSibling(directory.getFileName() + "-backup");
        Files.move(directory, backup);
        Files.write(directory, new byte[]{1}); // Prevent creation of a temporary save file.
        boolean rejected = false;
        try { failure.addRecord(null, 0, 0); } catch (RecordStoreException expected) { rejected = true; }
        check(rejected, "write failure is reported");
        check(failure.getNumRecords() == 1 && failure.getNextRecordID() == 2, "failed add rolls back in memory");
        check(Arrays.equals(committed, Files.readAllBytes(backup.resolve("failure"))),
                "failed save does not truncate committed RMS");
        Files.delete(directory); Files.move(backup, directory);

        platform.dataPath = root.resolve("avatar").toString() + File.separator;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        long started = System.nanoTime();
        Future<?> boot = executor.submit(() -> {
            try {
                Class<?> type = platform.loader.loadClass("s.qzcf");
                Object actualStore = type.getConstructor().newInstance();
                Field field = type.getDeclaredField("qza"); field.setAccessible(true);
                RecordStore rms = (RecordStore)field.get(actualStore);
                check(rms.getNumRecords() == 2 && rms.getNextRecordID() == 3,
                        "actual Avatar constructor completes with exactly two empty records");
                platform.loader.loadClass("s.qzto").getMethod("open", String.class).invoke(null, "socket://offline:0");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        try { boot.get(10, TimeUnit.SECONDS); }
        catch (TimeoutException timeout) {
            for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
                if (entry.getKey().getName().startsWith("pool-")) {
                    System.err.println("Avatar bootstrap blocked: " + entry.getKey().getState());
                    for (StackTraceElement frame : entry.getValue()) System.err.println("  " + frame);
                }
            throw timeout;
        }
        check(boot.isDone(), "actual offline server bootstrap finishes; external networking blocked");
        platform.loader.loadClass("s.qzto").getMethod("close").invoke(null);
        executor.shutdownNow();
        System.out.println("RmsEmptyRecordSmoke: " + checks + " checks passed; actual Avatar offline bootstrap "
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                + " ms; empty/deleted/reopen/legacy/atomic-failure; no gameplay or device verification");
    }
}
