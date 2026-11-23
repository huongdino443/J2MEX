package org.recompile.freej2me.lite;

/** Resolves whether a Recents re-entry should restore the game left in front. */
final class RuntimeReturnPolicy {
    private RuntimeReturnPolicy() { }

    static boolean shouldReturnToForegroundRuntime(
            String requestedSessionId,
            String foregroundSessionId,
            boolean homePinned,
            boolean explicitHomePending,
            boolean sessionManagerPending,
            boolean explicitSessionPending,
            boolean externalGamePending) {
        return requestedSessionId != null
                && requestedSessionId.equals(foregroundSessionId)
                && !homePinned
                && !explicitHomePending
                && !sessionManagerPending
                && !explicitSessionPending
                && !externalGamePending;
    }
}
