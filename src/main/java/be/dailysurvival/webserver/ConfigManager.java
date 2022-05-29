package be.dailysurvival.webserver;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private static File file;
    private static FileConfiguration fileConfiguration;
    public static void setup(){
        file = new File(Bukkit.getServer().getPluginManager().getPlugin("WebServer").getDataFolder(), "WebServerConfig.yml");
        if(!file.exists()){
            System.out.println("File does not exist");
            try{
                file.createNewFile();
            } catch (IOException e) {
                System.out.println(e);
            }
            fileConfiguration = YamlConfiguration.loadConfiguration(file);
            fileConfiguration.addDefault("port",80);
            fileConfiguration.addDefault("WebSiteFolder","Website");
            fileConfiguration.options().copyDefaults(true);
            save();
        }
    }




    public static FileConfiguration get() {
        return fileConfiguration;
    }

    public static void save(){
        try {
            fileConfiguration.save(file);
        }catch (IOException e){
            System.out.println("Can not save the config file");
        }

    }

}
