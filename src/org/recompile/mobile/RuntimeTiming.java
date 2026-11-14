/*
 * This file is part of FreeJ2ME.
 *
 * FreeJ2ME is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.recompile.mobile;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import org.recompile.freej2me.protocol.BluetoothProtocol;

/** Session-scoped virtual timing used only by transformed MIDlet bytecode. */
public final class RuntimeTiming {
    public static final int MIN_PERCENT = 25;
    public static final int MAX_PERCENT = BluetoothProtocol.MAX_EMULATION_SPEED_PERCENT;
    public static final int NORMAL_PERCENT = 100;

    private static final Object LOCK = new Object();
    private static int speedPercent = NORMAL_PERCENT;
    private static long hostAnchorNanos = System.nanoTime();
    private static long guestAnchorNanos;
    private static long guestWallAnchorMillis = System.currentTimeMillis();

    private RuntimeTiming() { }

    public static int getSpeedPercent() {
        synchronized (LOCK) {
            return speedPercent;
        }
    }

    public static void reset() {
        synchronized (LOCK) {
            speedPercent = NORMAL_PERCENT;
            hostAnchorNanos = System.nanoTime();
            guestAnchorNanos = 0L;
            guestWallAnchorMillis = System.currentTimeMillis();
        }
    }

    public static void setSpeedPercent(int percent) {
        if (percent < MIN_PERCENT || percent > MAX_PERCENT)
            throw new IllegalArgumentException("MIDlet speed must be between "
                    + MIN_PERCENT + "% and " + MAX_PERCENT + "%");
        synchronized (LOCK) {
            long now = System.nanoTime();
            guestAnchorNanos += scaleElapsed(now - hostAnchorNanos, speedPercent);
            guestWallAnchorMillis += scaleElapsed(now - hostAnchorNanos, speedPercent) / 1_000_000L;
            hostAnchorNanos = now;
            speedPercent = percent;
        }
    }

    public static long currentTimeMillis() {
        synchronized (LOCK) {
            long elapsed = Math.max(0L, System.nanoTime() - hostAnchorNanos);
            return guestWallAnchorMillis
                    + scaleElapsed(elapsed, speedPercent) / 1_000_000L;
        }
    }

    public static long nanoTime() {
        synchronized (LOCK) {
            long elapsed = Math.max(0L, System.nanoTime() - hostAnchorNanos);
            return guestAnchorNanos + scaleElapsed(elapsed, speedPercent);
        }
    }

    private static long scaleElapsed(long nanos, int percent) {
        if (nanos <= 0L) return 0L;
        double scaled = (double) nanos * (double) percent / 100.0d;
        return scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) scaled;
    }

    private static long scaleDelayNanos(long guestNanos) {
        if (guestNanos <= 0L) return Math.max(0L, guestNanos);
        int percent = getSpeedPercent();
        double scaled = (double) guestNanos * 100.0d / (double) percent;
        if (scaled >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, (long) Math.ceil(scaled));
    }

    private static long scaleDelayMillis(long guestMillis) {
        if (guestMillis <= 0L) return Math.max(0L, guestMillis);
        long nanos;
        if (guestMillis > Long.MAX_VALUE / 1_000_000L) nanos = Long.MAX_VALUE;
        else nanos = guestMillis * 1_000_000L;
        long scaled = scaleDelayNanos(nanos);
        return Math.max(1L, scaled / 1_000_000L
                + (scaled % 1_000_000L == 0L ? 0L : 1L));
    }

    public static void sleep(long millis) throws InterruptedException {
        Thread.sleep(scaleDelayMillis(millis));
    }

    public static void sleep(long millis, int nanos) throws InterruptedException {
        if (millis < 0L || nanos < 0 || nanos > 999999)
            throw new IllegalArgumentException();
        long totalNanos;
        if (millis > Long.MAX_VALUE / 1_000_000L) totalNanos = Long.MAX_VALUE;
        else {
            long baseNanos = millis * 1_000_000L;
            totalNanos = nanos > Long.MAX_VALUE - baseNanos
                    ? Long.MAX_VALUE : baseNanos + nanos;
        }
        long scaled = scaleDelayNanos(totalNanos);
        Thread.sleep(scaled / 1_000_000L, (int) (scaled % 1_000_000L));
    }

    public static void waitOn(Object monitor, long millis) throws InterruptedException {
        if (monitor == null) throw new NullPointerException();
        monitor.wait(scaleDelayMillis(millis));
    }

    public static void waitOn(Object monitor, long millis, int nanos)
            throws InterruptedException {
        if (monitor == null) throw new NullPointerException();
        if (millis < 0L || nanos < 0 || nanos > 999999)
            throw new IllegalArgumentException();
        long totalNanos;
        if (millis > Long.MAX_VALUE / 1_000_000L) totalNanos = Long.MAX_VALUE;
        else {
            long baseNanos = millis * 1_000_000L;
            totalNanos = nanos > Long.MAX_VALUE - baseNanos
                    ? Long.MAX_VALUE : baseNanos + nanos;
        }
        long scaled = scaleDelayNanos(totalNanos);
        monitor.wait(scaled / 1_000_000L, (int) (scaled % 1_000_000L));
    }

    public static void schedule(Timer timer, TimerTask task, long delay) {
        timer.schedule(task, scaleDelayMillis(delay));
    }

    public static void schedule(Timer timer, TimerTask task, long delay, long period) {
        timer.schedule(task, scaleDelayMillis(delay), scaleDelayMillis(period));
    }

    public static void schedule(Timer timer, TimerTask task, Date date) {
        timer.schedule(task, hostDateForGuestDate(date));
    }

    public static void schedule(Timer timer, TimerTask task, Date date, long period) {
        timer.schedule(task, hostDateForGuestDate(date), scaleDelayMillis(period));
    }

    public static void scheduleAtFixedRate(Timer timer, TimerTask task, long delay, long period) {
        timer.scheduleAtFixedRate(task, scaleDelayMillis(delay), scaleDelayMillis(period));
    }

    public static void scheduleAtFixedRate(Timer timer, TimerTask task, Date date, long period) {
        timer.scheduleAtFixedRate(task, hostDateForGuestDate(date), scaleDelayMillis(period));
    }

    private static Date hostDateForGuestDate(Date guestDate) {
        long remaining = guestDate.getTime() - currentTimeMillis();
        long hostDelay = scaleDelayMillis(remaining);
        long now = System.currentTimeMillis();
        long deadline = hostDelay > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + hostDelay;
        return new Date(deadline);
    }
}
