package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PatikraManager implements Listener {

	private final BausmePlugin plugin;
	private final Map<UUID, CheckSession> activeChecks = new HashMap<>();

	public PatikraManager(BausmePlugin plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

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

		// Vykdome komandą nedelsiant
		executePunishment(p, "start-command");

		int interval = plugin.getConfig().getInt("patikra.message-interval", 30);

		session.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
			session.timeLeft -= interval;

			if (session.timeLeft <= 0) {
				executePunishment(p, "timeout-command");
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

	private void executePunishment(Player player, String configKey) {
		String cmdTemplate = plugin.getConfig().getString("patikra." + configKey);
		if (cmdTemplate == null || cmdTemplate.isEmpty()) return;

		if (cmdTemplate.equals("SMART_PUNISHMENT")) {
			plugin.getDbManager().executeSmartPunishmentConsole(player.getName(), player.getUniqueId().toString(), "cheats_use");
		} else {
			// FORCE ESSENTIALS PREFIX if command starts with jail
			String finalCmd = cmdTemplate.replace("{player}", player.getName());
			if (finalCmd.startsWith("jail ")) {
				finalCmd = "essentials:" + finalCmd;
			}

			String exec = finalCmd;
			plugin.getLogger().info("[Patikra DEBUG] Executing: " + exec); // DEBUG LOG

			Bukkit.getScheduler().runTask(plugin, () -> {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), exec);
			});
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		if (activeChecks.containsKey(e.getPlayer().getUniqueId())) {
			Player p = e.getPlayer();
			executePunishment(p, "logout-command");
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