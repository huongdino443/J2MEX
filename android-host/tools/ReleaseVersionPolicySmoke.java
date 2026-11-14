package org.recompile.freej2me.lite;

public final class ReleaseVersionPolicySmoke {
    public static void main(String[] args) {
        check(ReleaseVersionPolicy.compare("v0.1.0", "0.1.0") == 0,
                "v prefix should be ignored");
        check(ReleaseVersionPolicy.compare("v0.1.10", "0.1.9") > 0,
                "multi-digit patch versions should compare numerically");
        check(ReleaseVersionPolicy.compare("1.0.0", "0.99.99") > 0,
                "major version should take precedence");
        check(ReleaseVersionPolicy.compare("0.1.0", "0.1.1") < 0,
                "older releases should compare lower");
        System.out.println("ReleaseVersionPolicySmoke: OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
