package org.recompile.freej2me.lite;

/** API-level storage access rules for shared J2MEX game data. */
final class StorageAccessPolicy {
    private StorageAccessPolicy() { }

    static boolean usesAllFilesAccess(int sdk) {
        return sdk >= 30;
    }

    static boolean usesLegacyRuntimePermissions(int sdk) {
        return sdk >= 23 && sdk < 30;
    }

    static boolean requiresLegacyWritePermission(int sdk) {
        return sdk >= 23 && sdk <= 29;
    }

    static boolean hasLegacyAccess(int sdk, boolean readGranted, boolean writeGranted) {
        if (sdk < 23) return true;
        if (sdk >= 30) return false;
        return readGranted && (!requiresLegacyWritePermission(sdk) || writeGranted);
    }

    static boolean hasStorageAccess(int sdk, boolean readGranted, boolean writeGranted,
            boolean allFilesGranted) {
        return usesAllFilesAccess(sdk)
                ? allFilesGranted
                : hasLegacyAccess(sdk, readGranted, writeGranted);
    }
}
