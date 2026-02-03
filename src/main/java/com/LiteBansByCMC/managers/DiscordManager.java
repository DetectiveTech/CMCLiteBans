package com.LiteBansByCMC.managers;

import com.LiteBansByCMC.BausmePlugin;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordManager {

	private final BausmePlugin plugin;

	public DiscordManager(BausmePlugin plugin) {
		this.plugin = plugin;
	}

	public void sendLog(String admin, String target, String command, String reason) {
		if (!plugin.getConfig().getBoolean("discord.enabled")) return;

		String webhookUrl = plugin.getConfig().getString("discord.webhook-url");
		if (webhookUrl == null || webhookUrl.equals("YOUR_WEBHOOK_URL")) return;

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				URL url = new URL(webhookUrl);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setDoOutput(true);

				// Simple JSON construction
				String json = String.format(
						"{" +
								"\"username\": \"%s\"," +
								"\"avatar_url\": \"%s\"," +
								"\"embeds\": [{" +
								"\"title\": \"Bausmė Patvirtinta\"," +
								"\"color\": 16711680," + // Red color
								"\"fields\": [" +
								"{\"name\": \"Admin\", \"value\": \"%s\", \"inline\": true}," +
								"{\"name\": \"Target\", \"value\": \"%s\", \"inline\": true}," +
								"{\"name\": \"Command\", \"value\": \"`%s`\"}," +
								"{\"name\": \"Reason\", \"value\": \"%s\"}" +
								"]" +
								"}]" +
								"}",
						plugin.getConfig().getString("discord.username", "Bausmės"),
						plugin.getConfig().getString("discord.avatar-url", "").replace("{admin}", admin),
						admin, target, command, reason
				);

				try (OutputStream os = conn.getOutputStream()) {
					byte[] input = json.getBytes(StandardCharsets.UTF_8);
					os.write(input, 0, input.length);
				}

				conn.getInputStream().close(); // Fire request
			} catch (Exception e) {
				plugin.getLogger().warning("Failed to send Discord log: " + e.getMessage());
			}
		});
	}
}