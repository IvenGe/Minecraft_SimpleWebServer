package be.dailysurvival.webserver;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public final class WebServer extends JavaPlugin {

    private ServerSocket serverSocket = null;

    @Override
    public void onEnable() {
        // Plugin startup logic
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        if (!validateConfig()) {
            getLogger().severe("Invalid configuration, disabling the plugin.");
            this.setEnabled(false);
            return;
        }

        createFolder(getConfig().getString("WebSiteFolder"));
        startWebServer(getConfig().getInt("port"));
    }

    private boolean validateConfig() {
        // Validate config values. Return false if any value is invalid.
        // For now, just a basic check. Expand as necessary.
        return getConfig().isInt("port") && getConfig().isString("WebSiteFolder");
    }

    private void startWebServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            getLogger().info("Listening for connections on port " + port);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    acceptConnection();
                }
            }.runTaskTimerAsynchronously(this, 20, 10);
        } catch (IOException e) {
            getLogger().severe("Error starting the web server: " + e.getMessage());
        }
    }

    private void acceptConnection() {
        try {
            Socket connectionSocket = serverSocket.accept();
            Thread connectionThread = new Thread(new Connection(connectionSocket, getConfig()));
            connectionThread.start();

            if (getConfig().getBoolean("LogNewConnections", true)) {  // default to true if not found
                getLogger().info("New connection accepted");
            }
        } catch (IOException e) {
            getLogger().severe("Error accepting a new connection: " + e.getMessage());
        }
    }

    private void createFolder(String name) {
        File folder = new File(this.getDataFolder(), name);
        if (!folder.exists()) {
            if (folder.mkdir()) {
                getLogger().info("Created folder: " + name);
            } else {
                getLogger().warning("Failed to create folder: " + name);
            }
        }
    }

    @Override
    public void onDisable() {
        // Close resources on plugin disable
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                getLogger().warning("Error closing server socket: " + e.getMessage());
            }
        }
    }
}
