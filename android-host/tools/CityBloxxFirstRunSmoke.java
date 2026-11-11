import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;

import javax.microedition.rms.RecordStore;

import org.recompile.mobile.MIDletLoader;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Verifies City Bloxx's cold-RMS city-mode bootstrap without modifying other games' RMS. */
public final class CityBloxxFirstRunSmoke
{
	public static void main(String[] args) throws Exception
	{
		if(args.length != 1)
		{
			throw new IllegalArgumentException("Usage: CityBloxxFirstRunSmoke <city-bloxx.jar>");
		}

		MobilePlatform platform = new MobilePlatform(240, 320);
		Mobile.setPlatform(platform);
		File data = Files.createTempDirectory("city-bloxx-first-run-").toFile();
		platform.dataPath = data.getCanonicalPath() + File.separator;
		platform.setPainter(() -> { });
		platform.loader = new MIDletLoader(new URL[]{new File(args[0]).toURI().toURL()});
		platform.loader.start();

		Thread.sleep(1600);
		sendKey(platform, -5);
		sendKey(platform, 53);
		Thread.sleep(1200);

		File rmsDir = new File(data, "./rms/City Bloxx");
		File modeStore = new File(rmsDir, "citymode");
		require(modeStore.isFile() && modeStore.length() > 16,
			"the game's own serializer did not create a citymode record");

		Class<?> stateClass = platform.loader.loadClass("a");
		Field modeFlag = findStaticField(stateClass, "a", boolean.class);
		require(modeFlag.getBoolean(null), "the loaded first-run state did not enable city mode");

		Class<?> gameClass = platform.loader.loadClass("House");
		Field cityGrid = findStaticField(gameClass, "i", int[][].class);
		require(cityGrid.get(null) != null, "City Bloxx did not initialize its city grid");

		File savedCity = new File(rmsDir, "cityModeRS");
		System.out.println("CityBloxxFirstRunSmoke: before fallback read, cityModeRS="
			+ (savedCity.isFile() ? savedCity.length() + " bytes" : "missing"));

		// This specifically tests the missing-store shim, not the full game resume UI.
		RecordStore cityStore = RecordStore.openRecordStore("cityModeRS", false);
		byte[] cityRecord = cityStore.getRecord(1);
		cityStore.closeRecordStore();
		require(cityRecord != null && cityRecord.length > 16,
			"missing cityModeRS fallback did not create a readable record");

		Method saveCity = gameClass.getDeclaredMethod("r");
		saveCity.setAccessible(true);
		saveCity.invoke(null);
		require(savedCity.isFile() && savedCity.length() > 16,
			"the initialized city grid could not be saved");

		System.out.println("CityBloxxFirstRunSmoke: missing-store fallback works; this is not a resume-path test");
		System.exit(0);
	}

	private static void sendKey(MobilePlatform platform, int code) throws Exception
	{
		platform.keyPressed(code);
		Thread.sleep(60);
		platform.keyReleased(code);
		Thread.sleep(500);
	}

	private static Field findStaticField(Class<?> owner, String name, Class<?> type)
		throws Exception
	{
		for(Field field : owner.getDeclaredFields())
		{
			if(name.equals(field.getName()) && type.equals(field.getType())
				&& java.lang.reflect.Modifier.isStatic(field.getModifiers()))
			{
				field.setAccessible(true);
				return field;
			}
		}
		throw new NoSuchFieldException(owner.getName() + "." + name);
	}

	private static void require(boolean condition, String message)
	{
		if(!condition)
		{
			throw new AssertionError(message);
		}
	}
}
