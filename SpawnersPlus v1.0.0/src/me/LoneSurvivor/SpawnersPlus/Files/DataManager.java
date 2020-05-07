package me.LoneSurvivor.SpawnersPlus.Files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.LoneSurvivor.SpawnersPlus.SpawnersPlus;

public class DataManager {

	private SpawnersPlus plugin;
	private FileConfiguration dataconfig = null;
	private File configFile = null;
	
	public DataManager(SpawnersPlus plugin) {
		this.plugin = plugin;
		saveDefaultConfig();
	}
	
	public void reloadConfig() {
		if(this.configFile == null) {
			this.configFile = new File(this.plugin.getDataFolder(), "data.yml");
		}
		this.dataconfig = YamlConfiguration.loadConfiguration(this.configFile);
		
		InputStream defaultStream = this.plugin.getResource("data.yml");
		if(defaultStream != null) {
			YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
			this.dataconfig.setDefaults(defaultConfig);
		}
	}
	
	public FileConfiguration getConfig() {
		if(this.dataconfig == null) {
			reloadConfig();
		}
		return this.dataconfig;
	}
	
	public void saveConfig() {
		if(this.dataconfig==null || this.configFile==null) {
			return;
		}
		try {
			this.getConfig().save(this.configFile);
		} catch (IOException e) {
			this.plugin.getLogger().log(Level.SEVERE, "Could not save config to " + this.configFile, e);
		}
	}
	
	public void saveDefaultConfig() {
		if(configFile==null) {
			configFile = new File(plugin.getDataFolder(), "data.yml");
		}
		if (!configFile.exists()) {
			plugin.saveResource("data.yml", false);
		}
	}
}