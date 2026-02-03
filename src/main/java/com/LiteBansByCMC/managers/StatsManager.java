package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public class StatsManager implements Listener {

	private final BausmePlugin plugin;
	private final boolean useMysql;
	private File statsFile;
	private FileConfiguration statsConfig;

	public StatsManager(BausmePlugin plugin) {
		this.plugin = plugin;
		this.useMysql = plugin.getConfig().getBoolean("use-mysql-stats", false);

		if (useMysql) {
			initTable();
		} else {
			initFile();
			checkAutoReset();
		}
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	private void initFile() {
		statsFile = new File(plugin.getDataFolder(), "stats.yml");
		if (!statsFile.exists()) {
			try { statsFile.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
		}
		statsConfig = YamlConfiguration.loadConfiguration(statsFile);
	}

	private void save() {
		try {
			statsConfig.save(statsFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// --- PATAISYMAS: Naudojame saugų SQL vykdymą ---
	private void initTable() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			// Tikriname, ar naudojame Custom DB. Jei ne (LiteBans API),
			// dažniausiai negalime kurti lentelių per jų API, todėl praleidžiame.
			if (!plugin.getConfig().getBoolean("use-custom-database", false)) return;

			String query = "CREATE TABLE IF NOT EXISTS cmc_admin_stats (" +
					"admin_name VARCHAR(64) PRIMARY KEY, " +
					"bans INT DEFAULT 0, mutes INT DEFAULT 0, warns INT DEFAULT 0, kicks INT DEFAULT 0, " +
					"total INT DEFAULT 0, score INT DEFAULT 0)";

			executeUpdate(query);
		});
	}

	// --- Helper metodas SQL atnaujinimams (INSERT/UPDATE/CREATE) ---
	private void executeUpdate(String query, Object... params) {
		try {
			if (!plugin.getConfig().getBoolean("use-custom-database", false)) {
				// LiteBans API
				// PASTABA: LiteBans API metodai meta tik SQLException
				try (PreparedStatement st = litebans.api.Database.get().prepareStatement(query)) {
					for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
					st.executeUpdate();
				}
			} else {
				// Custom DB logika (kol kas tik įspėjimas, kol nesutvarkėme DatabaseManager viešo priėjimo)
				plugin.getLogger().warning("MySQL Stats su Custom DB šiuo metu neaktyvuota (reikia getDbManager().getConnection()).");
			}
		} catch (SQLException e) { // <--- PATAISYTA: Paliktas tik SQLException
			e.printStackTrace();
		}
	}

	public void checkAutoReset() {
		if (useMysql) return;

		String tz = plugin.getConfig().getString("admin-stats.timezone", "Europe/Vilnius");
		ZoneId zone = ZoneId.of(tz);

		long lastReset = plugin.getConfig().getLong("admin-stats.last-reset", 0);
		ZonedDateTime now = ZonedDateTime.now(zone);
		ZonedDateTime lastMonday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
				.withHour(0).withMinute(0).withSecond(0).withNano(0);

		long mondayMillis = lastMonday.toInstant().toEpochMilli();

		if (lastReset < mondayMillis) {
			plugin.getLogger().info("It is a new week (Monday)! Resetting Admin Stats...");
			statsFile.delete();
			initFile();
			plugin.getConfig().set("admin-stats.last-reset", System.currentTimeMillis());
			plugin.saveConfig();
		}
	}

	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent e) {
		String msg = e.getMessage().toLowerCase();
		String[] args = msg.split(" ");
		if (args.length < 1) return;
		String cmd = args[0].replace("/", "");

		String type = null;
		if (cmd.contains("ban") && !cmd.contains("unban")) type = "ban";
		else if (cmd.contains("mute") && !cmd.contains("unmute")) type = "mute";
		else if (cmd.equals("warn")) type = "warn";
		else if (cmd.equals("kick")) type = "kick";

		if (cmd.contains("unban")) {
			applyPenalty(e.getPlayer().getName());
			return;
		}

		if (type != null && e.getPlayer().hasPermission("litebans." + type)) {
			increment(e.getPlayer().getName(), type);
		}
	}

	private void applyPenalty(String admin) {
		int penalty = plugin.getConfig().getInt("aes.revoked-penalty", -50);
		updateScore(admin, penalty);
	}

	private void updateScore(String admin, int amount) {
		if (useMysql) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				String query = "UPDATE cmc_admin_stats SET score = score + ? WHERE admin_name = ?";
				executeUpdate(query, amount, admin);
			});
		}
	}

	public void increment(String admin, String type) {
		int points = plugin.getConfig().getInt("aes.points." + type, 1);

		if (useMysql) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				// Pluralize type (ban -> bans)
				String col = type + "s";
				String query = "INSERT INTO cmc_admin_stats (admin_name, " + col + ", total, score) VALUES (?, 1, 1, ?) " +
						"ON DUPLICATE KEY UPDATE " + col + " = " + col + " + 1, total = total + 1, score = score + ?";
				executeUpdate(query, admin, points, points);
			});
		} else {
			String path = "stats." + admin + ".";
			statsConfig.set(path + type, getStat(admin, type) + 1);
			statsConfig.set(path + "total", getStat(admin, "total") + 1);
			save();
		}
	}

	public int getStat(String admin, String type) {
		if (useMysql) {
			return 0; // Async placeholder - čia reikėtų atskiro metodo gauti duomenis
		} else {
			return statsConfig.getInt("stats." + admin + "." + type, 0);
		}
	}
}