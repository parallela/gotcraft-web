package me.lubomirstankov.gotCraftWeb.config;

import me.lubomirstankov.gotCraftWeb.GotCraftWeb;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages all configuration operations for the plugin.
 * Handles loading, reloading, and accessing config values.
 */
public class ConfigManager {

    private final GotCraftWeb plugin;
    private final Logger logger;
    private FileConfiguration config;

    // Redis configuration
    private String redisHost;
    private int redisPort;
    private String redisPassword;
    private String redisChannel;
    private int redisTimeout;

    // Event mappings: eventType -> productName -> List of commands
    private Map<String, Map<String, List<String>>> eventMappings;

    public ConfigManager(GotCraftWeb plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.eventMappings = new HashMap<>();
        loadConfig();
    }

    /**
     * Loads or reloads the configuration from config.yml.
     */
    public void loadConfig() {
        // Save default config if not exists
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Load Redis configuration
        loadRedisConfig();

        // Load event mappings
        loadEventMappings();

        logger.info("Configuration loaded successfully");
    }

    /**
     * Loads Redis connection settings from config.
     */
    private void loadRedisConfig() {
        redisHost = config.getString("redis.host", "localhost");
        redisPort = config.getInt("redis.port", 6379);
        redisPassword = config.getString("redis.password", "");
        redisChannel = config.getString("redis.channel", "gotcraft_notifications");
        redisTimeout = config.getInt("redis.timeout", 2000);

        logger.info("Redis config: " + redisHost + ":" + redisPort + " channel=" + redisChannel);
    }

    /**
     * Loads event type -> product name -> commands mappings from config.
     */
    private void loadEventMappings() {
        eventMappings.clear();

        ConfigurationSection eventsSection = config.getConfigurationSection("events");
        if (eventsSection == null) {
            logger.warning("No 'events' section found in config.yml");
            return;
        }

        // Iterate through each event type (e.g., payment_success)
        for (String eventType : eventsSection.getKeys(false)) {
            Map<String, List<String>> productMappings = new HashMap<>();

            ConfigurationSection eventSection = eventsSection.getConfigurationSection(eventType);
            if (eventSection == null) continue;

            // Iterate through each product name under the event type
            for (String productName : eventSection.getKeys(false)) {
                List<String> commands = eventSection.getStringList(productName);
                if (!commands.isEmpty()) {
                    productMappings.put(productName, new ArrayList<>(commands));
                    logger.info("Loaded mapping: " + eventType + " -> " + productName + " -> " + commands.size() + " commands");
                }
            }

            if (!productMappings.isEmpty()) {
                eventMappings.put(eventType, productMappings);
            }
        }

        logger.info("Loaded " + eventMappings.size() + " event type mappings");
    }

    /**
     * Retrieves commands for a specific event type and product name.
     *
     * @param eventType The event type (e.g., "payment_success")
     * @param productName The product name (e.g., "VIP")
     * @return List of commands to execute, or empty list if no mapping exists
     */
    public List<String> getCommands(String eventType, String productName) {
        if (eventMappings.containsKey(eventType)) {
            Map<String, List<String>> productMap = eventMappings.get(eventType);
            if (productMap.containsKey(productName)) {
                return new ArrayList<>(productMap.get(productName));
            }
        }
        return new ArrayList<>();
    }

    /**
     * Gets all event mappings for debugging purposes.
     *
     * @return Map of all event -> product -> commands mappings
     */
    public Map<String, Map<String, List<String>>> getAllEventMappings() {
        return new HashMap<>(eventMappings);
    }

    // Getters for Redis configuration
    public String getRedisHost() {
        return redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public String getRedisPassword() {
        return redisPassword;
    }

    public String getRedisChannel() {
        return redisChannel;
    }

    public int getRedisTimeout() {
        return redisTimeout;
    }

    /**
     * Checks if Redis password is configured.
     *
     * @return true if password is set and not empty
     */
    public boolean hasRedisPassword() {
        return redisPassword != null && !redisPassword.isEmpty();
    }
}

