package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LocalLogManager {

	private final BausmePlugin plugin;
	private final File logFile;
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public LocalLogManager(BausmePlugin plugin) {
		this.plugin = plugin;
		this.logFile = new File(plugin.getDataFolder(), "logs.txt");
		createFileIfNotExists();
	}

	private void createFileIfNotExists() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdirs();
		}
		if (!logFile.exists()) {
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				plugin.getLogger().severe("Could not create logs.txt!");
				e.printStackTrace();
			}
		}
	}

	public void logPunishment(String admin, String target, String command, String reason) {
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
				String timestamp = dateFormat.format(new Date());
				String entry = String.format("[%s] ADMIN: %s | TARGET: %s | CMD: %s | REASON: %s",
						timestamp, admin, target, command, reason);

				writer.write(entry);
				writer.newLine();
			} catch (IOException e) {
				plugin.getLogger().warning("Failed to write to local log file: " + e.getMessage());
			}
		});
	}
}