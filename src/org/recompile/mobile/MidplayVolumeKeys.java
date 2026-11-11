package org.recompile.mobile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.microedition.lcdui.Displayable;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * MIDPlay's progressive-stream busy gate drops vertical keys. Use its own
 * controller action, not a second global player volume, so its level and
 * on-Canvas volume indicator stay synchronized. Restricted to the verified
 * controller signature; other MIDlets never enter this compatibility path.
 */
final class MidplayVolumeKeys {
    private final Object controller;
    private final Method action;
    private final VolumeControl control;
    private final int before;

    private MidplayVolumeKeys(Object controller, Method action, VolumeControl control) {
        this.controller = controller; this.action = action; this.control = control;
        before = control.getLevel();
    }
    static MidplayVolumeKeys capture(Displayable screen) {
        try {
            Method getter = screen.getClass().getMethod("m");
            getter.setAccessible(true);
            Object controller = getter.invoke(screen);
            if (controller == null) return null;
            Method action = controller.getClass().getMethod("a", boolean.class);
            action.setAccessible(true);
            Field field = controller.getClass().getDeclaredField("b");
            if (!Player.class.isAssignableFrom(field.getType())) return null;
            field.setAccessible(true);
            Player player = (Player)field.get(controller);
            VolumeControl volume = player == null ? null : (VolumeControl)player.getControl("VolumeControl");
            if (volume == null) {
                System.out.println("[midplay-volume] no controller VolumeControl; player=" + player);
                return null;
            }
            return new MidplayVolumeKeys(controller, action, volume);
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.out.println("[midplay-volume] unsupported controller: " + error);
            return null;
        }
    }
    void finish(boolean up) {
        try {
            boolean fallback = control.getLevel() == before;
            if (fallback) action.invoke(controller, up);
            System.out.println("[midplay-volume] " + (up ? "up" : "down") + " " + before + "->"
                    + control.getLevel() + " via " + (fallback ? "MIDPlay controller action" : "MIDPlay key handler"));
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.out.println("[midplay-volume] controller action failed: " + error);
        }
    }
}
