package me.lubomirstankov.gotCraftWeb.listener;

import me.lubomirstankov.gotCraftWeb.service.NotificationService;
import redis.clients.jedis.JedisPubSub;

/**
 * Redis pub/sub listener that receives messages and forwards them to NotificationService.
 * Extends JedisPubSub to handle Redis subscription events.
 */
public class RedisNotificationListener extends JedisPubSub {

    private final NotificationService notificationService;

    public RedisNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Called when a message is received on a subscribed channel.
     * This runs on the Redis subscription thread, not the main server thread.
     *
     * @param channel The channel that received the message
     * @param message The message content (JSON payload)
     */
    @Override
    public void onMessage(String channel, String message) {
        // Forward to notification service for processing
        notificationService.processMessage(message);
    }

    /**
     * Called when successfully subscribed to a channel.
     *
     * @param channel The channel subscribed to
     * @param subscribedChannels Total number of subscribed channels
     */
    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        System.out.println("[GotCraftWeb] Successfully subscribed to Redis channel: " + channel);
    }

    /**
     * Called when unsubscribed from a channel.
     *
     * @param channel The channel unsubscribed from
     * @param subscribedChannels Remaining subscribed channels
     */
    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        System.out.println("[GotCraftWeb] Unsubscribed from Redis channel: " + channel);
    }
}

