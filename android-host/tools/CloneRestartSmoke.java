import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.rms.RecordStore;
import org.recompile.mobile.*;

/** Offline launcher fixture: destroyApp is empty and static boot state survives. */
public final class CloneRestartSmoke {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path work = Paths.get("build/clone-restart-test");
        Path jar = fixture(work);
        MobilePlatform platform = new MobilePlatform(24, 32);
        Mobile.setPlatform(platform);
        platform.setPainter(() -> {});
        Display display = new Display();
        MIDletLoader loader = new MIDletLoader(new URL[]{jar.toUri().toURL()});
        platform.loader = loader;
        platform.dataPath = work.resolve("rms").toAbsolutePath() + File.separator;
        RecordStore store = RecordStore.openRecordStore("restart", true);
        int record = store.addRecord(new byte[]{17, 42}, 0, 2);
        store.closeRecordStore();
        Object factory = Class.forName("quyetdaik.a", true, loader).newInstance();
        Method make = factory.getClass().getMethod("make", String.class);

        Object first = make.invoke(factory, "a.code.main.GameMidlet");
        invoke(first, "start");
        require(initialized(first), "first clone initializes");
        Object second = make.invoke(factory, "b.code.main.GameMidlet");
        invoke(second, "start");
        require(initialized(second), "second clone initializes");
        Class<?> secondClass = second.getClass();
        invoke(first, "notifyDestroyed");
        display.setCurrent(new Form("Launcher"));

        Object restarted = make.invoke(factory, "a.code.main.GameMidlet");
        invoke(restarted, "start");
        require(initialized(restarted),
                "Stopped clone must initialize again instead of retaining its static boot flag");
        require(restarted.getClass() != first.getClass(), "restart gets a fresh class generation");
        require(restarted.getClass().getSuperclass() == first.getClass().getSuperclass(),
                "generations share the launcher MIDlet API");
        require(((Integer)invoke(restarted, "starts")) == 1, "fresh static initialization runs once");
        Class<?> reflected = (Class<?>)invoke(restarted, "reflectedState");
        require(reflected.getClassLoader() == restarted.getClass().getClassLoader(),
                "lib.Class reflection resolves within the clone generation");
        Object active = display.getCurrent();
        invoke(first, "showDisplay");
        invoke(first, "resumeRequest");
        require(display.getCurrent() == active, "destroyed generation cannot reclaim display");
        invoke(first, "notifyDestroyed");
        invoke(restarted, "showDisplay");
        require(display.getCurrent() == active, "late destroy from old generation cannot retire its replacement");
        require(second.getClass() == secondClass && initialized(second),
                "restarting one clone does not reset its sibling");

