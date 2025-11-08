package me.lubomirstankov.gotCraftWeb.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import me.lubomirstankov.gotCraftWeb.GotCraftWeb;
import me.lubomirstankov.gotCraftWeb.config.ConfigManager;
import me.lubomirstankov.gotCraftWeb.model.NotificationPayload;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.logging.Logger;

/**
 * Handles parsing of notification payloads and execution of mapped commands.
 * This service runs commands on the main server thread for thread safety.
 */
public class NotificationService {

    private final GotCraftWeb plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    private final Gson gson;

    public NotificationService(GotCraftWeb plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
        this.gson = new Gson();
    }

    /**
     * Processes a raw JSON message from Redis.
     * This method can be called from any thread as it schedules work on the main thread.
     *
     * @param jsonMessage The raw JSON message string
     */
    public void processMessage(String jsonMessage) {
        try {
            // Parse JSON to payload object
            NotificationPayload payload = gson.fromJson(jsonMessage, NotificationPayload.class);

            if (payload == null) {
                logger.warning("Failed to parse notification payload: null result");
                return;
            }

            // Log received event
            logger.info("Received event: " + payload.getEventType() +
                       " | Product: " + payload.getProductName() +
                       " | User: " + payload.getUsername() +
                       " | Amount: $" + payload.getAmount());

            // Validate required fields
            if (payload.getEventType() == null || payload.getProductName() == null) {
                logger.warning("Payload missing required fields (event_type or product_name)");
                return;
            }

            // Get commands for this event + product combination
            List<String> commands = configManager.getCommands(
                payload.getEventType(),
                payload.getProductName()
            );

            if (commands.isEmpty()) {
                logger.info("No commands configured for event_type='" + payload.getEventType() +
                           "' product_name='" + payload.getProductName() + "'");
                return;
            }

            // Execute commands on the main server thread
            executeCommands(payload, commands);

        } catch (JsonSyntaxException e) {
            logger.severe("Failed to parse JSON payload: " + e.getMessage());
            logger.severe("Raw message: " + jsonMessage);
        } catch (Exception e) {
            logger.severe("Error processing notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes a list of commands with placeholder replacement.
     * Runs on the main server thread for thread safety.
     *
     * @param payload The notification payload containing data for placeholders
     * @param commands List of commands to execute
     */
    private void executeCommands(NotificationPayload payload, List<String> commands) {
        // Schedule to run on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String command : commands) {
                try {
                    // Replace placeholders
                    String processedCommand = replacePlaceholders(command, payload);

                    // Log command execution
                    logger.info("Executing command: " + processedCommand);

                    // Execute as console
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);

                } catch (Exception e) {
                    logger.severe("Error executing command '" + command + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Replaces placeholders in a command string with values from the payload.
     * Supports color codes with & symbol.
     *
     * @param command The command template with placeholders
     * @param payload The payload containing replacement values
     * @return The processed command with placeholders replaced
     */
    private String replacePlaceholders(String command, NotificationPayload payload) {
        String result = command;

        // Replace basic placeholders
        result = result.replace("{player}", safeString(payload.getUsername()));
        result = result.replace("{username}", safeString(payload.getUsername()));
        result = result.replace("{product_name}", safeString(payload.getProductName()));
        result = result.replace("{amount}", safeString(payload.getAmount()));
        result = result.replace("{transaction_id}", safeString(payload.getTransactionId()));
        result = result.replace("{status}", safeString(payload.getStatus()));
        result = result.replace("{purchase_id}", safeString(payload.getPurchaseId()));
        result = result.replace("{user_id}", safeString(payload.getUserId()));

        // Replace metadata placeholders
        result = result.replace("{stripe_session_id}",
            safeString(payload.getMetadataValue("stripe_session_id")));
        result = result.replace("{stripe_payment_intent_id}",
            safeString(payload.getMetadataValue("stripe_payment_intent_id")));

        // Replace any other metadata with format {metadata.key}
        if (payload.getMetadata() != null) {
            for (String key : payload.getMetadata().keySet()) {
                result = result.replace("{metadata." + key + "}",
                    safeString(payload.getMetadata().get(key)));
            }
        }

        // Process color codes
        result = ChatColor.translateAlternateColorCodes('&', result);

        return result;
    }

    /**
     * Safely converts an object to string, returning empty string for null.
     *
     * @param obj The object to convert
     * @return String representation or empty string
     */
    private String safeString(Object obj) {
        return obj != null ? obj.toString() : "";
    }
}

