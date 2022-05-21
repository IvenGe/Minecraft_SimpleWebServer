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

        createFolder("WebServer");
        // Plugin startup logic
        runnable();
        // Listen for new client connections

    }

    private ServerSocket serverSocket = null;

    public void runnable() {


        try {
            serverSocket = new ServerSocket(80);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Listening for connections on port 80...");
        new BukkitRunnable() {
            @Override
            public void run() {

                StartServer();

            }
        }.runTaskTimerAsynchronously(this, 40, 5);
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
        Thread connectionThread = new Thread(new Connection(connectionSocket));

        // Start the connection thread
        connectionThread.start();
        System.out.println("New connection on port 80...");

    }

    private void createFolder(String name) {
        File f = new File(this.getDataFolder() + "/");
        if(!f.exists())
            f.mkdir();
    }
}
