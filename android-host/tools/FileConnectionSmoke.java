import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.util.Enumeration;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import org.recompile.mobile.MIDletLoader;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

public final class FileConnectionSmoke {
    private static int checks;
    private static final String ROOT_URL = "file:///freej2me/";

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private static void expectIOException(IoAction action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            checks++;
        }
    }

    public static void main(String[] args) throws Exception {
        File temp = Files.createTempDirectory("freej2me-jsr75-").toFile();
        File sharedRoot = Files.createTempDirectory("freej2me-shared-").toFile();
        MobilePlatform platform = new MobilePlatform(2, 2);
        platform.dataPath = temp.getCanonicalPath() + File.separator;
        platform.sharedStoragePath = sharedRoot.getCanonicalPath();
        Mobile.setPlatform(platform);

        Enumeration roots = FileSystemRegistry.listRoots();
        check(roots.hasMoreElements(), "private storage root is listed");
        check("freej2me/".equals(String.valueOf(roots.nextElement())),
                "private root matches file URL");
        check(roots.hasMoreElements(), "shared Android storage root is listed");
        check("Storage/".equals(String.valueOf(roots.nextElement())),
                "shared root uses the Storage volume name");
        check(!roots.hasMoreElements(), "only configured roots are exposed");
        File storageRoot = new File(temp, "jsr75-files");

        FileConnection directory = (FileConnection) Connector.open(
                ROOT_URL + "sjolani/", Connector.READ_WRITE);
        check(!directory.exists(), "new MIDlet directory starts empty");
        directory.mkdir();
        check(directory.exists() && directory.isDirectory(), "mkdir creates private directory");
        check(directory.canWrite(), "write mode reports writable");
        directory.close();
        check(!directory.isOpen(), "close updates connection state");

        FileConnection file = (FileConnection) Connector.open(
                ROOT_URL + "sjolani/version.txt", Connector.READ_WRITE);
        file.create();
        try (FileOutputStream out = (FileOutputStream) file.openOutputStream()) {
            out.write(new byte[] { 10, 20, 30, 40, 50 });
        }
        check(file.exists() && file.fileSize() == 5, "create and write file");
        FileConnection listing = (FileConnection) Connector.open(
                ROOT_URL + "sjolani/", Connector.READ);
        Enumeration entries = listing.list("*.txt", false);
        check(entries.hasMoreElements() && "version.txt".equals(entries.nextElement()),
                "directory list returns matching file names");
        listing.close();
        try (DataInputStream input = file.openDataInputStream()) {
            byte[] content = new byte[5];
            input.readFully(content);
            check(content[0] == 10 && content[4] == 50, "read preserves file bytes");
        }
        file.truncate(3);
        check(file.fileSize() == 3, "truncate changes stored length");
        file.close();
        FileConnection readOnly = (FileConnection) Connector.open(
                ROOT_URL + "sjolani/version.txt", Connector.READ);
        check(!readOnly.canWrite(), "read-only mode is not writable");
        expectIOException(() -> readOnly.openOutputStream(), "read-only output is rejected");
        readOnly.close();

        FileConnection spaced = (FileConnection) Connector.open(
                ROOT_URL + "Unnamed world/", Connector.READ_WRITE);
        spaced.mkdir();
        spaced.close();
        FileConnection encoded = (FileConnection) Connector.open(
                ROOT_URL + "Unnamed%20world/", Connector.READ);
        check(encoded.exists() && encoded.isDirectory(), "raw and escaped spaces name the same private directory");
        encoded.close();
        FileConnection worldData = (FileConnection) Connector.open(
                ROOT_URL + "Unnamed world/level.dat", Connector.READ_WRITE);
        worldData.create();
        try (java.io.OutputStream output = worldData.openOutputStream()) { output.write(73); }
        worldData.close();
        FileConnection savedWorld = (FileConnection) Connector.open(
                ROOT_URL + "Unnamed%20world/level.dat", Connector.READ);
        try (java.io.InputStream input = savedWorld.openInputStream()) {
            check(input.read() == 73, "world data with spaces survives closing and reopening");
        }
        savedWorld.close();
        expectIOException(() -> Connector.open(ROOT_URL + "%2e%2e/outside", Connector.READ_WRITE),
                "encoded traversal still cannot escape private root");
        expectIOException(() -> Connector.open(ROOT_URL + "Unnamed world/?query", Connector.READ),
                "space handling does not allow URL query components");
        expectIOException(() -> Connector.open("file:///freej2me/../outside", Connector.READ_WRITE),
                "path traversal outside private root is rejected");
        FileConnection remove = (FileConnection) Connector.open(
                ROOT_URL + "sjolani/version.txt", Connector.READ_WRITE);
        remove.delete();
        remove.close();

        FileConnection sharedDirectory = (FileConnection) Connector.open(
                "file:///Storage/Documents/", Connector.READ_WRITE);
        sharedDirectory.mkdir();
        check(sharedDirectory.isDirectory(), "shared root creates directories");
        check("file:///Storage/Documents/".equals(sharedDirectory.getURL()),
                "shared connection URLs preserve the Storage volume");
        sharedDirectory.close();
        FileConnection sharedFile = (FileConnection) Connector.open(
                "file:///Storage/Documents/note.txt", Connector.READ_WRITE);
        sharedFile.create();
        try (FileOutputStream output = (FileOutputStream) sharedFile.openOutputStream()) {
            output.write(new byte[] { 4, 5, 6 });
        }
        sharedFile.close();
        FileConnection sharedRead = (FileConnection) Connector.open(
                "file:///Storage/Documents/note.txt", Connector.READ);
        try (DataInputStream input = sharedRead.openDataInputStream()) {
            byte[] content = new byte[3];
            input.readFully(content);
            check(content[0] == 4 && content[2] == 6,
                    "shared Android file contents can be read");
        }
        sharedRead.close();
        FileConnection sharedRemove = (FileConnection) Connector.open(
                "file:///Storage/Documents/note.txt", Connector.READ_WRITE);
        sharedRemove.delete();
        sharedRemove.close();
        expectIOException(() -> Connector.open(
                "file:///Storage/../outside", Connector.READ_WRITE),
                "shared path traversal cannot leave Android storage");
        File outside = new File(temp, "outside.txt");
        Files.createFile(outside.toPath());
        Files.createSymbolicLink(new File(sharedRoot, "escape").toPath(), outside.toPath());
        expectIOException(() -> Connector.open(
                "file:///Storage/escape", Connector.READ),
                "shared symlinks cannot escape Android storage");

        if (args.length > 0) {
            File jar = new File(args[0]).getCanonicalFile();
            MIDletLoader loader = new MIDletLoader(new URL[] { jar.toURI().toURL() });
            platform.loader = loader;
            try {
                Class<?> resourceBootstrap = loader.loadClass("en");
                Method initialize = resourceBootstrap.getMethod("a", Boolean.TYPE);
                initialize.invoke(null, false);
                Field path = resourceBootstrap.getDeclaredField("a");
                path.setAccessible(true);
                check("file:///freej2me/sjolani/".equals(path.get(null)),
                        "actual PVTK bootstrap receives its virtual root");
                check(new File(storageRoot, "sjolani").isDirectory(),
                        "actual PVTK bootstrap creates its private resource directory");
            } finally {
                loader.close();
            }
        }

        System.out.println("FileConnectionSmoke: " + checks
                + " checks passed; private/shared roots, file lifecycle, byte I/O, traversal guard"
                + (args.length > 0 ? ", PVTK resource bootstrap" : ""));
    }
}
