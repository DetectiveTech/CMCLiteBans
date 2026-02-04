package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GuiManager implements Listener, InventoryHolder {

	private final BausmePlugin plugin;
	private final Map<UUID, ConfirmSession> sessions = new HashMap<>();
	private final Map<UUID, Boolean> silentMode = new HashMap<>();
	private final Map<UUID, Boolean> awaitingEvidence = new HashMap<>();

	public GuiManager(BausmePlugin plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	@Override
	public Inventory getInventory() { return null; }

	public void openMainMenu(Player admin) {
		openGui(admin, "gui", "MAIN");

		Inventory inv = admin.getOpenInventory().getTopInventory();
		if (inv.getHolder() == this && "MAIN".equals(plugin.getCurrentMenu(admin.getUniqueId()))) {
			boolean isSilent = silentMode.getOrDefault(admin.getUniqueId(), false);
			ItemStack silentBtn = new ItemStack(isSilent ? Material.LIME_DYE : Material.GRAY_DYE);
			ItemMeta meta = silentBtn.getItemMeta();
			meta.setDisplayName(ChatColor.GOLD + "Silent Mode: " + (isSilent ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
			meta.setLore(Arrays.asList(ChatColor.GRAY + "Spausk, kad perjungtum -s"));
			silentBtn.setItemMeta(meta);
			inv.setItem(8, silentBtn);
		}
	}

	public void openSubMenu(Player admin, String menuName) {
		openGui(admin, "sub_menus." + menuName, menuName);
	}

	public void openConfirmMenu(Player admin, String command, String targetName, String reason, int attemptedLevel, int maxLevel) {
		if (command == null) {
			admin.sendMessage(ChatColor.RED + "Klaida: Bausmė negalima.");
			admin.closeInventory();
			return;
		}

		sessions.put(admin.getUniqueId(), new ConfirmSession(command, targetName, reason, attemptedLevel, maxLevel));
		plugin.setCurrentMenu(admin.getUniqueId(), "CONFIRM");

		Inventory inv = Bukkit.createInventory(this, 27, ChatColor.DARK_RED + "Patvirtinti Bausmę?");
		boolean isSilent = silentMode.getOrDefault(admin.getUniqueId(), false);

		ItemStack confirm = new ItemStack(Material.LIME_WOOL);
		ItemMeta meta = confirm.getItemMeta();
		meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "PATVIRTINTI");
		List<String> lore = new ArrayList<>();
		lore.add(ChatColor.GRAY + "Taikinys: " + ChatColor.YELLOW + targetName);
		lore.add(ChatColor.GRAY + "Bausmė: " + ChatColor.RED + command.replace(";", " + "));
		if (isSilent) lore.add(ChatColor.AQUA + "Silent Mode (-s) Įjungtas");

		if (attemptedLevel < maxLevel) {
			lore.add(ChatColor.GOLD + "Dėmesio: Jūsų limitas " + attemptedLevel + " lygis (reikia " + maxLevel + ").");
		}

		lore.add("");
		lore.add(ChatColor.YELLOW + "Spausk, kad nubaustum!");
		meta.setLore(lore);
		confirm.setItemMeta(meta);
		inv.setItem(11, confirm);

		ItemStack evidence = new ItemStack(Material.WRITABLE_BOOK);
		ItemMeta eMeta = evidence.getItemMeta();
		eMeta.setDisplayName(ChatColor.GOLD + "Pridėti Įrodymus");
		evidence.setItemMeta(eMeta);
		inv.setItem(13, evidence);

		ItemStack cancel = new ItemStack(Material.RED_WOOL);
		ItemMeta cMeta = cancel.getItemMeta();
		cMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "ATŠAUKTI");
		cancel.setItemMeta(cMeta);
		inv.setItem(15, cancel);

		ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
		ItemMeta gMeta = glass.getItemMeta();
		gMeta.setDisplayName(" ");
		glass.setItemMeta(gMeta);
		for(int i=0; i<27; i++) if(inv.getItem(i) == null) inv.setItem(i, glass);

		admin.openInventory(inv);
	}

	public boolean isAwaitingEvidence(UUID uuid) {
		return awaitingEvidence.getOrDefault(uuid, false);
	}

	public void appendEvidence(Player admin, String link) {
		awaitingEvidence.remove(admin.getUniqueId());
		ConfirmSession session = sessions.get(admin.getUniqueId());

		if (session != null) {
			String newCmd = session.command + " | Įrodymas: " + link;
			openConfirmMenu(admin, newCmd, session.targetName, session.actionKey, session.attemptedLevel, session.maxLevel);
		} else {
			admin.sendMessage(ChatColor.RED + "Sesija pasibaigė.");
		}
	}

	private void openGui(Player admin, String configPath, String menuId) {
		ConfigurationSection section = plugin.getConfig().getConfigurationSection(configPath);
		if (section == null) {
			admin.sendMessage(ChatColor.RED + "Klaida: Konfigūracija nerasta: " + configPath);
			return;
		}

		int size = section.getInt("size", 27);
		String title = ChatColor.translateAlternateColorCodes('&', section.getString("title", "Menu"));

		Inventory inv = Bukkit.createInventory(this, size, title);
		String target = plugin.getSelectedTarget(admin.getUniqueId());

		ConfigurationSection items = section.getConfigurationSection("items");
		if (items != null) {
			for (String key : items.getKeys(false)) {
				ConfigurationSection itemSec = items.getConfigurationSection(key);
				if (itemSec == null) continue;

				int slot = itemSec.getInt("slot", -1);
				ItemStack is = createItem(admin, itemSec, target);

				if (key.equals("glass_filler")) {
					for (int i = 0; i < size; i++) if (inv.getItem(i) == null) inv.setItem(i, is);
				} else if (slot >= 0 && slot < size) {
					inv.setItem(slot, is);
				}
			}
		}

		plugin.setCurrentMenu(admin.getUniqueId(), menuId);
		admin.openInventory(inv);
	}

	private ItemStack createItem(Player admin, ConfigurationSection sec, String target) {
		String matName = sec.getString("material", "STONE");
		Material mat = Material.getMaterial(matName);
		if (mat == null) mat = Material.STONE;

		ItemStack item = new ItemStack(mat);
		ItemMeta meta = item.getItemMeta();

		if (sec.getName().equals("select_player") && meta instanceof SkullMeta && !target.equals("Niekas")) {
			((SkullMeta) meta).setOwner(target);
		} else if (sec.getName().equals("admin_head") && meta instanceof SkullMeta) {
			((SkullMeta) meta).setOwningPlayer(admin);
		}

		if (sec.contains("name")) meta.setDisplayName(replacePlaceholders(sec.getString("name"), admin, target));
		if (sec.contains("lore")) {
			List<String> lore = new ArrayList<>();
			for (String line : sec.getStringList("lore")) lore.add(replacePlaceholders(line, admin, target));
			meta.setLore(lore);
		}

		item.setItemMeta(meta);
		return item;
	}

	private String replacePlaceholders(String text, Player admin, String target) {
		if (text == null) return "";

		// Basic placeholders
		text = text.replace("%admin%", admin.getName());
		text = text.replace("%target%", target);

		// NEW: Points Placeholder
		int points = plugin.getStatsManager().getScore(admin.getName());
		text = text.replace("%points%", String.valueOf(points));

		// Reset days (Optional)
		text = text.replace("%reset_days%", String.valueOf(plugin.getConfig().getInt("admin-stats.reset-days", 7)));

		return ChatColor.translateAlternateColorCodes('&', text);
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {
		if (!(e.getWhoClicked() instanceof Player)) return;
		Player admin = (Player) e.getWhoClicked();

		if (e.getInventory().getHolder() != this) return;
		e.setCancelled(true);
		if (e.getCurrentItem() == null) return;

		String menuId = plugin.getCurrentMenu(admin.getUniqueId());

		if ("MAIN".equals(menuId) && e.getSlot() == 8) {
			boolean current = silentMode.getOrDefault(admin.getUniqueId(), false);
			silentMode.put(admin.getUniqueId(), !current);
			openMainMenu(admin);
			return;
		}

		if ("CONFIRM".equals(menuId)) {
			if (e.getSlot() == 11) {
				ConfirmSession session = sessions.remove(admin.getUniqueId());
				admin.closeInventory();

				if (plugin.isOnCooldown(admin)) {
					admin.sendMessage(ChatColor.RED + "Palaukite prieš bausdami vėl!");
					return;
				}

				if (session != null) {
					String fullCmd = session.command;
					String[] commands = fullCmd.split(";");
					boolean isSilent = silentMode.getOrDefault(admin.getUniqueId(), false);
					boolean anySuccess = false;

					String preCommand = plugin.getConfig().getString("run_command_before_ban");
					if (preCommand != null && !preCommand.isEmpty()) {
						Bukkit.dispatchCommand(Bukkit.getConsoleSender(), preCommand.replace("{player}", session.targetName));
					}

					for (String singleCmd : commands) {
						singleCmd = singleCmd.trim();
						if (isSilent) {
							String[] parts = singleCmd.split(" ", 2);
							singleCmd = parts[0] + " -s " + (parts.length > 1 ? parts[1] : "");
						}

						String type = singleCmd.split(" ")[0].replace("ip", "");
						if (admin.hasPermission("litebans." + type) || admin.isOp()) {
							admin.performCommand(singleCmd);
							anySuccess = true;
						} else {
							admin.sendMessage(ChatColor.RED + "Klaida: Jums trūksta teisės įvykdyti '" + type + "'.");
						}
					}

					if (anySuccess) {
						admin.sendMessage(ChatColor.GREEN + "Komanda išsiųsta (laukiamas patvirtinimas).");

						// --- FIX: DO NOT SET COOLDOWN HERE ---
						// plugin.setCooldown(admin); <-- REMOVED
						// We will set cooldown ONLY when LiteBans successfully adds the entry.

						// We still assume logs for now, but strictly speaking, these should also move to an event listener
						// to be 100% accurate. For now, leaving them here is acceptable as "Command Sent Logs".
						plugin.getLocalLogManager().logPunishment(admin.getName(), session.targetName, fullCmd, session.actionKey);
						plugin.getDiscordManager().sendLog(admin.getName(), session.targetName, fullCmd, session.actionKey);
						plugin.getRollbackManager().checkAndRollback(commands[0], session.targetName);

						if (session.attemptedLevel < session.maxLevel) {
							String msg = plugin.getConfig().getString("if_player_gives_to_low_punishment");
							if (msg != null && !msg.isEmpty()) {
								msg = msg.replace("{admin}", admin.getName())
										.replace("{target}", session.targetName)
										.replace("{level}", String.valueOf(session.attemptedLevel))
										.replace("{req_level}", String.valueOf(session.maxLevel));
								Bukkit.dispatchCommand(Bukkit.getConsoleSender(), msg);
							}
						}
					} else {
						admin.sendMessage(ChatColor.RED + "Bausmė nepavyko - cooldown nesuteiktas.");
					}
				}
			} else if (e.getSlot() == 13) {
				admin.closeInventory();
				admin.sendMessage(ChatColor.YELLOW + "Įklijuokite įrodymų nuorodą į chatą:");
				awaitingEvidence.put(admin.getUniqueId(), true);
				plugin.awaitChatInput(admin.getUniqueId());
			} else if (e.getSlot() == 15) {
				sessions.remove(admin.getUniqueId());
				openMainMenu(admin);
			}
			return;
		}

		String configPath = "MAIN".equals(menuId) ? "gui.items" : "sub_menus." + menuId + ".items";
		ConfigurationSection items = plugin.getConfig().getConfigurationSection(configPath);
		if (items == null) return;

		for (String key : items.getKeys(false)) {
			ConfigurationSection sec = items.getConfigurationSection(key);
			if (sec.getInt("slot") == e.getSlot()) {
				if (key.equals("select_player")) {
					admin.closeInventory();
					plugin.awaitChatInput(admin.getUniqueId());
					admin.sendMessage(ChatColor.GREEN + "Įrašykite vardą:");
					return;
				}
				if (sec.contains("open_menu")) {
					openSubMenu(admin, sec.getString("open_menu"));
					return;
				}
				if (sec.contains("action")) {
					String action = sec.getString("action");
					if ("back".equals(action)) {
						openMainMenu(admin);
						return;
					}
					String target = plugin.getSelectedTarget(admin.getUniqueId());
					if ("Niekas".equals(target)) {
						admin.sendMessage(ChatColor.RED + "Pirmiausia pasirinkite žaidėją!");
						return;
					}
					plugin.resolveAndCheck(admin, target, action);
					return;
				}
			}
		}
	}

	private static class ConfirmSession {
		String command, targetName, actionKey;
		int attemptedLevel, maxLevel;
		ConfirmSession(String c, String t, String a, int attempted, int max) {
			this.command = c;
			this.targetName = t;
			this.actionKey = a;
			this.attemptedLevel = attempted;
			this.maxLevel = max;
		}
	}
}