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

        RuntimeTiming.setSpeedPercent(1500);
        long preciseSleepStartedAt = System.nanoTime();
        for (int i = 0; i < 64; i++) RuntimeTiming.sleep(16L);
        long preciseSleepAverage = (System.nanoTime() - preciseSleepStartedAt) / 64L;
        if (preciseSleepAverage >= 1_950_000L)
            throw new AssertionError("Fast-forward sleep rounded to whole milliseconds: "
                    + preciseSleepAverage + " ns average");

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
        if (!MemoryEditorGuard.openEditor(false))
            throw new AssertionError("Offline memory editor should open before network use");
        expectNetworkDenied();
        expectNetworkBeginDenied();
        MemoryEditorGuard.closeEditor();
        expectNetworkDenied();
        if (!MemoryEditorGuard.openEditor(true))
            throw new AssertionError(
                    "Reopening after the editor has locked the session must not require offline status");
        expectNetworkDenied();
        MemoryEditorGuard.closeEditor();
        expectNetworkDenied();

        MemoryEditorGuard.resetSession();
        MemoryEditorGuard.checkConnector("datagram://8.8.8.8:53");
        MemoryEditorGuard.checkConnector("socket://:22122");
        MemoryEditorGuard.checkConnector("socket://127.0.0.1:14445");
        MemoryEditorGuard.checkConnector("http://192.168.1.40:8080");
        MemoryEditorGuard.checkConnector("http://game-server.local:8080");
        if (!MemoryEditorGuard.openEditor(false))
            throw new AssertionError("Unsupported probes and local servers must not lock editing");
        MemoryEditorGuard.checkConnector("socket://127.0.0.1:14445");
        expectNetworkDenied();
        MemoryEditorGuard.closeEditor();
        expectNetworkDenied();

        MemoryEditorGuard.resetSession();
        MemoryEditorGuard.checkConnector("https://example.invalid");
        if (MemoryEditorGuard.openEditor(true))
            throw new AssertionError("The editor must remain unavailable while a network is active");
        if (!MemoryEditorGuard.openEditor(false))
            throw new AssertionError("The editor should be allowed after connectivity is disabled");
        MemoryEditorGuard.closeEditor();

        MemoryEditorGuard.resetSession();
        boolean pending = MemoryEditorGuard.beginConnection("https://time.gov/");
        if (!pending || MemoryEditorGuard.openEditor(false))
            throw new AssertionError("An in-flight external connection must block editing");
        MemoryEditorGuard.finishConnection(pending);
        if (!MemoryEditorGuard.openEditor(false))
            throw new AssertionError("A failed external connection must release the editor lock");
        MemoryEditorGuard.closeEditor();

        MemoryEditorGuard.resetSession();
        boolean connected = MemoryEditorGuard.beginConnection("https://example.invalid");
        MemoryEditorGuard.finishConnection(connected);
        if (MemoryEditorGuard.openEditor(true))
            throw new AssertionError("Memory editor opened while network connectivity was active");
        if (!MemoryEditorGuard.openEditor(false))
            throw new AssertionError("Offline mode should permit editing after disconnecting");
        expectNetworkDenied();

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

    private static void expectNetworkBeginDenied() throws Exception {
        try {
            MemoryEditorGuard.beginConnection("https://m3g.example/resource.m3g");
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Direct external resource connection was not denied");
    }
}
