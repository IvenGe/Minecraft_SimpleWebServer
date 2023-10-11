package be.dailysurvival.webserver;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class ConfigManager {
    private static File file;
    private static FileConfiguration fileConfiguration;
    private static Logger logger = Bukkit.getLogger();

    public static void setup() {
        file = new File(Bukkit.getServer().getPluginManager().getPlugin("WebServer").getDataFolder(), "WebServerConfig.yml");

        // Check if the config file exists; if not, create it with default values
        if (!file.exists()) {
            logger.warning("WebServerConfig.yml does not exist. Creating default configuration...");
            createDefaultConfig();
        }

        fileConfiguration = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Creates a default configuration file.
     */
    private static void createDefaultConfig() {
        try {
            file.createNewFile();
            fileConfiguration = YamlConfiguration.loadConfiguration(file);
            fileConfiguration.addDefault("port", 80);
            fileConfiguration.addDefault("WebSiteFolder", "Website");
            fileConfiguration.addDefault("LogNewConnections",false);
            fileConfiguration.options().copyDefaults(true);
            save();
        } catch (IOException e) {
            logger.severe("Error while creating WebServerConfig.yml: " + e.getMessage());
        }
    }

    public static FileConfiguration get() {
        return fileConfiguration;
    }

    public static void save() {
        try {
            fileConfiguration.save(file);
        } catch (IOException e) {
            logger.severe("Error while saving WebServerConfig.yml: " + e.getMessage());
        }
    }
}
