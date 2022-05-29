package be.dailysurvival.webserver;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public final class WebServer extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getConfig().options().copyDefaults();
        saveDefaultConfig();
        createFolder(getConfig().getString("WebSiteFolder"));
        // Listen for new client connections
        runnable(getConfig().getInt("port"));


    }

    private ServerSocket serverSocket = null;

    public void runnable(int port) {


        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Listening for connections on port" + port);
        new BukkitRunnable() {
            @Override
            public void run() {

                StartServer();

            }
        }.runTaskTimerAsynchronously(this, 20, 10);
    }

    private void StartServer() {


        // Accept new client connection
        Socket connectionSocket = null;
        try {
            connectionSocket = serverSocket.accept();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create new thread to handle client request
        Thread connectionThread = new Thread(new Connection(connectionSocket, getConfig()));

        // Start the connection thread
        connectionThread.start();
        System.out.println("New connection");

    }

    private void createFolder(String name) {
        File f = new File(this.getDataFolder() + "/"+ name);
        if(!f.exists())
            f.mkdir();
    }
}
