package org.recompile.freej2me.protocol;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Java 8/Android shared wire model. No Android, AWT or MIDP dependencies. */
public final class NativeScreen {
    public static final int FRAMEBUFFER = 0, LIST = 1, FORM = 2, COMMAND_MENU = 3, ALERT = 4;
    public static final int EXCLUSIVE = 1, MULTIPLE = 2, IMPLICIT = 3, POPUP = 4;
    public static final int STRING = 1, IMAGE = 2, TEXT = 3, CHOICE = 4,
            GAUGE = 5, UNSUPPORTED = 6, ROW = 7, SPACER = 8,
            CUSTOM = 9, DATE_FIELD = 10;
    public static final int APPEARANCE_PLAIN = 0, APPEARANCE_HYPERLINK = 1,
            APPEARANCE_BUTTON = 2;
    public static final int DATE_MODE_DATE = 1, DATE_MODE_DATE_TIME = 2, DATE_MODE_TIME = 3;
    public static final int ITEM_LAYOUT_LEFT = 1, ITEM_LAYOUT_RIGHT = 2, ITEM_LAYOUT_CENTER = 3;
    private static final int VERSION = 2;
    private static final int MAX_ITEMS = 4096, MAX_TEXT = 1048576, MAX_PACKET = 16777216;
    public int epoch, kind, choiceType, focusId;
    public long revision, acknowledgedSequence;
    public String title = "", ticker = "";
    public final List<Entry> items = new ArrayList<>();
    public final List<Action> commands = new ArrayList<>();

    public static final class Entry {
        public int id, kind, constraints, maxSize, value, maximum, choiceType, actionId, caret;
        public boolean selected, interactive, longPressEnabled;
        public String label = "", text = "";
        public byte[] image = new byte[0];
        public final List<Entry> children = new ArrayList<>();
    }
    public static final class Action {
        public int id, type, priority;
        public String label = "";
    }
    public static final class State {
        public int id, caret, value;
        public String text = "";
        public boolean[] flags = new boolean[0];
    }
    public static final class Event {
        public static final int CHANGE = 0, COMMAND = 1, SELECT = 2, ITEM = 3, BACK = 4;
        public int epoch, action, target;
        public long sequence;
        public final List<State> states = new ArrayList<>();
    }

