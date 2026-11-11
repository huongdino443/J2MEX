package org.recompile.mobile;

import org.recompile.android.HeadlessMain;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class MIDletLocaleSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Locale originalLocale = Locale.getDefault();
        String originalProperty = System.getProperty("microedition.locale");
        File jar = File.createTempFile("midlet-locale-smoke", ".jar");
        try {
            Manifest manifest = new Manifest();
            Attributes attributes = manifest.getMainAttributes();
            attributes.putValue("Manifest-Version", "1.0");
            attributes.putValue("MIDlet-Name", "Locale Smoke");
            attributes.putValue("MIDlet-Version", "1.0");
            attributes.putValue("MIDlet-Vendor", "FreeJ2ME");
            attributes.putValue("MIDlet-1",
                    "Locale Smoke, /icon.png, org.recompile.mobile.LocaleSmoke");
            try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar), manifest)) { }

            Locale.setDefault(Locale.US);
            String deviceLocale = HeadlessMain.applyDeviceLocale("vi-VN");
            require("vi-VN".equals(deviceLocale),
                    "Android locale tag should be normalized and retained");
            require("vi-VN".equals(Locale.getDefault().toLanguageTag()),
                    "Android device locale should replace the embedded JVM default");
            MIDletLoader loader = new MIDletLoader(new URL[]{jar.toURI().toURL()});
            require("vi-VN".equals(System.getProperty("microedition.locale")),
                    "system property should follow the device's default locale");

            Field propertiesField = MIDletLoader.class.getDeclaredField("properties");
            propertiesField.setAccessible(true);
            HashMap<String, String> properties =
                    (HashMap<String, String>)propertiesField.get(loader);
            require("vi-VN".equals(properties.get("microedition.locale")),
                    "MIDlet app property should follow the device's default locale");
            System.out.println("MIDletLocaleSmoke: vi-VN propagated to system and MIDlet properties");
        } finally {
            Locale.setDefault(originalLocale);
            if (originalProperty == null) System.clearProperty("microedition.locale");
            else System.setProperty("microedition.locale", originalProperty);
            jar.delete();
        }
    }
}
