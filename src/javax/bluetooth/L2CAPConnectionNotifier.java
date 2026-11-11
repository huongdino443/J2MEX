package javax.bluetooth;

import java.io.IOException;
import javax.microedition.io.Connection;

/** JSR-82 L2CAP listener. */
public interface L2CAPConnectionNotifier extends Connection {
    L2CAPConnection acceptAndOpen() throws IOException;
}
