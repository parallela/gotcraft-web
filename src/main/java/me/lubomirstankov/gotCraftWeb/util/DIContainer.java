package me.lubomirstankov.gotCraftWeb.util;

import me.lubomirstankov.gotCraftWeb.GotCraftWeb;
import me.lubomirstankov.gotCraftWeb.config.ConfigManager;
import me.lubomirstankov.gotCraftWeb.service.NotificationService;
import me.lubomirstankov.gotCraftWeb.service.RedisService;

/**
 * Simple Dependency Injection container for managing plugin services.
 * Provides centralized access to all major components.
 */
public class DIContainer {

    private final GotCraftWeb plugin;

    // Services
    private ConfigManager configManager;
    private NotificationService notificationService;
    private RedisService redisService;

    public DIContainer(GotCraftWeb plugin) {
        this.plugin = plugin;
        initialize();
    }

    /**
     * Initializes all services in the correct order with proper dependencies.
     */
    private void initialize() {
        // 1. Initialize ConfigManager first (no dependencies)
        configManager = new ConfigManager(plugin);

        // 2. Initialize NotificationService (depends on ConfigManager)
        notificationService = new NotificationService(plugin, configManager);

        // 3. Initialize RedisService (depends on ConfigManager)
        redisService = new RedisService(plugin, configManager);
    }

    /**
     * Reloads all services that support reloading.
     */
    public void reload() {
        plugin.getLogger().info("Reloading plugin services...");

        // Disconnect from Redis
        if (redisService != null && redisService.isConnected()) {
            redisService.disconnect();
        }

        // Reload configuration
        configManager.loadConfig();

        // Reconnect to Redis with new config
        if (redisService.connect()) {
            redisService.startSubscription(notificationService);
        }

        plugin.getLogger().info("Plugin services reloaded successfully");
    }

    /**
     * Shuts down all services gracefully.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down plugin services...");

        if (redisService != null) {
            redisService.disconnect();
        }

        plugin.getLogger().info("All services shut down");
    }

    // Getters for services

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public RedisService getRedisService() {
        return redisService;
    }
}

