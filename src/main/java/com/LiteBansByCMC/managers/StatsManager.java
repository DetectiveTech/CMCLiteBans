package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import litebans.api.Entry;
import litebans.api.Events;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager implements Listener {

	private final BausmePlugin plugin;
	private final boolean useMysql;
	private File statsFile;
	private FileConfiguration statsConfig;

	// Cache: AdminName -> Score (Points)
	private final Map<String, Integer> scoreCache = new ConcurrentHashMap<>();

	public StatsManager(BausmePlugin plugin) {
		this.plugin = plugin;
		this.useMysql = plugin.getConfig().getBoolean("use-mysql-stats", false);

		if (useMysql) {
			initTable();
			// Load scores for online players
			Bukkit.getOnlinePlayers().forEach(p -> loadScore(p.getName()));
		} else {
			initFile();
		}

		// 1. Check immediately on startup
		checkAutoReset();

		// 2. CHECK EVERY HOUR (Automatic 24/7 support)
		// This runs asynchronously, so it will NOT affect TPS.
		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAutoReset, 72000L, 72000L);

		Bukkit.getPluginManager().registerEvents(this, plugin);
		registerLiteBansEvents();
	}

	// --- LITEBANS EVENT LISTENER ---
	private void registerLiteBansEvents() {
		Events.get().register(new Events.Listener() {
			@Override
			public void entryAdded(Entry entry) {
				String executorName = entry.getExecutorName();
				// FIX: Force lowercase to match config keys (ban, mute, etc.)
				String type = (entry.getType() != null) ? entry.getType().toLowerCase() : "unknown";

				// Ignore Console, System, or null
				if (executorName == null || executorName.equalsIgnoreCase("Console") || executorName.equalsIgnoreCase("System")) {
					return;
				}

				// Get points from config
				int points = plugin.getConfig().getInt("aes.points." + type, 0);

				if (points != 0) {
					addPoints(executorName, points);
					plugin.getLogger().info("[CMCLiteBans] Added " + points + " points to " + executorName + " for " + type);
				}
			}
		});
	}

	// --- SETUP ---
	private void initFile() {
		statsFile = new File(plugin.getDataFolder(), "stats.yml");
		if (!statsFile.exists()) {
			try { statsFile.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
		}
		statsConfig = YamlConfiguration.loadConfiguration(statsFile);
	}

	private void save() {
		try { statsConfig.save(statsFile); } catch (IOException e) { e.printStackTrace(); }
	}

	private void initTable() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			String query = "CREATE TABLE IF NOT EXISTS cmc_admin_stats (" +
					"admin_name VARCHAR(64) PRIMARY KEY, " +
					"score INT DEFAULT 0)";
			executeUpdate(query);
		});
	}

	// --- AUTOMATIC WEEKLY RESET & REPORTING ---
	public void checkAutoReset() {
		// NOTE: This method is called asynchronously by the timer/constructor

		// 1. Get Timezone
		String tz = plugin.getConfig().getString("admin-stats.timezone", "Europe/Vilnius");
		ZoneId zone;
		try { zone = ZoneId.of(tz); } catch (Exception e) { zone = ZoneId.of("Europe/Vilnius"); }

		// 2. Calculate "Start of this Week" (Monday 00:00:00)
		ZonedDateTime now = ZonedDateTime.now(zone);
		ZonedDateTime lastMonday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
				.withHour(0).withMinute(0).withSecond(0).withNano(0);

		long mondayMillis = lastMonday.toInstant().toEpochMilli();
		long lastResetMillis = plugin.getConfig().getLong("admin-stats.last-reset", 0);

		// 3. Reset Needed?
		if (lastResetMillis < mondayMillis) {
			plugin.getLogger().info("--- WEEKLY RESET DETECTED ---");

			// 4. APPEND TO REPORT.TXT BEFORE WIPE
			saveWeeklyReport();

			// 5. WIPE DATA
			if (useMysql) {
				executeUpdate("TRUNCATE TABLE cmc_admin_stats");
				scoreCache.clear();
				// Reset cache for online players to 0
				Bukkit.getOnlinePlayers().forEach(p -> scoreCache.put(p.getName(), 0));
			} else {
				if (statsFile.exists()) statsFile.delete();
				initFile();
			}

			// 6. UPDATE CONFIG
			plugin.getConfig().set("admin-stats.last-reset", System.currentTimeMillis());
			plugin.saveConfig();
			plugin.getLogger().info("--- ADMIN STATS RESET COMPLETE ---");
		}
	}

	private void saveWeeklyReport() {
		plugin.getLogger().info("Appending to weekly report...");

		// Single file: report.txt
		File reportFile = new File(plugin.getDataFolder(), "report.txt");

		// The 'true' parameter in FileWriter enables APPEND mode
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile, true))) {

			String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

			writer.newLine();
			writer.write("==================================================");
			writer.newLine();
			writer.write(" WEEKLY REPORT: " + dateStr);
			writer.newLine();
			writer.write("==================================================");
			writer.newLine();

			// Fetch Data
			Map<String, Integer> finalScores = new HashMap<>();

			if (useMysql) {
				try (PreparedStatement st = litebans.api.Database.get().prepareStatement("SELECT * FROM cmc_admin_stats ORDER BY score DESC")) {
					try (ResultSet rs = st.executeQuery()) {
						while (rs.next()) {
							finalScores.put(rs.getString("admin_name"), rs.getInt("score"));
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
					writer.write("ERROR FETCHING MYSQL DATA: " + e.getMessage());
					writer.newLine();
				}
			} else {
				// Flat file reading
				for (String key : statsConfig.getConfigurationSection("stats").getKeys(false)) {
					finalScores.put(key, statsConfig.getInt("stats." + key + ".score"));
				}
			}

			// Write Data
			if (finalScores.isEmpty()) {
				writer.write(" No activity recorded this week.");
				writer.newLine();
			} else {
				for (Map.Entry<String, Integer> entry : finalScores.entrySet()) {
					writer.write(String.format(" %-20s | %d pts", entry.getKey(), entry.getValue()));
					writer.newLine();
				}
			}

			writer.write("--------------------------------------------------");
			writer.newLine();

			plugin.getLogger().info("Stats appended to report.txt");

		} catch (IOException e) {
			plugin.getLogger().severe("Could not write to report.txt!");
			e.printStackTrace();
		}
	}

	// --- EVENTS ---
	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		if (useMysql) loadScore(e.getPlayer().getName());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		if (useMysql) scoreCache.remove(e.getPlayer().getName());
	}

	// --- MANUAL COMMAND HANDLER (ONLY FOR UNBANS/REVOKES) ---
	// LiteBans API handles Bans/Mutes, but Unbans are tricky in API,
	// so we keep CommandPreprocess just for PENALTIES.
	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent e) {
		String msg = e.getMessage().toLowerCase();
		String[] args = msg.split(" ");
		if (args.length < 2) return;
		String cmd = args[0].replace("/", "");

		if (cmd.equals("unban") || cmd.equals("unmute")) {
			if (e.getPlayer().hasPermission("litebans.unban")) {
				int penalty = plugin.getConfig().getInt("aes.revoked-penalty", -50);
				addPoints(e.getPlayer().getName(), penalty);
				plugin.getLogger().info("[CMCLiteBans] Penalty: " + penalty + " pts to " + e.getPlayer().getName() + " for revoking.");
			}
		}
	}

	public void addPoints(String admin, int amount) {
		if (useMysql) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				String query = "INSERT INTO cmc_admin_stats (admin_name, score) VALUES (?, ?) " +
						"ON DUPLICATE KEY UPDATE score = score + ?";
				try (PreparedStatement st = litebans.api.Database.get().prepareStatement(query)) {
					st.setString(1, admin);
					st.setInt(2, amount); // Initial
					st.setInt(3, amount); // Update
					st.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
				}
				loadScore(admin); // Refresh cache
			});
		} else {
			int current = statsConfig.getInt("stats." + admin + ".score", 0);
			statsConfig.set("stats." + admin + ".score", current + amount);
			save();
		}
	}

	private void loadScore(String admin) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			String query = "SELECT score FROM cmc_admin_stats WHERE admin_name=?";
			try (PreparedStatement st = litebans.api.Database.get().prepareStatement(query)) {
				st.setString(1, admin);
				try (ResultSet rs = st.executeQuery()) {
					if (rs.next()) {
						scoreCache.put(admin, rs.getInt("score"));
					} else {
						scoreCache.put(admin, 0);
					}
				}
			} catch (SQLException e) {
				// Ignore errors (usually if table not created yet)
			}
		});
	}

	public int getScore(String admin) {
		if (useMysql) {
			return scoreCache.getOrDefault(admin, 0);
		} else {
			return statsConfig.getInt("stats." + admin + ".score", 0);
		}
	}

	private void executeUpdate(String query) {
		try (PreparedStatement st = litebans.api.Database.get().prepareStatement(query)) {
			st.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}