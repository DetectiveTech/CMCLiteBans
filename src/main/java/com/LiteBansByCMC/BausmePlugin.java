package com.LiteBansByCMC;

import com.LiteBansByCMC.managers.*;
import litebans.api.Entry;
import litebans.api.Events;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.*;

public class BausmePlugin extends JavaPlugin implements Listener {

	private StatsManager statsManager;
	private DatabaseManager dbManager;
	private GuiManager guiManager;
	private DiscordManager discordManager;
	private HistoryManager historyManager;
	private RollbackManager rollbackManager;
	private LocalLogManager localLogManager;
	private PatikraManager patikraManager;

	private final Map<UUID, String> selectedTargets = new HashMap<>();
	private final Map<UUID, String> currentMenus = new HashMap<>();
	private final Set<UUID> chatInput = new HashSet<>();
	private final Map<UUID, Long> cooldowns = new HashMap<>();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadManagers();
		Bukkit.getPluginManager().registerEvents(this, this);

		// --- COMMAND REGISTRATION ---

		// 1. /bausme
		if (getCommand("bausme") != null) {
			getCommand("bausme").setExecutor(this);
			getCommand("bausme").setTabCompleter(this);
		}

		// 2. /patikra
		if (getCommand("patikra") != null) {
			// We set the executor to THIS class, and handle it in onCommand below
			getCommand("patikra").setExecutor(this);

			// Define Tab Completer for /patikra specifically
			getCommand("patikra").setTabCompleter((sender, command, alias, args) -> {
				List<String> completions = new ArrayList<>();

				// Arg 1: Player Name
				if (args.length == 1) {
					for (Player p : Bukkit.getOnlinePlayers()) {
						completions.add(p.getName());
					}
					return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
				}

				// Arg 2: "stop" OR Discord ID list
				if (args.length == 2) {
					completions.add("stop");
					List<String> discords = getConfig().getStringList("patikra_discords");
					if (discords != null) completions.addAll(discords);
					return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
				}

				return Collections.emptyList();
			});
		}

		// --- LITEBANS EVENTS ---
		try {
			Events.get().register(new Events.Listener() {
				@Override
				public void entryAdded(Entry entry) {
					String executorName = entry.getExecutorName();
					if (executorName == null || executorName.equals("Console")) return;

					Player admin = Bukkit.getPlayer(executorName);
					if (admin != null) {
						setCooldown(admin);
					}
				}
			});
		} catch (NoClassDefFoundError | Exception e) {
			getLogger().warning("LiteBans not found or API error. Cooldowns might not sync automatically.");
		}

