package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HistoryManager implements Listener, InventoryHolder {

	private final BausmePlugin plugin;
	private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	public HistoryManager(BausmePlugin plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	@Override
	public Inventory getInventory() { return null; }

	public void openHistory(Player admin, String targetName, List<HistoryEntry> entries, String suggestedCmd, String actionKey, int attemptedLevel, int maxLevel) {
		plugin.setCurrentMenu(admin.getUniqueId(), "HISTORY");
		Inventory inv = Bukkit.createInventory(this, 54, "Istorija: " + targetName);

		// 1. Skirti Bausmę (Rekomendacija)
		ItemStack actionItem;
		boolean isError = suggestedCmd != null && suggestedCmd.startsWith("ERROR");

		if (isError) {
			actionItem = new ItemStack(Material.BARRIER);
			ItemMeta meta = actionItem.getItemMeta();
			meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "BAUSMĖ NEGALIMA");
			List<String> lore = new ArrayList<>();
			lore.add(ChatColor.GRAY + "Priežastis:");
			lore.add(ChatColor.RED + suggestedCmd.replace("ERROR: ", ""));
			meta.setLore(lore);
			actionItem.setItemMeta(meta);
		} else if (suggestedCmd != null) {
			actionItem = new ItemStack(Material.EMERALD_BLOCK);
			ItemMeta meta = actionItem.getItemMeta();
			meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "SKIRTI BAUSMĘ");
			List<String> lore = new ArrayList<>();
			lore.add(ChatColor.GRAY + "Rekomenduojama:");
			lore.add(ChatColor.RED + suggestedCmd.replace(";", " + "));

			// Feature 5 info
			if (attemptedLevel < maxLevel) {
				lore.add("");
				lore.add(ChatColor.GOLD + "Dėmesio: Jūsų limitas " + attemptedLevel + " lygis.");
				lore.add(ChatColor.GOLD + "Žaidėjas nusipelnė " + maxLevel + " lygio.");
			}

			lore.add("");
			lore.add(ChatColor.YELLOW + "Spausk, kad tęstum!");
			meta.setLore(lore);
			actionItem.setItemMeta(meta);
		} else {
			actionItem = new ItemStack(Material.BARRIER);
			ItemMeta meta = actionItem.getItemMeta();
			meta.setDisplayName(ChatColor.RED + "Klaida: Bausmė nerasta");
			actionItem.setItemMeta(meta);
		}
		inv.setItem(22, actionItem); // Centrinis slotas

		// 2. Istorijos įrašai
		int slot = 27; // Pradedam nuo 4-os eilutės
		for (HistoryEntry entry : entries) {
			if (slot >= 54) break;

			Material mat = Material.PAPER;
			if (entry.type.equals("ban")) mat = Material.RED_WOOL;
			else if (entry.type.equals("mute")) mat = Material.ORANGE_WOOL;
			else if (entry.type.equals("warn")) mat = Material.YELLOW_WOOL;

			ItemStack is = new ItemStack(mat);
			ItemMeta meta = is.getItemMeta();
			meta.setDisplayName(ChatColor.GOLD + entry.reason + ChatColor.GRAY + " (" + entry.duration + ")");
			List<String> lore = new ArrayList<>();
			lore.add(ChatColor.GRAY + "Data: " + ChatColor.WHITE + sdf.format(new Date(entry.time)));
			lore.add(ChatColor.GRAY + "Admin: " + ChatColor.WHITE + entry.bannedBy);
			lore.add(ChatColor.GRAY + "Trukmė: " + ChatColor.WHITE + entry.duration);
			meta.setLore(lore);
			is.setItemMeta(meta);
			inv.setItem(slot++, is);
		}

		// 3. Filler Glass
		ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
		ItemMeta gMeta = glass.getItemMeta();
		gMeta.setDisplayName(" ");
		glass.setItemMeta(gMeta);
		for(int i=0; i<54; i++) if(inv.getItem(i) == null) inv.setItem(i, glass);

		admin.openInventory(inv);

		// Store session info
		pendingCommands.put(admin.getUniqueId(), new PendingInfo(suggestedCmd, targetName, actionKey, attemptedLevel, maxLevel));
	}

	private final java.util.Map<java.util.UUID, PendingInfo> pendingCommands = new java.util.HashMap<>();

	private static class PendingInfo {
		String cmd, target, action;
		int attemptedLevel, maxLevel;
		PendingInfo(String c, String t, String a, int att, int max) {
			cmd = c; target = t; action = a;
			attemptedLevel = att;
			maxLevel = max;
		}
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {
		if (e.getInventory().getHolder() != this) return;
		e.setCancelled(true);
		if (!(e.getWhoClicked() instanceof Player)) return;
		Player p = (Player) e.getWhoClicked();

		if (e.getSlot() == 22) {
			PendingInfo info = pendingCommands.get(p.getUniqueId());
			if (info != null && info.cmd != null && !info.cmd.startsWith("ERROR")) {
				plugin.getGuiManager().openConfirmMenu(p, info.cmd, info.target, info.action, info.attemptedLevel, info.maxLevel);
			} else {
				p.sendMessage(ChatColor.RED + "Veiksmas negalimas.");
			}
		}
	}

	public static class HistoryEntry {
		String type, reason, bannedBy, duration;
		long time;

		public HistoryEntry(String type, String reason, String bannedBy, long time, String duration) {
			this.type = type;
			this.reason = reason;
			this.bannedBy = bannedBy;
			this.time = time;
			this.duration = duration;
		}
	}
}