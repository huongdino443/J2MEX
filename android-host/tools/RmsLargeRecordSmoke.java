import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import org.recompile.mobile.MIDletLoader;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Large logical RMS records round-trip without changing the legacy inline format. */
public final class RmsLargeRecordSmoke {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("rms-large-test-");
        Path jar = root.resolve("fixture.jar");
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("MIDlet-Name", "RMS Large Record Fixture");
        attributes.putValue("MIDlet-1", "Fixture,,fixture.Midlet");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jar), manifest)) { }

        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform);
        platform.dataPath = root.toString() + File.separator;
        platform.loader = new MIDletLoader(new java.net.URL[]{jar.toUri().toURL()});

        byte[] large = data(90112, 17);
        RecordStore store = RecordStore.openRecordStore("large-cache", true);
        int largeId = store.addRecord(large, 0, large.length);
        check(largeId == 1, "large record keeps its normal RMS ID");
        check(store.getRecordSize(largeId) == large.length, "large record reports its full size");
        check(Arrays.equals(large, store.getRecord(largeId)), "large record is immediately readable");

        byte[] small = new byte[]{3, 1, 4, 1, 5};
        int smallId = store.addRecord(small, 0, small.length);
        check(smallId == 2, "following records keep normal sequential IDs");
        check(Arrays.equals(small, store.getRecord(smallId)), "small record remains inline and readable");

        Path storeFile = root.resolve("rms").resolve(platform.loader.suitename).resolve("large-cache");
        Path blobDirectory = storeFile.resolveSibling("large-cache.rmsx");
        check(Files.size(storeFile) < 65535, "legacy RMS file contains a compact reference");
        check(Files.isDirectory(blobDirectory), "large data is stored beside the RMS file");

        store.closeRecordStore();
        store = RecordStore.openRecordStore("large-cache", false);
        check(store.getNumRecords() == 2, "reopen preserves logical record count");
        check(Arrays.equals(large, store.getRecord(largeId)), "large record survives reopen");
        check(Arrays.equals(small, store.getRecord(smallId)), "legacy-sized record survives reopen");

        byte[] replacement = data(120037, 91);
        store.setRecord(largeId, replacement, 0, replacement.length);
        store.closeRecordStore();
        store = RecordStore.openRecordStore("large-cache", false);
        check(Arrays.equals(replacement, store.getRecord(largeId)), "large record update survives reopen");
        check(Arrays.equals(small, store.getRecord(smallId)), "updating a large record preserves siblings");

        store.deleteRecord(largeId);
        store.closeRecordStore();
        store = RecordStore.openRecordStore("large-cache", false);
        boolean deleted = false;
        try { store.getRecord(largeId); }
        catch (InvalidRecordIDException expected) { deleted = true; }
        check(deleted && store.getNumRecords() == 1, "deleting a spilled record preserves RMS semantics");
        check(Arrays.equals(small, store.getRecord(smallId)), "deleting a spilled record preserves siblings");

        Path[] blobs;
        try (java.util.stream.Stream<Path> entries = Files.list(blobDirectory)) {
            blobs = entries.toArray(Path[]::new);
        }
        check(blobs.length >= 2, "content-addressed blobs keep prior committed data intact");
        store.closeRecordStore();
        RecordStore.deleteRecordStore("large-cache");
        check(!Files.exists(storeFile) && !Files.exists(blobDirectory),
                "deleting a store removes its extended data");

        // A broken sidecar must fail closed rather than return the private marker as game data.
        Path corruptRoot = Files.createTempDirectory("rms-large-corrupt-");
        platform.dataPath = corruptRoot.toString() + File.separator;
        store = RecordStore.openRecordStore("corrupt-cache", true);
        store.addRecord(large, 0, large.length);
        store.closeRecordStore();
        Path corruptFile = corruptRoot.resolve("rms").resolve(platform.loader.suitename)
                .resolve("corrupt-cache.rmsx");
        Path blob;
        try (java.util.stream.Stream<Path> entries = Files.list(corruptFile)) {
            blob = entries.findFirst().orElseThrow(() -> new AssertionError("missing blob"));
        }
        Files.write(blob, new byte[]{1, 2, 3});
        boolean rejected = false;
        try { RecordStore.openRecordStore("corrupt-cache", false); }
        catch (RecordStoreException expected) { rejected = true; }
        check(rejected, "corrupt extended data is rejected");
        RecordStore.deleteRecordStore("corrupt-cache");
        check(!Files.exists(corruptFile), "deleting a corrupt store removes its sidecar directory");

        System.out.println("RmsLargeRecordSmoke: " + checks
                + " checks passed; add/read/update/reopen/delete, sibling preservation, corruption rejection");
    }

    private static byte[] data(int size, int seed) {
        byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) data[i] = (byte)(seed + i * 31);
        return data;
    }
}
