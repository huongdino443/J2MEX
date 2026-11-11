import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.io.*;

public final class NetworkConnectorSmoke {
    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1 && value != '\n') {
            if (value != '\r') bytes.write(value);
        }
        return bytes.toString("ISO-8859-1");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void respond(Socket socket, int requestNumber) throws IOException {
        socket.setSoTimeout(5000);
        InputStream input = socket.getInputStream();
        String request = readLine(input);
        boolean sawHeader = false;
        int contentLength = 0;
        String line;
        while (!(line = readLine(input)).isEmpty()) {
            if (line.equalsIgnoreCase("X-FreeJ2ME: local-test")) sawHeader = true;
            if (line.toLowerCase().startsWith("content-length:"))
                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
        }
        byte[] requestBody = new byte[contentLength];
        int offset = 0;
        while (offset < requestBody.length) {
            int count = input.read(requestBody, offset, requestBody.length - offset);
            if (count < 0) throw new EOFException("Short HTTP request body");
            offset += count;
        }
        check(requestNumber == 0 ? "GET /health?test=1 HTTP/1.1".equals(request)
                : "POST /submit HTTP/1.1".equals(request), "Wrong HTTP request line: " + request);
        check(sawHeader, "Request header did not reach server");
        if (requestNumber == 1)
            check("ping".equals(new String(requestBody, "UTF-8")), "POST body mismatch");
        byte[] body = (requestNumber == 0 ? "local-ok" : "post-ok").getBytes("UTF-8");
        OutputStream output = socket.getOutputStream();
        output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nX-Test-Reply: yes\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n").getBytes("ISO-8859-1"));
        output.write(body);
        output.flush();
    }
    public static void main(String[] args) throws Exception {
        check(Connector.class.getDeclaredMethod("open", String.class).getReturnType() == Connection.class,
                "Connector.open(String) has the wrong binary return type");
        ServerSocket server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
        AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
        Thread responder = new Thread(() -> {
            try {
                for (int i = 0; i < 2; i++) try (Socket socket = server.accept()) { respond(socket, i); }
            } catch (Throwable error) { serverFailure.set(error); }
        }, "LocalHttpTestServer");
        responder.setDaemon(true);
        responder.start();

        try {
            String base = "http://127.0.0.1:" + server.getLocalPort();
            HttpConnection get = (HttpConnection)Connector.open(base + "/health?test=1");
            get.setRequestProperty("X-FreeJ2ME", "local-test");
            check(get.getResponseCode() == HttpConnection.HTTP_OK, "Unexpected GET response");
            check("yes".equals(get.getHeaderField("X-Test-Reply")), "Response header missing");
            check("local-ok".equals(new String(readAll(get.openInputStream()), "UTF-8")), "GET response body mismatch");
            check("127.0.0.1".equals(get.getHost()) && "http".equals(get.getProtocol()), "URL metadata mismatch");
            get.close();

            HttpConnection post = (HttpConnection)Connector.open(base + "/submit");
            post.setRequestMethod(HttpConnection.POST);
            post.setRequestProperty("X-FreeJ2ME", "local-test");
            try (OutputStream output = post.openOutputStream()) { output.write("ping".getBytes("UTF-8")); }
            check(post.getResponseCode() == HttpConnection.HTTP_OK, "Unexpected POST response");
            check("post-ok".equals(new String(readAll(post.openInputStream()), "UTF-8")), "POST response body mismatch");
            post.close();
            responder.join(5000);
            check(!responder.isAlive(), "Local HTTP server did not finish");
            if (serverFailure.get() != null) throw new AssertionError("Local HTTP server failed", serverFailure.get());

            Connection secure = Connector.open("https://example.invalid/test");
            check(secure instanceof HttpsConnection, "HTTPS URL did not return HttpsConnection");
            secure.close();
            try {
                Connector.open("ftp://127.0.0.1/file");
                throw new AssertionError("Unsupported protocol was accepted");
            } catch (ConnectionNotFoundException expected) { }
            System.out.println("PASS: Connector.open(String) descriptor, local HTTP GET/POST, headers, HTTPS routing, unsupported protocol");
        } finally {
            server.close();
        }
    }
    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int n;
            while ((n = stream.read(buffer)) != -1) output.write(buffer, 0, n);
            return output.toByteArray();
        }
    }
}
