package ch.so.agi.jedit.uml._static;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.nio.file.Path;

import org.gjt.sp.util.Log;

public final class LivePreview {
    private static final int DEFAULT_PORT = 17865;
    private static LivePreview INSTANCE;

    private LivePreviewServer server;
    private int port = DEFAULT_PORT;

    private LivePreview() {}

    public static synchronized LivePreview get() {
        if (INSTANCE == null) INSTANCE = new LivePreview();
        return INSTANCE;
    }

    public synchronized void show(Path htmlFile) {
        ensureServer();
        server.setContent(htmlFile);
        server.openInBrowserOnce(); // first time only
        server.reload();            // every call: tell browser to reload
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    /* -------------------- internals -------------------- */

    private void ensureServer() {
        if (server != null) return;
        try {
            server = new LivePreviewServer(port);
            server.start();
        } catch (IOException ex) {
            // Fall back to a free port if the default is taken
            if (ex instanceof BindException) {
                port = findFreePort();
                try {
                    server = new LivePreviewServer(port);
                    server.start();
                } catch (IOException ex2) {
                    throw new RuntimeException("Failed to start LivePreview on port " + port, ex2);
                }
            } else {
                throw new RuntimeException("Failed to start LivePreview", ex);
            }
        }
    }

    private static int findFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            return DEFAULT_PORT + 1;
        }
    }
}