    public void write(DataOutputStream out) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(VERSION);
        data.writeInt(epoch); data.writeLong(revision); data.writeLong(acknowledgedSequence); data.writeInt(kind);
        text(data, title); text(data, ticker);
        data.writeInt(choiceType); data.writeInt(focusId);
        count(data, commands.size(), 256);
        for (Action c : commands) {
            data.writeInt(c.id); data.writeInt(c.type); data.writeInt(c.priority); text(data, c.label);
        }
        count(data, items.size(), MAX_ITEMS);
        for (Entry item : items) writeEntry(data, item, 0);
        packet(out, bytes);
    }
    public static NativeScreen read(DataInputStream input) throws IOException {
        DataInputStream in = packet(input);
        version(in);
        NativeScreen s = new NativeScreen();
        s.epoch = in.readInt(); s.revision = in.readLong(); s.acknowledgedSequence = in.readLong(); s.kind = in.readInt();
        if (s.kind < 0 || s.kind > ALERT) throw new IOException("Unknown native screen");
        s.title = text(in); s.ticker = text(in);
        s.choiceType = in.readInt(); s.focusId = in.readInt();
        int count = count(in, 256);
        for (int i = 0; i < count; i++) {
            Action c = new Action();
            c.id = in.readInt(); c.type = in.readInt(); c.priority = in.readInt(); c.label = text(in);
            s.commands.add(c);
        }
        count = count(in, MAX_ITEMS);
        for (int i = 0; i < count; i++) s.items.add(readEntry(in, 0));
        exhausted(in);
        return s;
    }
    private static void writeEntry(DataOutputStream out, Entry e, int depth) throws IOException {
        if (depth > 1) throw new IOException("Nested native item");
        out.writeInt(e.id); out.writeInt(e.kind); text(out, e.label); text(out, e.text);
        blob(out, e.image, 4194304);
        out.writeInt(e.constraints); out.writeInt(e.maxSize); out.writeInt(e.value);
        out.writeInt(e.maximum); out.writeInt(e.choiceType); out.writeInt(e.actionId); out.writeInt(e.caret);
        out.writeBoolean(e.selected); out.writeBoolean(e.interactive);
        out.writeBoolean(e.longPressEnabled);
        count(out, e.children.size(), MAX_ITEMS);
        for (Entry child : e.children) writeEntry(out, child, depth + 1);
    }
    private static Entry readEntry(DataInputStream in, int depth) throws IOException {
        if (depth > 1) throw new IOException("Nested native item");
        Entry e = new Entry();
        e.id = in.readInt(); e.kind = in.readInt(); e.label = text(in); e.text = text(in);
        e.image = blob(in, 4194304);
        e.constraints = in.readInt(); e.maxSize = in.readInt(); e.value = in.readInt();
        e.maximum = in.readInt(); e.choiceType = in.readInt(); e.actionId = in.readInt(); e.caret = in.readInt();
        e.selected = in.readBoolean(); e.interactive = in.readBoolean();
        e.longPressEnabled = in.readBoolean();
        int count = count(in, MAX_ITEMS);
        for (int i = 0; i < count; i++) e.children.add(readEntry(in, depth + 1));
        return e;
    }
    public static void writeEvent(DataOutputStream out, Event event) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(VERSION); data.writeInt(event.epoch); data.writeLong(event.sequence); data.writeInt(event.action); data.writeInt(event.target);
        count(data, event.states.size(), MAX_ITEMS);
        for (State s : event.states) {
            data.writeInt(s.id); text(data, s.text); data.writeInt(s.caret); data.writeInt(s.value);
            count(data, s.flags.length, MAX_ITEMS);
            for (boolean flag : s.flags) data.writeBoolean(flag);
        }
        packet(out, bytes);
    }
    public static Event readEvent(DataInputStream input) throws IOException {
        DataInputStream in = packet(input); version(in);
        Event e = new Event();
        e.epoch = in.readInt(); e.sequence = in.readLong(); e.action = in.readInt(); e.target = in.readInt();
        if (e.action < 0 || e.action > 4) throw new IOException("Unknown native event");
        int count = count(in, MAX_ITEMS);
        for (int i = 0; i < count; i++) {
            State s = new State();
            s.id = in.readInt(); s.text = text(in); s.caret = in.readInt(); s.value = in.readInt();
            s.flags = new boolean[count(in, MAX_ITEMS)];
            for (int f = 0; f < s.flags.length; f++) s.flags[f] = in.readBoolean();
            e.states.add(s);
        }
        exhausted(in); return e;
    }
    private static void version(DataInputStream in) throws IOException {
        if (in.readInt() != VERSION) throw new IOException("Native UI protocol version mismatch");
    }
    private static void exhausted(DataInputStream in) throws IOException {
        if (in.available() != 0) throw new IOException("Trailing native UI data");
    }
    private static void packet(DataOutputStream out, ByteArrayOutputStream bytes) throws IOException {
        if (bytes.size() > MAX_PACKET) throw new IOException("Native UI packet too large");
        out.writeInt(bytes.size()); bytes.writeTo(out);
    }
    private static DataInputStream packet(DataInputStream in) throws IOException {
        int length = count(in, MAX_PACKET);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
    private static void text(DataOutputStream out, String s) throws IOException {
        blob(out, (s == null ? "" : s).getBytes("UTF-8"), MAX_TEXT);
    }
    private static String text(DataInputStream in) throws IOException { return new String(blob(in, MAX_TEXT), "UTF-8"); }
    private static void blob(DataOutputStream out, byte[] bytes, int max) throws IOException {
        if (bytes.length > max) throw new IOException("Native UI value too large");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static byte[] blob(DataInputStream in, int max) throws IOException {
        int length = count(in, max);
        if (length > in.available()) throw new EOFException("Truncated native UI packet");
        byte[] bytes = new byte[length]; in.readFully(bytes); return bytes;
    }
    private static void count(DataOutputStream out, int value, int max) throws IOException {
        if (value < 0 || value > max) throw new IOException("Native UI count out of bounds");
        out.writeInt(value);
    }
    private static int count(DataInputStream in, int max) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > max) throw new IOException("Native UI length out of bounds");
        return value;
    }
}
