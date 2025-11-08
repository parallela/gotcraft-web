package me.lubomirstankov.gotCraftWeb.command;

import me.lubomirstankov.gotCraftWeb.GotCraftWeb;
import me.lubomirstankov.gotCraftWeb.util.DIContainer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the /gotcraftweb command for plugin management and debugging.
 */
public class GotCraftWebCommand implements CommandExecutor, TabCompleter {

    private final GotCraftWeb plugin;
    private final DIContainer container;

    public GotCraftWebCommand(GotCraftWeb plugin, DIContainer container) {
        this.plugin = plugin;
        this.container = container;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);

            case "debug":
                return handleDebug(sender);

            case "status":
                return handleStatus(sender);

            case "help":
                sendHelp(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /gotcraftweb help");
                return true;
        }
    }

    /**
     * Handles the reload subcommand.
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("gotcraftweb.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to reload this plugin.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Reloading GotCraftWeb...");

        try {
            container.reload();
            sender.sendMessage(ChatColor.GREEN + "GotCraftWeb reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading plugin: " + e.getMessage());
            plugin.getLogger().severe("Error during reload: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Handles the debug subcommand - shows all loaded event mappings.
     */
    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("gotcraftweb.debug")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view debug information.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== GotCraftWeb Event Mappings ===");

        Map<String, Map<String, List<String>>> mappings =
            container.getConfigManager().getAllEventMappings();

        if (mappings.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No event mappings configured.");
            return true;
        }

        for (Map.Entry<String, Map<String, List<String>>> eventEntry : mappings.entrySet()) {
            String eventType = eventEntry.getKey();
            sender.sendMessage(ChatColor.AQUA + "\nEvent Type: " + ChatColor.WHITE + eventType);

            for (Map.Entry<String, List<String>> productEntry : eventEntry.getValue().entrySet()) {
                String productName = productEntry.getKey();
                List<String> commands = productEntry.getValue();

                sender.sendMessage(ChatColor.GREEN + "  Product: " + ChatColor.WHITE + productName);
                sender.sendMessage(ChatColor.GRAY + "  Commands (" + commands.size() + "):");

                for (int i = 0; i < commands.size(); i++) {
                    sender.sendMessage(ChatColor.GRAY + "    " + (i + 1) + ". " + commands.get(i));
                }
            }
        }

        sender.sendMessage(ChatColor.GOLD + "\n=== End of Mappings ===");
        return true;
    }

    /**
     * Handles the status subcommand - shows connection status.
     */
    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("gotcraftweb.status")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view status.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== GotCraftWeb Status ===");

        // Redis status
        boolean connected = container.getRedisService().isConnected();
        String status = connected ? ChatColor.GREEN + "Connected" : ChatColor.RED + "Disconnected";
        sender.sendMessage(ChatColor.YELLOW + "Redis: " + status);

        if (connected) {
            sender.sendMessage(ChatColor.GRAY + "  Host: " + container.getConfigManager().getRedisHost());
            sender.sendMessage(ChatColor.GRAY + "  Port: " + container.getConfigManager().getRedisPort());
            sender.sendMessage(ChatColor.GRAY + "  Channel: " + container.getConfigManager().getRedisChannel());
        }

        // Event mappings count
        int eventCount = container.getConfigManager().getAllEventMappings().size();
        sender.sendMessage(ChatColor.YELLOW + "Event Types: " + ChatColor.WHITE + eventCount);

        sender.sendMessage(ChatColor.GOLD + "===================");
        return true;
    }

    /**
     * Sends help message with available commands.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== GotCraftWeb Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/gotcraftweb reload" + ChatColor.GRAY + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/gotcraftweb debug" + ChatColor.GRAY + " - Show event mappings");
        sender.sendMessage(ChatColor.YELLOW + "/gotcraftweb status" + ChatColor.GRAY + " - Show connection status");
        sender.sendMessage(ChatColor.YELLOW + "/gotcraftweb help" + ChatColor.GRAY + " - Show this help");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = List.of("reload", "debug", "status", "help");
            String input = args[0].toLowerCase();

            for (String subCmd : subCommands) {
                if (subCmd.startsWith(input)) {
                    completions.add(subCmd);
                }
            }
        }

        return completions;
    }
}

