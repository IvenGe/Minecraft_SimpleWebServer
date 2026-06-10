package be.dailysurvival.webserver;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles a single HTTP connection. Each instance runs on its own (virtual) thread.
 */
public class Connection implements Runnable {

    // URL paths that are redirected to another location (301)
    private static final Map<String, String> REDIRECTS = Map.of(
            "/", "/index.html",
            "/index.htm", "/index.html",
            "/index", "/index.html"
    );

    private final Socket connectionSocket;
    private final Path webRoot;
    private final Logger logger;

    // Key/value pairs parsed from the client request
    private final Map<String, String> request = new HashMap<>();

    /**
     * @param connectionSocket the socket the client used to connect to the server
     * @param webRoot          absolute, normalized path of the folder to serve files from
     * @param logger           the plugin logger
     */
    public Connection(Socket connectionSocket, Path webRoot, Logger logger) {
        this.connectionSocket = connectionSocket;
        this.webRoot = webRoot;
        this.logger = logger;
    }

    @Override
    public void run() {
        try (connectionSocket) {
            OutputStream out = new BufferedOutputStream(connectionSocket.getOutputStream());
            if (parseRequest()) {
                sendResponse(out);
            } else {
                sendError(out, "400 Bad Request");
            }
            out.flush();
        } catch (IOException ex) {
            // Client disconnects etc. are normal; don't spam the console
            logger.log(Level.FINE, "Connection error", ex);
        }
    }

    /**
     * Parses the client request into the request map.
     *
     * @return true if the request was well-formed, false otherwise
     */
    private boolean parseRequest() throws IOException {
        BufferedReader connectionReader = new BufferedReader(
                new InputStreamReader(connectionSocket.getInputStream(), StandardCharsets.ISO_8859_1));

        // Top line of a request, e.g.: GET /index.html HTTP/1.1
        String requestLine = connectionReader.readLine();
        if (requestLine == null) {
            return false;
        }

        String[] requestLineParams = requestLine.split(" ");
        if (requestLineParams.length != 3) {
            return false;
        }

        request.put("Method", requestLineParams[0]);
        request.put("Resource", requestLineParams[1]);
        request.put("Protocol", requestLineParams[2]);

        // Read the header fields (guard against the client closing mid-request)
        String headerLine = connectionReader.readLine();
        while (headerLine != null && !headerLine.isEmpty()) {
            String[] requestParams = headerLine.split(":", 2);
            if (requestParams.length == 2) {
                request.put(requestParams[0], requestParams[1].trim());
            }
            headerLine = connectionReader.readLine();
        }
        return true;
    }

    /**
     * Sends the appropriate response based on the client request:
     * 301 for redirected paths, 200 for files inside the web root,
     * 404 otherwise. Only GET and HEAD are allowed.
     */
    private void sendResponse(OutputStream out) throws IOException {
        String method = request.get("Method");
        boolean headOnly = "HEAD".equals(method);
        if (!"GET".equals(method) && !headOnly) {
            writeHeaders(out, "405 Method Not Allowed", null, 0, "Allow: GET, HEAD");
            return;
        }

        // Decode the request target (strips ?query/#fragment, resolves %xx escapes)
        String resourcePath;
        try {
            resourcePath = new URI(request.get("Resource")).getPath();
        } catch (URISyntaxException e) {
            sendError(out, "400 Bad Request");
            return;
        }
        if (resourcePath == null || resourcePath.isEmpty()) {
            sendError(out, "400 Bad Request");
            return;
        }

        // Redirect known aliases (e.g. "/" -> "/index.html")
        String redirectTarget = REDIRECTS.get(resourcePath);
        if (redirectTarget != null) {
            writeHeaders(out, "301 Moved Permanently", null, 0, "Location: " + redirectTarget);
            return;
        }

        // Resolve the requested file inside the web root and make sure the
        // result cannot escape it (blocks path traversal like /../server.properties)
        Path file;
        try {
            file = webRoot.resolve(resourcePath.substring(1)).normalize();
        } catch (InvalidPathException e) {
            sendError(out, "400 Bad Request");
            return;
        }

        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            sendError(out, "404 Not Found");
            return;
        }

        // Determine the MIME type, falling back to a generic binary type
        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        writeHeaders(out, "200 OK", contentType, Files.size(file), null);
        if (!headOnly) {
            Files.copy(file, out);
        }
    }

    /**
     * Sends an error status with a small HTML page describing it.
     */
    private void sendError(OutputStream out, String status) throws IOException {
        String body = """
                <!DOCTYPE html>
                <html>
                <head><title>%s</title></head>
                <body><h1>%s</h1></body>
                </html>
                """.formatted(status, status);

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        writeHeaders(out, status, "text/html; charset=utf-8", bodyBytes.length, null);
        if (!"HEAD".equals(request.get("Method"))) {
            out.write(bodyBytes);
        }
    }

    /**
     * Writes a full HTTP response header block (status line + headers + blank line).
     */
    private void writeHeaders(OutputStream out, String status, String contentType,
                              long contentLength, String extraHeader) throws IOException {
        StringBuilder header = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n");
        if (extraHeader != null) {
            header.append(extraHeader).append("\r\n");
        }
        if (contentType != null) {
            header.append("Content-Type: ").append(contentType).append("\r\n");
        }
        header.append("Content-Length: ").append(contentLength).append("\r\n");
        header.append("Connection: close\r\n\r\n");
        out.write(header.toString().getBytes(StandardCharsets.US_ASCII));
    }
}
