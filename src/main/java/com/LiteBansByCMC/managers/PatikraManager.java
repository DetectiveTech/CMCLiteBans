package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PatikraManager implements Listener {

	private final BausmePlugin plugin;
	private final Map<UUID, CheckSession> activeChecks = new HashMap<>();

	// Failas saugoti žaidėjus, kuriems reikia nuimti jail prisijungus
	private File unjailFile;
	private FileConfiguration unjailConfig;

	public PatikraManager(BausmePlugin plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
		loadUnjailData();
	}

	private void loadUnjailData() {
		unjailFile = new File(plugin.getDataFolder(), "unjail_queue.yml");
		if (!unjailFile.exists()) {
			try { unjailFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
		}
		unjailConfig = YamlConfiguration.loadConfiguration(unjailFile);
	}

	private void saveUnjailData() {
		try { unjailConfig.save(unjailFile); } catch (IOException e) { e.printStackTrace(); }
	}

	// --- UNJAIL QUEUE LOGIC ---
	private void queueUnjail(String playerName) {
		List<String> list = unjailConfig.getStringList("pending");
		if (!list.contains(playerName)) {
			list.add(playerName);
			unjailConfig.set("pending", list);
			saveUnjailData();
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		List<String> list = unjailConfig.getStringList("pending");

		if (list.contains(p.getName())) {
			// Žaidėjas prisijungė, ir jis laukia unjail
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "essentials:unjail " + p.getName());
				plugin.getLogger().info("[Patikra] Queued unjail executed for " + p.getName());

				// Išimame iš sąrašo
				list.remove(p.getName());
				unjailConfig.set("pending", list);
				saveUnjailData();
			}, 20L); // 1 sekundės pauzė po prisijungimo, kad Essentials užsikrautų
		}
	}

	// --- COMMAND HANDLER ---
	public void handleCommand(CommandSender sender, String[] args) {
		if (!sender.hasPermission("bausme.patikra")) {
			sender.sendMessage(ChatColor.RED + "Neturite teisės.");
			return;
		}

		if (args.length < 2) {
			sender.sendMessage(color(plugin.getConfig().getString("patikra.messages.usage")));
			return;
		}

		String targetName = args[0];
		Player target = Bukkit.getPlayer(targetName);

		// --- STOP ---
		if (args[1].equalsIgnoreCase("stop")) {
			if (target == null) {
				AtomicBoolean found = new AtomicBoolean(false);
				activeChecks.entrySet().removeIf(entry -> {
					if (entry.getValue().playerName.equalsIgnoreCase(targetName)) {
						entry.getValue().cancel();
						sender.sendMessage(color(plugin.getConfig().getString("patikra.messages.stopped_admin").replace("{player}", targetName)));
						found.set(true);
						return true;
					}
					return false;
				});
				if (!found.get()) sender.sendMessage(color(plugin.getConfig().getString("patikra.messages.not_checking")));
				return;
			}

			if (!activeChecks.containsKey(target.getUniqueId())) {
				sender.sendMessage(color(plugin.getConfig().getString("patikra.messages.not_checking")));
				return;
			}

			stopCheck(target.getUniqueId());
			sender.sendMessage(color(plugin.getConfig().getString("patikra.messages.stopped_admin").replace("{player}", target.getName())));
			target.sendMessage(ChatColor.YELLOW + "Laikmatis sustabdytas.");

			// Jei stop rankiniu būdu, galime irgi nuimti jail
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "essentials:unjail " + target.getName());
			return;
		}

		// --- START ---
		if (target == null) {
			sender.sendMessage(color(plugin.getConfig().getString("patikra.messages.not_found")));
			return;
		}

		if (activeChecks.containsKey(target.getUniqueId())) {
			sender.sendMessage(color(plugin.getConfig().getString("patikra.messages.already_checking")));
			return;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < args.length; i++) sb.append(args[i]).append(" ");
		String discordText = sb.toString().trim();

		startCheck(target, discordText);
		sender.sendMessage(color(plugin.getConfig().getString("patikra.messages.started_admin").replace("{player}", target.getName())));
	}

	private void startCheck(Player p, String text) {
		int duration = plugin.getConfig().getInt("patikra.duration", 300);
		CheckSession session = new CheckSession(p.getName(), text, duration);
		activeChecks.put(p.getUniqueId(), session);

		// Start command
		executeStartCommand(p, "start-command");

		int interval = plugin.getConfig().getInt("patikra.message-interval", 30);

		session.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
			session.timeLeft -= interval;

			if (session.timeLeft <= 0) {
				// LAIKAS BAIGĖSI (Player is ONLINE)
				executePunishment(p, "timeout-command", true); // true = player is online
				String broadcast = plugin.getConfig().getString("patikra.messages.logout_broadcast");
				if (broadcast != null) Bukkit.broadcastMessage(color(broadcast.replace("{player}", p.getName())));
				stopCheck(p.getUniqueId());
				return;
			}

			sendAlerts(p, text, session.timeLeft);

		}, 0L, interval * 20L);
	}

	private void stopCheck(UUID uuid) {
		CheckSession session = activeChecks.remove(uuid);
		if (session != null) session.cancel();
	}

	private void sendAlerts(Player p, String text, int secondsLeft) {
		String timeStr = String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60);
		String title = color(plugin.getConfig().getString("patikra.messages.player_title"));
		String sub = color(plugin.getConfig().getString("patikra.messages.player_subtitle").replace("{text}", text));
		p.sendTitle(title, sub, 0, 100, 0);

		for (String line : plugin.getConfig().getStringList("patikra.messages.player_chat")) {
			p.sendMessage(color(line.replace("{time}", timeStr).replace("{text}", text)));
		}
	}

	private void executeStartCommand(Player player, String configKey) {
		String cmdTemplate = plugin.getConfig().getString("patikra." + configKey);
		if (cmdTemplate == null || cmdTemplate.isEmpty()) return;

		String finalCmd = cmdTemplate.replace("{player}", player.getName());
		if (finalCmd.startsWith("jail ")) finalCmd = "essentials:" + finalCmd;

		String exec = finalCmd;
		Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), exec));
	}

	// EXECUTE PUNISHMENT
	// isOnline = true (Laikas baigėsi), false (Atsijungė)
	private void executePunishment(Player player, String configKey, boolean isOnline) {

		// 1. UNJAIL LOGIC
		if (isOnline) {
			// Jei žaidėjas prisijungęs, nuimame jail iš karto
			Bukkit.getScheduler().runTask(plugin, () -> {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "essentials:unjail " + player.getName());
			});
		} else {
			// Jei žaidėjas atsijungęs, dedame į eilę
			queueUnjail(player.getName());
			plugin.getLogger().info("[Patikra] Player offline. Added " + player.getName() + " to unjail queue.");
		}

		// 2. BAN LOGIC
		String cmdTemplate = plugin.getConfig().getString("patikra." + configKey);
		if (cmdTemplate == null || cmdTemplate.isEmpty()) return;

		if (cmdTemplate.equals("SMART_PUNISHMENT")) {
			plugin.getDbManager().executeSmartPunishmentConsole(player.getName(), player.getUniqueId().toString(), "cheats_use");
		} else {
			String finalCmd = cmdTemplate.replace("{player}", player.getName());
			if (finalCmd.startsWith("jail ")) finalCmd = "essentials:" + finalCmd;

			String exec = finalCmd;
			// Jei online, duodam mažą pauzę, kad unjail suveiktų
			long delay = isOnline ? 5L : 0L;

			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), exec);
			}, delay);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		if (activeChecks.containsKey(e.getPlayer().getUniqueId())) {
			Player p = e.getPlayer();

			// Vykdome bausmę su žyma, kad žaidėjas OFFLINE
			executePunishment(p, "logout-command", false);

			String broadcast = plugin.getConfig().getString("patikra.messages.logout_broadcast");
			if (broadcast != null) Bukkit.broadcastMessage(color(broadcast.replace("{player}", p.getName())));

			stopCheck(p.getUniqueId());
		}
	}

	private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

	private class CheckSession {
		String playerName;
		String text;
		int timeLeft;
		int taskId = -1;
		public CheckSession(String name, String text, int time) { this.playerName = name; this.text = text; this.timeLeft = time; }
		public void cancel() { if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); }
	}
}