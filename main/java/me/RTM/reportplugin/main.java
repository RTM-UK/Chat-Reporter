package me.RTM.reportplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

public class Main extends JavaPlugin implements Listener {

    private final Map<UUID, List<String>> chatLogs = new HashMap<>();
    private String webhookUrl = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        webhookUrl = getConfig().getString("webhook");

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("ReportPlugin Enabled!");
    }

    // ---------------- CHAT LOGGING ----------------

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        chatLogs.putIfAbsent(player.getUniqueId(), new ArrayList<>());
        List<String> logs = chatLogs.get(player.getUniqueId());

        logs.add(event.getMessage());

        if (logs.size() > 15) {
            logs.remove(0);
        }
    }

    // ---------------- COMMANDS ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /link <webhook>
        if (command.getName().equalsIgnoreCase("link")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /link <discord_webhook_url>");
                return true;
            }

            webhookUrl = args[0];

            getConfig().set("webhook", webhookUrl);
            saveConfig();

            player.sendMessage(ChatColor.GREEN + "Webhook linked successfully!");
            return true;
        }

        // /report <player> <reason>
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length < 2) {
            reporter.sendMessage(ChatColor.RED + "Usage: /report <player> <reason>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            reporter.sendMessage(ChatColor.RED + "That player is not online.");
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        Location loc = target.getLocation();
        List<String> logs = chatLogs.getOrDefault(target.getUniqueId(), new ArrayList<>());

        // ---------------- STAFF ALERT ----------------

        for (Player online : Bukkit.getOnlinePlayers()) {

            if (online.isOp()) {

                online.sendMessage(ChatColor.DARK_RED + "==========================");
                online.sendMessage(ChatColor.RED + "NEW PLAYER REPORT");
                online.sendMessage(ChatColor.YELLOW + "Reporter: " + reporter.getName());
                online.sendMessage(ChatColor.YELLOW + "Reported Player: " + target.getName());
                online.sendMessage(ChatColor.YELLOW + "Reason: " + reason);
                online.sendMessage(ChatColor.YELLOW + "World: " + loc.getWorld().getName());
                online.sendMessage(ChatColor.YELLOW + "XYZ: " +
                        loc.getBlockX() + ", " +
                        loc.getBlockY() + ", " +
                        loc.getBlockZ());

                online.sendMessage(ChatColor.GRAY + "Recent Chat Logs:");

                if (logs.isEmpty()) {
                    online.sendMessage(ChatColor.GRAY + "- No recent messages");
                } else {
                    for (String msg : logs) {
                        online.sendMessage(ChatColor.WHITE + "- " + msg);
                    }
                }

                online.sendMessage(ChatColor.DARK_RED + "==========================");
            }
        }

        // ---------------- SAVE REPORT FILE ----------------

        saveReport(reporter, target, reason, loc, logs);

        // ---------------- DISCORD WEBHOOK ----------------

        sendDiscordReport(reporter, target, reason, loc, logs);

        reporter.sendMessage(ChatColor.GREEN + "Report submitted successfully.");

        return true;
    }

    // ---------------- DISCORD WEBHOOK ----------------

    private void sendDiscordReport(Player reporter,
                                   Player target,
                                   String reason,
                                   Location loc,
                                   List<String> logs) {

        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                java.net.URL url = new java.net.URL(webhookUrl);
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                // LIMIT chat logs to avoid 400 errors
                StringBuilder logText = new StringBuilder();
                int count = 0;

                for (String msg : logs) {
                    logText.append(msg).append("\\n");
                    if (++count >= 10) break; // prevent overflow
                }

                String json =
                        "{"
                                + "\"embeds\":[{"
                                + "\"title\":\"🚨 New Player Report\","
                                + "\"color\":15158332,"
                                + "\"fields\":["
                                + "{"
                                + "\"name\":\"Reporter\","
                                + "\"value\":\"" + reporter.getName() + "\","
                                + "\"inline\":true"
                                + "},"
                                + "{"
                                + "\"name\":\"Target\","
                                + "\"value\":\"" + target.getName() + "\","
                                + "\"inline\":true"
                                + "},"
                                + "{"
                                + "\"name\":\"Reason\","
                                + "\"value\":\"" + reason + "\","
                                + "\"inline\":false"
                                + "},"
                                + "{"
                                + "\"name\":\"Location\","
                                + "\"value\":\"" + loc.getWorld().getName()
                                + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")\","
                                + "\"inline\":false"
                                + "},"
                                + "{"
                                + "\"name\":\"Recent Chat\","
                                + "\"value\":\"" + (logText.length() == 0 ? "None" : logText.toString()) + "\","
                                + "\"inline\":false"
                                + "}"
                                + "]"
                                + "}]"
                                + "}";

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();

                if (code != 204) {
                    getLogger().warning("Discord webhook failed with HTTP code: " + code);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ---------------- FILE SAVE ----------------

    private void saveReport(Player reporter,
                            Player target,
                            String reason,
                            Location loc,
                            List<String> logs) {

        try {
            File folder = new File(getDataFolder(), "reports");

            if (!folder.exists()) {
                folder.mkdirs();
            }

            String fileName = target.getName() + "-" + System.currentTimeMillis() + ".txt";
            File file = new File(folder, fileName);

            FileWriter writer = new FileWriter(file);

            writer.write("====================================\n");
            writer.write("REPORT TIME: " + LocalDateTime.now() + "\n");
            writer.write("Reporter: " + reporter.getName() + "\n");
            writer.write("Reported Player: " + target.getName() + "\n");
            writer.write("Reported UUID: " + target.getUniqueId() + "\n");
            writer.write("Reason: " + reason + "\n");
            writer.write("World: " + loc.getWorld().getName() + "\n");
            writer.write("Coordinates: " +
                    loc.getBlockX() + ", " +
                    loc.getBlockY() + ", " +
                    loc.getBlockZ() + "\n\n");

            writer.write("Recent Chat Logs:\n");

            if (logs.isEmpty()) {
                writer.write("- No recent messages\n");
            } else {
                for (String msg : logs) {
                    writer.write("- " + msg + "\n");
                }
            }

            writer.write("====================================\n");

            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
