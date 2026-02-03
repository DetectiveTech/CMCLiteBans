package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import com.LiteBansByCMC.utils.TimeUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import litebans.api.Database;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DatabaseManager {

	private final BausmePlugin plugin;
	private HikariDataSource dataSource;
	private final boolean useCustomDb;

	public DatabaseManager(BausmePlugin plugin) {
		this.plugin = plugin;
		this.useCustomDb = plugin.getConfig().getBoolean("use-custom-database", false);

		if (useCustomDb) {
			setupCustomDatabase();
		}
	}

	private void setupCustomDatabase() {
		String host = plugin.getConfig().getString("database.host");
		String port = plugin.getConfig().getString("database.port");
		String database = plugin.getConfig().getString("database.database_name");
		String username = plugin.getConfig().getString("database.user");
		String password = plugin.getConfig().getString("database.password");

		HikariConfig config = new HikariConfig();
		String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";

		config.setJdbcUrl(jdbcUrl);
		config.setUsername(username);
		config.setPassword(password);
		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
		config.setMaximumPoolSize(10);
		config.setPoolName("CMCLiteBansPool");

		try {
			this.dataSource = new HikariDataSource(config);
			plugin.getLogger().info("Connected to Custom MySQL/MariaDB successfully.");
		} catch (Exception e) {
			plugin.getLogger().severe("Could not connect to database! " + e.getMessage());
		}
	}

	public void close() {
		if (dataSource != null && !dataSource.isClosed()) {
			dataSource.close();
		}
	}

	@FunctionalInterface
	private interface ResultHandler {
		void handle(ResultSet rs) throws SQLException;
	}

	private void executeQuery(String query, ResultHandler handler, Object... params) {
		if (useCustomDb && dataSource != null) {
			try (Connection conn = dataSource.getConnection();
				 PreparedStatement st = conn.prepareStatement(query)) {
				setParams(st, params);
				try (ResultSet rs = st.executeQuery()) {
					handler.handle(rs);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			try (PreparedStatement st = Database.get().prepareStatement(query)) {
				setParams(st, params);
				try (ResultSet rs = st.executeQuery()) {
					handler.handle(rs);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private void setParams(PreparedStatement st, Object... params) throws SQLException {
		for (int i = 0; i < params.length; i++) {
			st.setObject(i + 1, params[i]);
		}
	}

	private String getTable(String table) {
		String prefix = plugin.getConfig().getString("database.table_prefix", "litebans_");
		return prefix + table.replace("{", "").replace("}", "");
	}

	// --- NEW METHOD: RESET HISTORY (FIXED) ---
	public void resetHistory(CommandSender sender, String targetName, String type) {
		try {
			// 1. Get UUID and IP
			String uuid = null;
			AtomicReference<String> uuidRef = new AtomicReference<>(null);
			AtomicReference<String> ipRef = new AtomicReference<>(null);

			String historyTable = getTable("history");
			executeQuery("SELECT uuid, ip FROM " + historyTable + " WHERE name=? ORDER BY date DESC LIMIT 1", rs -> {
				if (rs.next()) {
					uuidRef.set(rs.getString("uuid"));
					ipRef.set(rs.getString("ip"));
				}
			}, targetName);

			uuid = uuidRef.get();
			String ip = ipRef.get();

			if (uuid == null) {
				sender.sendMessage(ChatColor.RED + "Žaidėjas " + targetName + " nerastas istorijoje.");
				return;
			}

			// 2. Determine tags
			List<String> tagsToReset = new ArrayList<>();

			if (type.equals("all")) {
				tagsToReset.add("#");
			} else if (type.equals("chat")) {
				for(int i=100; i<200; i++) tagsToReset.add("#" + i);
			} else if (type.equals("gameplay") || type.equals("warps")) {
				for(int i=200; i<300; i++) tagsToReset.add("#" + i);
			} else if (type.equals("cheat") || type.equals("cheats")) {
				for(int i=300; i<400; i++) tagsToReset.add("#" + i);
			} else {
				sender.sendMessage(ChatColor.RED + "Nežinomas tipas. Naudokite: chat, gameplay, cheat, all");
				return;
			}

			// 3. Execute Updates (FIXED LOGIC)
			int totalChanges = 0;
			String[] tables = {"bans", "mutes", "warnings"};

			for (String rawTable : tables) {
				String tableName = getTable(rawTable);

				if (type.equals("all")) {
					String query = "UPDATE " + tableName + " SET reason = REPLACE(reason, '#', '[RESET]#') " +
							"WHERE (uuid=? OR ip=?) AND reason LIKE '%#%' AND reason NOT LIKE '%[RESET]%'";

					// PATAISYMAS: Naudojame Database.get().prepareStatement() tiesiogiai
					try (PreparedStatement st = (useCustomDb && dataSource != null)
							? dataSource.getConnection().prepareStatement(query)
							: Database.get().prepareStatement(query)) {

						st.setString(1, uuid);
						st.setString(2, (ip != null ? ip : uuid));
						totalChanges += st.executeUpdate();
					}
				} else {
					for (String tag : tagsToReset) {
						String query = "UPDATE " + tableName + " SET reason = REPLACE(reason, ?, ?) " +
								"WHERE (uuid=? OR ip=?) AND reason LIKE ?";

						// PATAISYMAS: Naudojame Database.get().prepareStatement() tiesiogiai
						try (PreparedStatement st = (useCustomDb && dataSource != null)
								? dataSource.getConnection().prepareStatement(query)
								: Database.get().prepareStatement(query)) {

							st.setString(1, tag);
							st.setString(2, "[RESET-" + tag + "]");
							st.setString(3, uuid);
							st.setString(4, (ip != null ? ip : uuid));
							st.setString(5, "%" + tag + "%");

							totalChanges += st.executeUpdate();
						}
					}
				}
			}

			sender.sendMessage(ChatColor.GREEN + "Sėkmingai anuliuota " + totalChanges + " įrašų žaidėjui " + targetName + " (ir jo IP).");
			plugin.getLogger().info("Admin " + sender.getName() + " reset history for " + targetName + " (Type: " + type + ")");

		} catch (SQLException e) {
			e.printStackTrace();
			sender.sendMessage(ChatColor.RED + "DB Klaida: " + e.getMessage());
		}
	}

	// --- MAIN LOGIC (UNCHANGED) ---

	public void checkAlts(Player admin, String targetName, String targetUUID) {
		if (!plugin.getConfig().getBoolean("alts.check-on-select", true)) return;

		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
			String historyTable = getTable("history");
			String query = "SELECT COUNT(DISTINCT uuid) as alt_count FROM " + historyTable + " WHERE ip = " +
					"(SELECT ip FROM " + historyTable + " WHERE uuid = ? ORDER BY date DESC LIMIT 1)";

			executeQuery(query, rs -> {
				if (rs.next()) {
					int count = rs.getInt("alt_count");
					int threshold = plugin.getConfig().getInt("alts.warning-threshold", 1);
					if (count > threshold) {
						admin.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "[ALTS] " +
								ChatColor.YELLOW + targetName + " has " + ChatColor.RED + count +
								ChatColor.YELLOW + " accounts on this IP!");
					}
				}
			}, targetUUID);
		});
	}

	public void printHistory(Player admin, String targetUUID, String targetName, String actionKey) {
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				boolean isSpecificRule = plugin.getConfig().contains("escalation." + actionKey);
				if (isSpecificRule) {
					handleEscalation(admin, targetUUID, targetName, actionKey);
				} else {
					handleGeneralHistory(admin, targetUUID, targetName, actionKey);
				}
			} catch (Exception e) {
				e.printStackTrace();
				admin.sendMessage(ChatColor.RED + "DB Error: " + e.getMessage());
			}
		});
	}

	private void handleEscalation(Player admin, String uuid, String name, String actionKey) throws Exception {
		String basePath = "escalation." + actionKey;
		String reasonFilter = plugin.getConfig().getString(basePath + ".reason-filter", null);

		AtomicReference<String> targetIP = new AtomicReference<>(null);
		String historyTable = getTable("history");
		executeQuery("SELECT ip FROM " + historyTable + " WHERE uuid=? ORDER BY date DESC LIMIT 1", rs -> {
			if (rs.next()) targetIP.set(rs.getString("ip"));
		}, uuid);
		String ip = targetIP.get();

		java.util.concurrent.atomic.AtomicBoolean hasRecentBan = new java.util.concurrent.atomic.AtomicBoolean(false);
		java.util.concurrent.atomic.AtomicLong banTime = new java.util.concurrent.atomic.AtomicLong(0);

		if (reasonFilter != null) {
			String bansTable = getTable("bans");
			String query = "SELECT time FROM " + bansTable + " WHERE (uuid=? OR ip=?) AND reason LIKE ? ORDER BY time DESC LIMIT 1";
			executeQuery(query, rs -> {
				if (rs.next()) {
					hasRecentBan.set(true);
					banTime.set(rs.getLong("time"));
				}
			}, uuid, (ip != null ? ip : uuid), "%" + reasonFilter + "%");
		}

		long minTime;
		ZoneId zone = ZoneId.of("Europe/Vilnius");
		ZonedDateTime now = ZonedDateTime.now(zone);
		long startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).toInstant().toEpochMilli();

		if (hasRecentBan.get() && banTime.get() >= startOfMonth) {
			minTime = startOfMonth;
		} else {
			String historyPeriod = plugin.getConfig().getString("history-filter.bans", "30d");
			minTime = System.currentTimeMillis() - TimeUtils.parseTime(historyPeriod);
		}

		List<HistoryManager.HistoryEntry> entries = new ArrayList<>();

		if (reasonFilter != null) {
			String filter = "%" + reasonFilter + "%";
			fetchEntries(entries, "mutes", "mute", uuid, ip, minTime, filter);
			fetchEntries(entries, "bans", "ban", uuid, ip, minTime, filter);
			fetchEntries(entries, "warnings", "warn", uuid, ip, minTime, filter);
		}

		entries.sort((a, b) -> Long.compare(b.time, a.time));

		int realLevel = entries.size();
		int adminLimit = getMaxPermissionLimit(admin, actionKey);

		int actualLevel = realLevel;
		if (adminLimit != -1 && actualLevel > adminLimit) {
			actualLevel = adminLimit;
		}

		String suggestedCmd = calculateSmartPunishment(admin, name, actionKey, realLevel);

		int finalActualLevel = actualLevel;
		int finalRealLevel = realLevel;

		plugin.getServer().getScheduler().runTask(plugin, () -> {
			plugin.getHistoryManager().openHistory(admin, name, entries, suggestedCmd, actionKey, finalActualLevel, finalRealLevel);
		});
	}

	private void fetchEntries(List<HistoryManager.HistoryEntry> entries, String rawTableName, String type, String uuid, String ip, long minTime, String filter) {
		String table = getTable(rawTableName);
		String query = "SELECT time, until, reason, banned_by_name FROM " + table + " WHERE (uuid=? OR ip=?) AND time > ? AND reason LIKE ?";

		executeQuery(query, rs -> {
			while (rs.next()) {
				long time = rs.getLong("time");
				long until = rs.getLong("until");
				String durationStr = "Unknown";

				if (type.equals("warn")) {
					durationStr = "Įspėjimas";
				} else if (until <= 0) {
					durationStr = "Permanent";
				} else {
					long diff = until - time;
					long days = diff / 86400000L;
					long hours = (diff % 86400000L) / 3600000L;
					long minutes = (diff % 3600000L) / 60000L;

					if (days > 0) durationStr = days + "d";
					else if (hours > 0) durationStr = hours + "h";
					else durationStr = minutes + "m";
				}

				entries.add(new HistoryManager.HistoryEntry(
						type,
						rs.getString("reason"),
						rs.getString("banned_by_name"),
						time,
						durationStr
				));
			}
		}, uuid, (ip != null ? ip : uuid), minTime, filter);
	}

	private String calculateSmartPunishment(Player admin, String name, String actionKey, int targetLevel) {
		int adminLimit = getMaxPermissionLimit(admin, actionKey);

		if (adminLimit < 0) {
			return "ERROR: Neturite teisės bausti (Limitas: -1)";
		}

		if (targetLevel > adminLimit) {
			admin.sendMessage(ChatColor.YELLOW + "⚠ Žaidėjas (su Alt) nusipelnė " + ChatColor.RED + targetLevel +
					ChatColor.YELLOW + " lygio, bet jūsų limitas yra " + ChatColor.GREEN + adminLimit + ".");
			targetLevel = adminLimit;
		}

		String cmd = plugin.getConfig().getString("escalation." + actionKey + ".levels." + targetLevel);

		if (cmd == null) {
			for(int i = targetLevel; i >= 0; i--) {
				cmd = plugin.getConfig().getString("escalation." + actionKey + ".levels." + i);
				if(cmd != null) break;
			}
		}

		if (cmd != null) return cmd.replace("{player}", name);

		return "ERROR: Konfigūracijos klaida (Nėra komandos)";
	}

	private int getMaxPermissionLimit(Player admin, String actionKey) {
		if (admin.isOp() || admin.hasPermission("*") || admin.hasPermission("bausme.admin")) {
			return 999;
		}

		String path = "escalation." + actionKey + ".permission-limits";
		ConfigurationSection limitsSection = plugin.getConfig().getConfigurationSection(path);

		if (limitsSection == null) return 999;

		int allowedMax = -1;
		boolean hasAnyPerm = false;

		Map<String, Object> values = limitsSection.getValues(false);

		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String configKey = entry.getKey();
			String realPermission = configKey.replace("-", ".");

			int val = 0;
			if (entry.getValue() instanceof Integer) {
				val = (Integer) entry.getValue();
			} else {
				continue;
			}

			if (admin.hasPermission(realPermission)) {
				hasAnyPerm = true;
				if (val > allowedMax) allowedMax = val;
			}
		}

		if (!hasAnyPerm) return -1;
		return allowedMax;
	}

	private void handleGeneralHistory(Player admin, String uuid, String name, String type) throws Exception {
		String periodStr = plugin.getConfig().getString("history-filter.mutes", "30d");
		long minTime = System.currentTimeMillis() - TimeUtils.parseTime(periodStr);

		admin.sendMessage(ChatColor.GOLD + "=== BENDRA ISTORIJA (" + periodStr + ") ===");

		String table = getTable("mutes");
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
			String query = "SELECT * FROM " + table + " WHERE uuid=? AND time > ? ORDER BY time DESC LIMIT 10";
			executeQuery(query, rs -> {
				boolean found = false;
				while (rs.next()) {
					found = true;
					String reason = rs.getString("reason");
					String by = rs.getString("banned_by_name");
					java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
					String date = sdf.format(new java.util.Date(rs.getLong("time")));
					admin.sendMessage(ChatColor.RED + "[" + date + "] " + reason + ChatColor.GRAY + " (" + by + ")");
				}
				if (!found) admin.sendMessage(ChatColor.GRAY + "Istorija tuščia.");
			}, uuid, minTime);
		});
	}

	public void executeSmartPunishmentConsole(String playerName, String uuid, String actionKey) {
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				String basePath = "escalation." + actionKey;
				String reasonFilter = plugin.getConfig().getString(basePath + ".reason-filter", null);

				AtomicReference<String> targetIP = new AtomicReference<>(null);
				String historyTable = getTable("history");
				executeQuery("SELECT ip FROM " + historyTable + " WHERE uuid=? ORDER BY date DESC LIMIT 1", rs -> {
					if (rs.next()) targetIP.set(rs.getString("ip"));
				}, uuid);
				String ip = targetIP.get();

				java.util.concurrent.atomic.AtomicBoolean hasRecentBan = new java.util.concurrent.atomic.AtomicBoolean(false);
				java.util.concurrent.atomic.AtomicLong banTime = new java.util.concurrent.atomic.AtomicLong(0);

				if (reasonFilter != null) {
					String table = getTable("bans");
					String query = "SELECT time FROM " + table + " WHERE (uuid=? OR ip=?) AND reason LIKE ? ORDER BY time DESC LIMIT 1";

					executeQuery(query, rs -> {
						if (rs.next()) {
							hasRecentBan.set(true);
							banTime.set(rs.getLong("time"));
						}
					}, uuid, (ip != null ? ip : uuid), "%" + reasonFilter + "%");
				}

				long minTime;
				ZoneId zone = ZoneId.of("Europe/Vilnius");
				ZonedDateTime now = ZonedDateTime.now(zone);
				long startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).toInstant().toEpochMilli();

				if (hasRecentBan.get() && banTime.get() >= startOfMonth) {
					minTime = startOfMonth;
				} else {
					String historyPeriod = plugin.getConfig().getString("history-filter.bans", "30d");
					minTime = System.currentTimeMillis() - TimeUtils.parseTime(historyPeriod);
				}

				java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
				if (reasonFilter != null) {
					String filter = "%" + reasonFilter + "%";
					count.addAndGet(countEntries("mutes", uuid, ip, minTime, filter));
					count.addAndGet(countEntries("bans", uuid, ip, minTime, filter));
					count.addAndGet(countEntries("warnings", uuid, ip, minTime, filter));
				}

				int targetLevel = count.get();
				String cmd = plugin.getConfig().getString("escalation." + actionKey + ".levels." + targetLevel);

				if (cmd == null) {
					for(int i = targetLevel; i >= 0; i--) {
						cmd = plugin.getConfig().getString("escalation." + actionKey + ".levels." + i);
						if(cmd != null) break;
					}
				}

				if (cmd != null) {
					String finalCmd = cmd.replace("{player}", playerName);
					plugin.getServer().getScheduler().runTask(plugin, () -> {
						plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), finalCmd);
						plugin.getLogger().info("Patikra Auto-Ban: " + playerName + " (Lygis: " + targetLevel + ")");

						plugin.getLocalLogManager().logPunishment("Console (Patikra)", playerName, finalCmd, "Atsijungimas per patikrą");
						plugin.getDiscordManager().sendLog("Console (Patikra)", playerName, finalCmd, "Atsijungimas per patikrą");
					});
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private int countEntries(String rawTableName, String uuid, String ip, long minTime, String filter) {
		String table = getTable(rawTableName);
		String query = "SELECT COUNT(*) as total FROM " + table + " WHERE (uuid=? OR ip=?) AND time > ? AND reason LIKE ?";

		java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger(0);
		executeQuery(query, rs -> {
			if (rs.next()) total.set(rs.getInt("total"));
		}, uuid, (ip != null ? ip : uuid), minTime, filter);

		return total.get();
	}
}