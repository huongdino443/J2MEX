package org.recompile.mobile;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Narrow first-run compatibility for City Bloxx's missing city-mode RMS store.
 *
 * The game has its own serializer, so seed through that rather than changing
 * the general RecordStore missing-store behavior or inventing a binary record.
 */
public final class CityBloxxFirstRun
{
	private CityBloxxFirstRun()
	{
	}

	public static boolean seedMissingModeStore(MIDletLoader loader, String appName,
		String storeName, File storeFile)
	{
		if(loader == null || storeFile == null
			|| !"City Bloxx".equals(appName)
			|| !"House".equals(loader.getMIDletClassName()))
		{
			return false;
		}

		if("cityModeRS".equals(storeName))
		{
			return seedMissingCityStore(loader, storeFile);
		}
		if(!"citymode".equals(storeName))
		{
			return false;
		}

		boolean previousModeFlag = false;
		int previousSelection = -1;
		Field modeFlag = null;
		Field selection = null;
		try
		{
			Class<?> stateClass = loader.loadClass("a");
			Class<?> gameClass = loader.loadClass("House");
			modeFlag = findStaticField(stateClass, "a", boolean.class);
			selection = findStaticField(stateClass, "I", int.class);

			// Validate the obfuscated game classes before applying this shim.
			stateClass.getDeclaredMethod("g");
			Method saveState = stateClass.getDeclaredMethod("h");
			gameClass.getDeclaredMethod("B");
			gameClass.getDeclaredMethod("r");

			modeFlag.setAccessible(true);
			previousModeFlag = modeFlag.getBoolean(null);
			modeFlag.setBoolean(null, true);
			selection.setAccessible(true);
			previousSelection = selection.getInt(null);
			selection.setInt(null, 0);
			saveState.setAccessible(true);
			saveState.invoke(null);

			if(!storeFile.isFile() || storeFile.length() <= 16)
			{
				modeFlag.setBoolean(null, previousModeFlag);
				selection.setInt(null, previousSelection);
				return false;
			}

			System.out.println("Initialized City Bloxx first-run city mode state");
			return true;
		}
		catch (Exception e)
		{
			if(modeFlag != null)
			{
				try
				{
					modeFlag.setAccessible(true);
					modeFlag.setBoolean(null, previousModeFlag);
				}
				catch (Exception ignored)
				{
				}
			}
			if(selection != null)
			{
				try
				{
					selection.setAccessible(true);
					selection.setInt(null, previousSelection);
				}
				catch (Exception ignored)
				{
				}
			}
			System.out.println("City Bloxx first-run state initialization failed: " + e.getMessage());
			return false;
		}
	}

	private static boolean seedMissingCityStore(MIDletLoader loader, File storeFile)
	{
		try
		{
			Class<?> stateClass = loader.loadClass("a");
			Class<?> gameClass = loader.loadClass("House");
			Field modeFlag = findStaticField(stateClass, "a", boolean.class);
			Field cityGrid = findStaticField(gameClass, "i", int[][].class);
			modeFlag.setAccessible(true);
			cityGrid.setAccessible(true);

			// House initializes the grid before it reads cityModeRS. Do not create
			// city data from another screen or before that game state exists.
			if(!modeFlag.getBoolean(null) || cityGrid.get(null) == null)
			{
				return false;
			}

			Method saveCity = gameClass.getDeclaredMethod("r");
			saveCity.setAccessible(true);
			saveCity.invoke(null);
			if(!storeFile.isFile() || storeFile.length() <= 16)
			{
				return false;
			}

			System.out.println("Initialized City Bloxx first-run cityModeRS state");
			return true;
		}
		catch (Exception e)
		{
			System.out.println("City Bloxx first-run cityModeRS initialization failed: " + e.getMessage());
			return false;
		}
	}

	private static Field findStaticField(Class<?> owner, String name, Class<?> type)
		throws NoSuchFieldException
	{
		for(Field field : owner.getDeclaredFields())
		{
			if(name.equals(field.getName())
				&& type.equals(field.getType())
				&& Modifier.isStatic(field.getModifiers()))
			{
				return field;
			}
		}
		throw new NoSuchFieldException(owner.getName() + "." + name);
	}
}
