import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.midlet.MIDlet;
import org.recompile.mobile.BluetoothBridge;

/** Checks Android Bluetooth permission, adapter enablement, and JSR-82 inquiry. */
public final class BluetoothDiagnosticMIDlet extends MIDlet implements CommandListener {
    private final Display display = Display.getDisplay(this);
    private Form form;
    private StringItem output;
    private String log = "";
    private volatile boolean busy;
    private volatile boolean inquiryRunning;
    private volatile DiscoveryAgent agent;

    private final Command checkAccess = new Command("Kiem tra quyen", Command.SCREEN, 1);
    private final Command startInquiry = new Command("Do thiet bi", Command.SCREEN, 2);
    private final Command stopInquiry = new Command("Dung do", Command.SCREEN, 3);
    private final Command clear = new Command("Xoa log", Command.SCREEN, 4);
    private final Command exit = new Command("Thoat", Command.EXIT, 5);

    private final DiscoveryListener listener = new DiscoveryListener() {
        public void deviceDiscovered(RemoteDevice device, DeviceClass deviceClass) {
            append("DEVICE: " + device.getBluetoothAddress()
                    + (device.getFriendlyName(false) == null ? "" : "  " + device.getFriendlyName(false))
                    + " class=0x" + Integer.toHexString(deviceClass.getServiceClasses()
                            | deviceClass.getMajorDeviceClass() | deviceClass.getMinorDeviceClass()));
        }
        public void inquiryCompleted(int code) {
            inquiryRunning = false;
            append("INQUIRY COMPLETED: " + code + " (" + inquiryResult(code) + ")");
        }
        public void servicesDiscovered(int transactionId, ServiceRecord[] records) {
            append("SERVICES: transaction=" + transactionId + " records="
                    + (records == null ? 0 : records.length));
        }
        public void serviceSearchCompleted(int transactionId, int responseCode) {
            append("SERVICE SEARCH: transaction=" + transactionId + " result=" + responseCode);
        }
    };

    protected void startApp() {
        if (form == null) {
            form = new Form("Bluetooth Diagnostic");
            output = new StringItem("Log", "Ready. Chon Kiem tra quyen truoc.");
            form.append(output);
            form.addCommand(checkAccess);
            form.addCommand(startInquiry);
            form.addCommand(stopInquiry);
            form.addCommand(clear);
            form.addCommand(exit);
            form.setCommandListener(this);
        }
        display.setCurrent(form);
    }

    protected void pauseApp() { }

    protected void destroyApp(boolean unconditional) {
        if (inquiryRunning && agent != null) {
            try { agent.cancelInquiry(listener); }
            catch (Throwable ignored) { }
        }
    }

    public void commandAction(Command command, Displayable screen) {
        if (command == exit) {
            notifyDestroyed();
        } else if (command == clear) {
            synchronized (this) { log = ""; }
            refresh();
        } else if (command == checkAccess) {
            runAccessCheck();
        } else if (command == startInquiry) {
            runInquiry();
        } else if (command == stopInquiry) {
            stopInquiry();
        }
    }

    private void runAccessCheck() {
        if (!beginOperation()) return;
        append("CALL: LocalDevice.getLocalDevice()");
        new Thread(new Runnable() {
            public void run() {
                try {
                    append("BRIDGE: transport visible=" + BluetoothBridge.hasTransport()
                            + "; loader=" + BluetoothBridge.class.getClassLoader());
                    LocalDevice local = LocalDevice.getLocalDevice();
                    agent = local.getDiscoveryAgent();
                    append("RESULT: ACCESS_OK; Bluetooth API responded.");
                    try {
                        String name = local.getFriendlyName();
                        append("ADAPTER NAME: " + (name == null ? "(empty)" : name));
                    } catch (Throwable error) {
                        appendError("GET NAME", error);
                    }
                } catch (Throwable error) {
                    appendError("ACCESS FAILED", error);
                } finally {
                    busy = false;
                }
            }
        }, "BluetoothAccessDiagnostic").start();
    }

    private void runInquiry() {
        if (!beginOperation()) return;
        append("CALL: LocalDevice.getLocalDevice() then startInquiry(GIAC)");
        new Thread(new Runnable() {
            public void run() {
                try {
                    LocalDevice local = LocalDevice.getLocalDevice();
                    agent = local.getDiscoveryAgent();
                    boolean started = agent.startInquiry(DiscoveryAgent.GIAC, listener);
                    inquiryRunning = started;
                    append("RESULT: startInquiry returned " + started);
                    if (!started) append("No inquiry started. See INQUIRY_ERROR or ACCESS FAILED above.");
                } catch (Throwable error) {
                    inquiryRunning = false;
                    appendError("INQUIRY FAILED", error);
                } finally {
                    busy = false;
                }
            }
        }, "BluetoothInquiryDiagnostic").start();
    }

    private void stopInquiry() {
        final DiscoveryAgent current = agent;
        if (!inquiryRunning || current == null) {
            append("STOP: no active inquiry.");
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    append("CALL: cancelInquiry()");
                    current.cancelInquiry(listener);
                } catch (Throwable error) {
                    appendError("STOP FAILED", error);
                }
            }
        }, "BluetoothStopDiagnostic").start();
    }

    private synchronized boolean beginOperation() {
        if (busy) {
            append("BUSY: wait for the current check to finish.");
            return false;
        }
        busy = true;
        return true;
    }

    private void appendError(String label, Throwable error) {
        String message = error.getMessage();
        append(label + ": " + error.getClass().getName()
                + (message == null || message.length() == 0 ? "" : " — " + message));
    }

    private void append(String line) {
        synchronized (this) {
            log += (log.length() == 0 ? "" : "\n") + line;
            if (log.length() > 5000) log = log.substring(log.length() - 5000);
        }
        refresh();
    }

    private void refresh() {
        display.callSerially(new Runnable() {
            public void run() {
                if (output != null) {
                    synchronized (BluetoothDiagnosticMIDlet.this) {
                        output.setText(log);
                    }
                }
            }
        });
    }

    private static String inquiryResult(int code) {
        switch (code) {
            case DiscoveryListener.INQUIRY_COMPLETED: return "completed";
            case DiscoveryListener.INQUIRY_TERMINATED: return "cancelled";
            case DiscoveryListener.INQUIRY_ERROR: return "error / access denied / adapter unavailable";
            default: return "unknown";
        }
    }
}