		getLogger().info("CMCLiteBans enabled successfully!");
	}

	@Override
	public void onDisable() {
		if (dbManager != null) {
			dbManager.close();
		}
		getLogger().info("CMCLiteBans disabled!");
	}

	private void loadManagers() {
		this.dbManager = new DatabaseManager(this);
		this.discordManager = new DiscordManager(this);
		this.localLogManager = new LocalLogManager(this);
		this.statsManager = new StatsManager(this);
		this.historyManager = new HistoryManager(this);
		this.rollbackManager = new RollbackManager(this);
		this.guiManager = new GuiManager(this);
		this.patikraManager = new PatikraManager(this);
	}

	// --- TAB COMPLETION FOR /BAUSME ---
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (command.getName().equalsIgnoreCase("bausme")) {
			List<String> completions = new ArrayList<>();

			// Argument 1: [reload, nuimti, <player>]
			if (args.length == 1) {
				if (sender.hasPermission("bausme.admin")) {
					completions.add("reload");
					completions.add("nuimti");
				}
				// Also suggest players for the GUI opening
				for (Player p : Bukkit.getOnlinePlayers()) {
					completions.add(p.getName());
				}
				return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
			}

			// Argument 2: If Arg 1 is "nuimti" -> suggest [chat, cheat, gameplay, all]
			if (args.length == 2 && args[0].equalsIgnoreCase("nuimti")) {
				if (sender.hasPermission("bausme.admin")) {
					List<String> types = Arrays.asList("chat", "cheat", "gameplay", "all");
					return StringUtil.copyPartialMatches(args[1], types, new ArrayList<>());
				}
			}

			// Argument 3: If Arg 1 is "nuimti" -> suggest <player> again
			if (args.length == 3 && args[0].equalsIgnoreCase("nuimti")) {
				return null; // Return null tells Bukkit to suggest online players automatically
			}
		}
		return Collections.emptyList();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

		// --- PATIKRA COMMAND ---
		if (command.getName().equalsIgnoreCase("patikra")) {
			if (patikraManager != null) {
				patikraManager.handleCommand(sender, args);
			} else {
				sender.sendMessage(ChatColor.RED + "Error: PatikraManager not loaded.");
			}
			return true;
		}

		// --- COMMAND: /BAUSME NUIMTI ---
		if (args.length >= 3 && args[0].equalsIgnoreCase("nuimti")) {
			if (!sender.hasPermission("bausme.admin")) {
				sender.sendMessage(ChatColor.RED + "Neturite teisės (bausme.admin).");
				return true;
			}

			String type = args[1].toLowerCase();
			String target = args[2];

			Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
				dbManager.resetHistory(sender, target, type);
			});
			return true;
		}

		// --- COMMAND: /BAUSME RELOAD ---
		if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
			if (!sender.hasPermission("bausme.admin")) {
				sender.sendMessage(ChatColor.RED + "Neturite teisės (bausme.admin).");
				return true;
			}
			if (dbManager != null) dbManager.close();

			reloadConfig();
			loadManagers();
			sender.sendMessage(ChatColor.GREEN + "[CMCLiteBans] Konfigūracija perkrauta sėkmingai!");
			return true;
		}

		// --- DEFAULT: OPEN GUI ---
		if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "GUI can only be opened by players.");
			return true;
		}
		Player p = (Player) sender;

		if (!p.hasPermission("bausme.use") && !p.hasPermission("bausme.admin")) {
			p.sendMessage(ChatColor.RED + "Neturite teisės naudoti šią komandą.");
			return true;
		}

		if (args.length > 0) {
			String targetName = args[0];
			selectedTargets.put(p.getUniqueId(), targetName);
			p.sendMessage(ChatColor.GREEN + "Taikinys nustatytas: " + ChatColor.YELLOW + targetName);
			guiManager.openMainMenu(p);
			return true;
		}

		// No args -> Open menu for "No Target"
		selectedTargets.remove(p.getUniqueId());
		guiManager.openMainMenu(p);
		return true;
	}

	// ... (Events and Resolve Logic same as before) ...
	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncPlayerChatEvent e) {
		UUID uuid = e.getPlayer().getUniqueId();

		if (guiManager.isAwaitingEvidence(uuid)) {
			e.setCancelled(true);
			String link = e.getMessage();
			if (!link.startsWith("http")) {
				e.getPlayer().sendMessage(ChatColor.RED + "Tai nepanašu į nuorodą. Bandykite dar kartą.");
				return;
			}
			guiManager.appendEvidence(e.getPlayer(), link);
			return;
		}

		if (chatInput.contains(uuid)) {
			e.setCancelled(true);
			chatInput.remove(uuid);

			String rawMessage = e.getMessage();
			String target = ChatColor.stripColor(rawMessage).trim().split(" ")[0];

			selectedTargets.put(uuid, target);

			Bukkit.getScheduler().runTask(this, () -> {
				e.getPlayer().sendMessage(ChatColor.GREEN + "Taikinys nustatytas: " + ChatColor.YELLOW + target);
				guiManager.openMainMenu(e.getPlayer());
			});
		}
	}

	public void resolveAndCheck(Player admin, String targetName, String type) {
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			String uuid = null;
			Player p = Bukkit.getPlayer(targetName);
			if (p != null) uuid = p.getUniqueId().toString();

			if (uuid == null) {
				OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
				if (op.hasPlayedBefore() || (op.getUniqueId().version() == 4)) {
					uuid = op.getUniqueId().toString();
				}
			}

			if (uuid == null) {
				try (java.sql.PreparedStatement st = litebans.api.Database.get().prepareStatement("SELECT uuid FROM {history} WHERE name=? LIMIT 1")) {
					st.setString(1, targetName);
					try (java.sql.ResultSet rs = st.executeQuery()) {
						if (rs.next()) uuid = rs.getString("uuid");
					}
				} catch (Exception ignored) {}
			}

			if (uuid == null) {
				Bukkit.getScheduler().runTask(this, () ->
						admin.sendMessage(ChatColor.RED + "Žaidėjas nerastas (Nėra duomenų bazėje/serveryje).")
				);
				return;
			}

			dbManager.checkAlts(admin, targetName, uuid);
			dbManager.printHistory(admin, uuid, targetName, type);
		});
	}

	public long getCooldownTime(Player p) {
		if (p.isOp() || p.hasPermission("bausme.admin")) {
			return 0;
		}

		long bestTime = getConfig().getLong("cooldowns.default", 300);

		if (getConfig().getConfigurationSection("cooldowns.ranks") != null) {
			for (String rankName : getConfig().getConfigurationSection("cooldowns.ranks").getKeys(false)) {
				String path = "cooldowns.ranks." + rankName;
				String permission = getConfig().getString(path + ".permission");
				long time = getConfig().getLong(path + ".time");

				if (permission != null && p.hasPermission(permission)) {
					if (time < bestTime) {
						bestTime = time;
					}
				}
			}
		}
		return bestTime;
	}

	public boolean isOnCooldown(Player p) {
		if (!cooldowns.containsKey(p.getUniqueId())) return false;

		long endTimestamp = cooldowns.get(p.getUniqueId());
		long timeLeft = endTimestamp - System.currentTimeMillis();

		if (timeLeft <= 0) {
			cooldowns.remove(p.getUniqueId());
			return false;
		}

		return true;
	}

	public void setCooldown(Player p) {
		long seconds = getCooldownTime(p);
		if (seconds > 0) {
			cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
		}
	}

	public long getCooldownLeft(Player p) {
		return (cooldowns.get(p.getUniqueId()) - System.currentTimeMillis()) / 1000;
	}

	public GuiManager getGuiManager() { return guiManager; }
	public StatsManager getStatsManager() { return statsManager; }
	public DiscordManager getDiscordManager() { return discordManager; }
	public HistoryManager getHistoryManager() { return historyManager; }
	public RollbackManager getRollbackManager() { return rollbackManager; }
	public LocalLogManager getLocalLogManager() { return localLogManager; }
	public PatikraManager getPatikraManager() { return patikraManager; }
	public DatabaseManager getDbManager() { return dbManager; }

	public String getSelectedTarget(UUID uuid) { return selectedTargets.getOrDefault(uuid, "Niekas"); }
	public void setCurrentMenu(UUID uuid, String menu) { currentMenus.put(uuid, menu); }
	public String getCurrentMenu(UUID uuid) { return currentMenus.get(uuid); }
	public void awaitChatInput(UUID uuid) { chatInput.add(uuid); }
}