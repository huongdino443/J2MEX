package org.recompile.freej2me.lite;

public final class RuntimeReturnPolicySmoke {
    public static void main(String[] args) {
        check(route("session-a", "session-a", false, false, false, false, false),
                "Recents should restore the same game that was left in front");
        check(!route("session-a", "session-b", false, false, false, false, false),
                "a different foreground session must not be replaced");
        check(!route(null, "session-a", false, false, false, false, false),
                "a missing Recents marker must not trigger a game switch");
        check(!route("session-a", "session-a", true, false, false, false, false),
                "an explicitly pinned Home screen must remain Home");
        check(!route("session-a", "session-a", false, true, false, false, false),
                "an explicit Home request must remain Home");
        check(!route("session-a", "session-a", false, false, true, false, false),
                "opening the session manager must not redirect immediately");
        check(!route("session-a", "session-a", false, false, false, true, false),
                "an explicit session target must take priority");
        check(!route("session-a", "session-a", false, false, false, false, true),
                "opening an external game must take priority");
        System.out.println("RuntimeReturnPolicySmoke: Recents return priorities passed");
    }

    private static boolean route(String requestedSession, String foregroundSession,
            boolean homePinned, boolean explicitHome, boolean manager,
            boolean session, boolean externalGame) {
        return RuntimeReturnPolicy.shouldReturnToForegroundRuntime(
                requestedSession, foregroundSession, homePinned, explicitHome,
                manager, session, externalGame);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
