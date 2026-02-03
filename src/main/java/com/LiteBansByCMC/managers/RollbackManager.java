package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

public class RollbackManager {

	private final BausmePlugin plugin;
	private CoreProtectAPI cpApi;

	public RollbackManager(BausmePlugin plugin) {
		this.plugin = plugin;
		hookCoreProtect();
	}

	private void hookCoreProtect() {
		Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
		if (cp instanceof CoreProtect) {
			this.cpApi = ((CoreProtect) cp).getAPI();
		}
	}

	public void checkAndRollback(String command, String targetName) {
		if (cpApi == null) return;

		// Only rollback on bans, and if reason contains "grief" or "destroy"
		if (!command.contains("ban")) return;

		String lowerCmd = command.toLowerCase();
		// Check keywords (could be in config)
		if (lowerCmd.contains("grief") || lowerCmd.contains("griov")) {

			// Perform Rollback: Radius 20, Time 24h
			plugin.getLogger().info("Triggering CoreProtect rollback for " + targetName);

			// CoreProtect API uses lists for parameters
			List<String> users = Arrays.asList(targetName);
			List<Integer> action = Arrays.asList(0, 1); // 0=Break, 1=Place

			// (user, time, radius, action_list, block_list, exclude_list, table, location)
			// Time: seconds. 86400 = 24h
			cpApi.performRollback(86400, users, null, null, null, action, 0, null);
		}
	}
}