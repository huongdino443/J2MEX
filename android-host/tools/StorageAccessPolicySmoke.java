package org.recompile.freej2me.lite;

public final class StorageAccessPolicySmoke {
    public static void main(String[] args) {
        for (int sdk = 21; sdk <= 35; sdk++) {
            boolean allFiles = sdk >= 30;
            boolean runtimePermissions = sdk >= 23 && sdk < 30;
            boolean writePermission = sdk >= 23 && sdk <= 29;
            check(StorageAccessPolicy.usesAllFilesAccess(sdk) == allFiles,
                    "all-files setting at API " + sdk);
            check(StorageAccessPolicy.usesLegacyRuntimePermissions(sdk) == runtimePermissions,
                    "legacy runtime permission path at API " + sdk);
            check(StorageAccessPolicy.requiresLegacyWritePermission(sdk) == writePermission,
                    "legacy write permission at API " + sdk);

            boolean expectedWithoutGrant = sdk < 23;
            check(StorageAccessPolicy.hasStorageAccess(sdk, false, false, false)
                            == expectedWithoutGrant,
                    "access denied state at API " + sdk);

            boolean expectedWithLegacyGrants = sdk < 30;
            check(StorageAccessPolicy.hasStorageAccess(sdk, true, true, false)
                            == expectedWithLegacyGrants,
                    "legacy grant state at API " + sdk);
            check(StorageAccessPolicy.hasStorageAccess(sdk, false, false, true)
                            == (sdk < 23 || allFiles),
                    "all-files grant state at API " + sdk);
        }
        System.out.println("StorageAccessPolicySmoke: API 21-35 permission boundaries passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
