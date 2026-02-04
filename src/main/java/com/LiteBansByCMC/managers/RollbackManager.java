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
		// SVARBU: Tikriname ar CoreProtect yra serveryje PRIEŠ kviesdami metodą,
		// kuriame naudojama CoreProtect klasė. Tai apsaugo nuo NoClassDefFoundError.
		if (Bukkit.getPluginManager().getPlugin("CoreProtect") != null) {
			try {
				hookCoreProtect();
			} catch (NoClassDefFoundError | Exception e) {
				plugin.getLogger().warning("Nepavyko prisijungti prie CoreProtect API: " + e.getMessage());
			}
		}
	}

	private void hookCoreProtect() {
		Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
		// Dvigubas patikrinimas ir 'instanceof' naudojimas
		if (cp != null && cp.isEnabled() && cp instanceof CoreProtect) {
			this.cpApi = ((CoreProtect) cp).getAPI();
			plugin.getLogger().info("Sėkmingai prisijungta prie CoreProtect!");
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
			try {
				cpApi.performRollback(86400, users, null, null, null, action, 0, null);
			} catch (Exception e) {
				plugin.getLogger().warning("Klaida atliekant Rollback: " + e.getMessage());
			}
		}
	}
}