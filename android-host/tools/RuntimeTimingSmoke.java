package org.recompile.mobile;

import java.io.IOException;

public final class RuntimeTimingSmoke {
    public static void main(String[] args) throws Exception {
        RuntimeTiming.reset();
        if (RuntimeTiming.getSpeedPercent() != RuntimeTiming.NORMAL_PERCENT)
            throw new AssertionError("Runtime timing did not reset to 1x");

        long before = RuntimeTiming.nanoTime();
        RuntimeTiming.setSpeedPercent(800);
        Thread.sleep(30L);
        long fastElapsed = RuntimeTiming.nanoTime() - before;
        if (fastElapsed < 100_000_000L)
            throw new AssertionError("Guest monotonic clock did not accelerate: " + fastElapsed);

        RuntimeTiming.setSpeedPercent(50);
        long slowBefore = RuntimeTiming.nanoTime();
        Thread.sleep(40L);
        long slowElapsed = RuntimeTiming.nanoTime() - slowBefore;
        if (slowElapsed < 5_000_000L)
            throw new AssertionError("Guest monotonic clock stopped at low speed");

        boolean rejected = false;
        try {
            RuntimeTiming.setSpeedPercent(0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("Invalid speed was accepted");

        RuntimeTiming.setSpeedPercent(RuntimeTiming.MAX_PERCENT);
        if (RuntimeTiming.getSpeedPercent() != 2000)
            throw new AssertionError("The 20x speed limit was not accepted");
        rejected = false;
        try {
            RuntimeTiming.setSpeedPercent(RuntimeTiming.MAX_PERCENT + 1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("Speed above 20x was accepted");

        MemoryEditorGuard.resetSession();
        if (!MemoryEditorGuard.openEditor())
            throw new AssertionError("Offline memory editor should open before network use");
        expectNetworkDenied();
        MemoryEditorGuard.closeEditor();
        expectNetworkDenied();

        MemoryEditorGuard.resetSession();
        MemoryEditorGuard.checkConnector("https://example.invalid");
        if (MemoryEditorGuard.openEditor())
            throw new AssertionError("Memory editor opened after network use");

        RuntimeTiming.reset();
        System.out.println("RuntimeTimingSmoke passed");
    }

    private static void expectNetworkDenied() throws Exception {
        try {
            MemoryEditorGuard.checkConnector("http://example.invalid");
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Network connector was not denied");
    }
}