        for (int cycle = 0; cycle < 5; cycle++) {
            Class<?> previous = restarted.getClass();
            invoke(restarted, "notifyDestroyed");
            display.setCurrent(new Form("Launcher"));
            restarted = make.invoke(factory, "a.code.main.GameMidlet");
            invoke(restarted, "start");
            require(initialized(restarted) && restarted.getClass() != previous,
                    "repeat stop/start creates a working fresh generation");
            invoke(second, "showDisplay");
            require(display.getCurrent().getClass().getClassLoader() == secondClass.getClassLoader(),
                    "sibling remains resumable after another clone restarts");
        }
        store = RecordStore.openRecordStore("restart", false);
        require(Arrays.equals(store.getRecord(record), new byte[]{17, 42}),
                "clone restart preserves RMS records");
        store.closeRecordStore();
        System.out.println("CloneRestartSmoke: " + checks
                + " checks passed; fresh static state, repeated restart, sibling, reflection, RMS");
    }

    private static Object invoke(Object target, String method) throws Exception {
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (InvocationTargetException error) { throw new RuntimeException(error.getCause()); }
    }
    private static boolean initialized(Object target) throws Exception {
        return (Boolean)invoke(target, "initialized");
    }
    private static void require(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static Path fixture(Path work) throws Exception {
        Path source = work.resolve("src"), classes = work.resolve("classes");
        Files.createDirectories(classes);
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("lib/MIDlet.java",
                "package lib; public abstract class MIDlet {"
                + "protected abstract void startApp(); protected abstract void destroyApp(boolean x);"
                + "public void start(){startApp();}"
                + "public void showDisplay(){Display.getDisplay(this).show();}"
                + "public void resumeRequest(){showDisplay();} public void notifyPaused(){}"
                + "public void notifyDestroyed(){destroyApp(true);Display.midlets.remove(this);}}");
        sources.put("lib/Display.java",
                "package lib; public class Display {"
                + "public static java.util.Hashtable<MIDlet,Display> midlets=new java.util.Hashtable<>();"
                + "private javax.microedition.lcdui.Displayable current;"
                + "private Display(MIDlet owner){midlets.put(owner,this);}"
                + "public static Display getDisplay(MIDlet owner){Display d=midlets.get(owner);"
                + "return d==null?new Display(owner):d;}"
                + "public void setCurrent(javax.microedition.lcdui.Displayable next){current=next;"
                + "org.recompile.mobile.Mobile.getDisplay().setCurrent(next);}"
                + "public void show(){setCurrent(current);}}");
        sources.put("lib/Class.java",
                "package lib; public class Class {"
                + "public static java.lang.Class<?> aorName(String n)throws Exception{"
                + "return java.lang.Class.forName(\"a.\"+n);}}");
        sources.put("quyetdaik/a.java",
                "package quyetdaik; public class a {"
                + "public lib.MIDlet make(String name)throws Exception{"
                + "return (lib.MIDlet)Class.forName(name).newInstance();}}");
        for (String prefix : new String[]{"a", "b"}) {
            sources.put(prefix + "/State.java", "package " + prefix
                    + "; public class State {public static boolean booted;public static int starts;}");
            sources.put(prefix + "/code/main/GameMidlet.java",
                    "package " + prefix + ".code.main; public class GameMidlet extends lib.MIDlet {"
                    + "private javax.microedition.lcdui.Canvas screen;"
                    + "protected void startApp(){if(" + prefix + ".State.booted)return;"
                    + prefix + ".State.booted=true;" + prefix + ".State.starts++;"
                    + "screen=new javax.microedition.lcdui.Canvas(){protected void paint("
                    + "javax.microedition.lcdui.Graphics g){g.setColor(0x123456);"
                    + "g.fillRect(0,0,getWidth(),getHeight());}};"
                    + "lib.Display.getDisplay(this).setCurrent(screen);}"
                    + "protected void destroyApp(boolean x){}"
                    + "public boolean initialized(){return screen!=null;}"
                    + "public int starts(){return " + prefix + ".State.starts;}"
                    + "public java.lang.Class<?> reflectedState()throws Exception{"
                    + "return lib.Class.aorName(\"State\");}}");
        }
        List<String> compiler = new ArrayList<>(Arrays.asList("--release", "8", "-cp",
                System.getProperty("java.class.path"), "-d", classes.toString()));
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path file = source.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue().getBytes(StandardCharsets.UTF_8));
            compiler.add(file.toString());
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null,
                compiler.toArray(new String[0])) != 0) throw new AssertionError("fixture compilation");
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("MIDlet-1", "Restart,,quyetdaik.quyetdaik");
        attributes.putValue("MIDlet-Name", "RestartFixture");
        attributes.putValue("num", "2");
        attributes.putValue("0", "A,,a.code.main.GameMidlet,");
        attributes.putValue("1", "B,,b.code.main.GameMidlet,");
        Path jar = work.resolve("fixture.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest);
             java.util.stream.Stream<Path> paths = Files.walk(classes)) {
            for (Path file : (Iterable<Path>)paths.filter(Files::isRegularFile)::iterator) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return jar;
    }
}
