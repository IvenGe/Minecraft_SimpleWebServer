package be.dailysurvival.webserver;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WebServer extends JavaPlugin {

    private ServerSocket serverSocket;
    private Thread acceptorThread;
    private ExecutorService connectionPool;

    @Override
    public void onEnable() {
        // Write the bundled config.yml to plugins/WebServer/ on first run
        saveDefaultConfig();

        if (!validateConfig()) {
            getLogger().severe("Invalid configuration, disabling the plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Path webRoot = createWebRoot(getConfig().getString("WebSiteFolder", "Website"));
        if (webRoot == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        startWebServer(getConfig().getInt("port"), webRoot);
    }

    /**
     * Validates config values. Returns false if any value is invalid.
     */
    private boolean validateConfig() {
        if (!getConfig().isInt("port") || !getConfig().isString("WebSiteFolder")) {
            return false;
        }

        int port = getConfig().getInt("port");
        if (port < 1 || port > 65535) {
            getLogger().severe("Port must be between 1 and 65535, got: " + port);
            return false;
        }
        if (port < 1024) {
            getLogger().warning("Port " + port + " is a privileged port and may require "
                    + "running the server as root/administrator. Consider a port >= 1024 (e.g. 8080).");
        }
        return true;
    }

    /**
     * Creates the website folder inside the plugin's data folder and returns its path.
     */
    private Path createWebRoot(String name) {
        Path root = getDataFolder().toPath().resolve(name).normalize().toAbsolutePath();
        try {
            Files.createDirectories(root);
            return root;
        } catch (IOException e) {
            getLogger().severe("Failed to create website folder '" + name + "': " + e.getMessage());
            return null;
        }
    }

    private void startWebServer(int port, Path webRoot) {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            getLogger().severe("Error starting the web server on port " + port + ": " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boolean logConnections = getConfig().getBoolean("LogNewConnections", false);

        // Each connection is handled on its own virtual thread (cheap, Java 21+)
        connectionPool = Executors.newVirtualThreadPerTaskExecutor();

        // A single dedicated thread blocks on accept() instead of (mis)using the
        // Bukkit scheduler, which would tie up its async thread pool.
        acceptorThread = Thread.ofPlatform()
                .name("WebServer-Acceptor")
                .daemon()
                .start(() -> acceptLoop(webRoot, logConnections));

        getLogger().info("Listening for connections on port " + port);
    }

    private void acceptLoop(Path webRoot, boolean logConnections) {
        while (!serverSocket.isClosed()) {
            try {
                Socket connectionSocket = serverSocket.accept();

                if (logConnections) {
                    getLogger().info("New connection from "
                            + connectionSocket.getInetAddress().getHostAddress());
                }

                connectionPool.execute(new Connection(connectionSocket, webRoot, getLogger()));
            } catch (IOException e) {
                // accept() throws once the socket is closed during shutdown; only
                // report errors that happen while we are still supposed to run.
                if (!serverSocket.isClosed()) {
                    getLogger().warning("Error accepting a new connection: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onDisable() {
        // Closing the socket also unblocks the acceptor thread's accept() call
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                getLogger().warning("Error closing server socket: " + e.getMessage());
            }
        }

        if (acceptorThread != null) {
            acceptorThread.interrupt();
        }

        if (connectionPool != null) {
            connectionPool.shutdown();
            try {
                if (!connectionPool.awaitTermination(3, TimeUnit.SECONDS)) {
                    connectionPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
