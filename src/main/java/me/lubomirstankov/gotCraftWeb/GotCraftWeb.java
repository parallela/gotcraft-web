package me.lubomirstankov.gotCraftWeb;

import me.lubomirstankov.gotCraftWeb.command.GotCraftWebCommand;
import me.lubomirstankov.gotCraftWeb.util.DIContainer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for GotCraftWeb.
 * Handles Redis pub/sub notifications from web payment system and executes configured commands.
 *
 * @author lubomirstankov
 * @version 1.0
 */
public final class GotCraftWeb extends JavaPlugin {

    private DIContainer container;

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("Starting GotCraftWeb plugin...");

        try {
            // Initialize dependency injection container
            container = new DIContainer(this);

            // Register commands
            registerCommands();

            // Connect to Redis and start subscription
            if (container.getRedisService().connect()) {
                container.getRedisService().startSubscription(container.getNotificationService());
                getLogger().info("GotCraftWeb plugin enabled successfully!");
            } else {
                getLogger().severe("Failed to connect to Redis. Plugin will continue but notifications won't work.");
                getLogger().severe("Please check your Redis configuration in config.yml");
            }

        } catch (Exception e) {
            getLogger().severe("Failed to enable GotCraftWeb: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Shutting down GotCraftWeb plugin...");

        if (container != null) {
            container.shutdown();
        }

        getLogger().info("GotCraftWeb plugin disabled successfully!");
    }

    /**
     * Registers plugin commands with executors and tab completers.
     */
    private void registerCommands() {
        GotCraftWebCommand commandExecutor = new GotCraftWebCommand(this, container);

        org.bukkit.command.PluginCommand command = getCommand("gotcraftweb");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        } else {
            getLogger().warning("Could not register /gotcraftweb command!");
        }
    }

    /**
     * Gets the dependency injection container.
     *
     * @return The DI container instance
     */
    public DIContainer getContainer() {
        return container;
    }
}
