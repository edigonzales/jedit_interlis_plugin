package ch.so.agi.jedit.uml._static;

import com.sun.net.httpserver.*;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class LivePreviewServer {
    private final int port;
    private HttpServer server;

    private volatile Path htmlFile;
    private volatile Path rootDir;
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private volatile boolean openedBrowserOnce = false;

    public LivePreviewServer(int port) { this.port = port; }

    public synchronized void start() throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handleRoot);
        server.createContext("/diagram", this::handleDiagram);
        server.createContext("/fs", this::handleStatic);
        server.createContext("/events", this::handleEvents);
        server.start();
        scheduler.scheduleAtFixedRate(this::heartbeat, 15, 15, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        scheduler.shutdownNow();
        clients.forEach(Client::closeQuietly);
        clients.clear();
    }

    public void setContent(Path htmlFile) {
        Objects.requireNonNull(htmlFile, "htmlFile");
        Path p = htmlFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(p)) throw new IllegalArgumentException("Not a file: " + p);
        this.htmlFile = p;
        this.rootDir = p.getParent();
    }

    public String getUrl() {
        return "http://127.0.0.1:" + port + "/diagram";
    }

    public void openInBrowserOnce() {
        if (openedBrowserOnce) return;
        openedBrowserOnce = true;
        try { Desktop.getDesktop().browse(URI.create(getUrl())); } catch (Exception ignore) {}
    }

    public void reload() {
        broadcastEvent("reload", "now");
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }
        ex.getResponseHeaders().add("Location", "/diagram");
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private void handleDiagram(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }
        Path html = htmlFile;
        if (html == null) { send500(ex, "No HTML set"); return; }
        byte[] raw = Files.readAllBytes(html);
        String s = new String(raw, StandardCharsets.UTF_8);
        s = injectBaseAndSse(s);
        byte[] out = s.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.add("Content-Type", "text/html; charset=utf-8");
        h.add("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, out.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(out); }
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }
        Path root = rootDir;
        if (root == null) { send404(ex); return; }
        String raw = ex.getRequestURI().getPath(); // /fs/...
        String rel = raw.substring("/fs".length());
        if (rel.startsWith("/")) rel = rel.substring(1);
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root) || !Files.exists(p) || Files.isDirectory(p)) { send404(ex); return; }
        byte[] body = Files.readAllBytes(p);
        String ct = guessContentType(p);
        Headers h = ex.getResponseHeaders();
        h.add("Content-Type", ct);
        h.add("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void handleEvents(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send405(ex); return; }
        Headers h = ex.getResponseHeaders();
        h.add("Content-Type", "text/event-stream; charset=utf-8");
        h.add("Cache-Control", "no-cache");
        h.add("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0); // chunked
        Client c = new Client(ex);
        clients.add(c);
        c.writeComment("connected");
        c.flush();
        try { c.blockUntilClosed(); } finally { clients.remove(c); c.closeQuietly(); }
    }

    private void heartbeat() {
        for (Client c : clients) {
            try { c.writeComment("hb"); c.flush(); } catch (IOException ignore) { c.closed = true; }
        }
        clients.removeIf(Client::isClosed);
    }

    private void broadcastEvent(String event, String data) {
        for (Client c : clients) {
            try { c.writeEvent(event, data); c.flush(); } catch (IOException ignore) { c.closed = true; }
        }
        clients.removeIf(Client::isClosed);
    }

    private static String injectBaseAndSse(String html) {
        final String marker = "<!-- livepreview-sse -->";
        if (html.contains(marker)) return html;
        String inject = "\n" + marker + "\n" +
            "<base href=\"/fs/\">\n" +
            "<script>(function(){try{var es=new EventSource('/events');es.addEventListener('reload',function(){location.reload();});}catch(e){}})();</script>\n";
        int head = html.indexOf("<head");
        if (head >= 0) {
            int gt = html.indexOf('>', head);
            if (gt >= 0) return html.substring(0, gt + 1) + inject + html.substring(gt + 1);
        }
        return inject + html;
    }

    private static void send404(HttpExchange ex) throws IOException {
        byte[] b = "Not found".getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(404, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
    private static void send405(HttpExchange ex) throws IOException {
        byte[] b = "Method not allowed".getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(405, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
    private static void send500(HttpExchange ex, String msg) throws IOException {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(500, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static String guessContentType(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=utf-8";
        if (name.endsWith(".css"))  return "text/css; charset=utf-8";
        if (name.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg"))  return "image/svg+xml";
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif"))  return "image/gif";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }

    private static final class Client {
        final HttpExchange ex;
        final OutputStream out;
        volatile boolean closed = false;
        Client(HttpExchange ex) throws IOException { this.ex = ex; this.out = new BufferedOutputStream(ex.getResponseBody()); }
        void writeEvent(String event, String data) throws IOException {
            writeRaw("event: " + event + "\n");
            writeRaw("data: " + data + "\n\n");
        }
        void writeComment(String text) throws IOException { writeRaw(":" + text + "\n\n"); }
        void writeRaw(String s) throws IOException { out.write(s.getBytes(StandardCharsets.UTF_8)); }
        void flush() throws IOException { out.flush(); }
        boolean isClosed() { return closed; }
        void closeQuietly() { closed = true; try { out.close(); } catch (Exception ignore) {} try { ex.close(); } catch (Exception ignore) {} }
        void blockUntilClosed() {
            try { while (!closed) Thread.sleep(60_000); } catch (InterruptedException ignore) { closed = true; }
        }
    }
}
