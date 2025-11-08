package me.lubomirstankov.gotCraftWeb.service;

import me.lubomirstankov.gotCraftWeb.GotCraftWeb;
import me.lubomirstankov.gotCraftWeb.config.ConfigManager;
import me.lubomirstankov.gotCraftWeb.listener.RedisNotificationListener;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.logging.Logger;

/**
 * Manages Redis connection and pub/sub operations.
 * Handles connection pooling and async subscription.
 */
public class RedisService {

    private final GotCraftWeb plugin;
    private final ConfigManager configManager;
    private final Logger logger;

    private JedisPool jedisPool;
    private RedisNotificationListener redisListener;
    private Thread subscriptionThread;
    private volatile boolean running = false;

    public RedisService(GotCraftWeb plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Connects to Redis and initializes the connection pool.
     *
     * @return true if connection successful, false otherwise
     */
    public boolean connect() {
        try {
            logger.info("Connecting to Redis at " + configManager.getRedisHost() + ":" + configManager.getRedisPort());

            // Configure connection pool
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            // Create Jedis pool with or without password
            if (configManager.hasRedisPassword()) {
                jedisPool = new JedisPool(
                    poolConfig,
                    configManager.getRedisHost(),
                    configManager.getRedisPort(),
                    configManager.getRedisTimeout(),
                    configManager.getRedisPassword()
                );
            } else {
                jedisPool = new JedisPool(
                    poolConfig,
                    configManager.getRedisHost(),
                    configManager.getRedisPort(),
                    configManager.getRedisTimeout()
                );
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                String response = jedis.ping();
                if (!"PONG".equals(response)) {
                    logger.severe("Redis ping failed: " + response);
                    return false;
                }
            }

            logger.info("Successfully connected to Redis");
            return true;

        } catch (JedisConnectionException e) {
            logger.severe("Failed to connect to Redis: " + e.getMessage());
            logger.severe("Please check your Redis configuration and ensure Redis is running");
            return false;
        } catch (Exception e) {
            logger.severe("Unexpected error connecting to Redis: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Starts subscribing to the configured Redis channel in a separate thread.
     *
     * @param notificationService The service to handle received messages
     */
    public void startSubscription(NotificationService notificationService) {
        if (jedisPool == null) {
            logger.severe("Cannot start subscription: Redis not connected");
            return;
        }

        if (running) {
            logger.warning("Redis subscription already running");
            return;
        }

        running = true;
        redisListener = new RedisNotificationListener(notificationService);

        // Create subscription thread to avoid blocking main thread
        subscriptionThread = new Thread(() -> {
            while (running) {
                try (Jedis jedis = jedisPool.getResource()) {
                    logger.info("Subscribing to Redis channel: " + configManager.getRedisChannel());

                    // This blocks until unsubscribe is called
                    jedis.subscribe(redisListener, configManager.getRedisChannel());

                } catch (JedisConnectionException e) {
                    if (running) {
                        logger.warning("Redis connection lost, attempting to reconnect in 5 seconds...");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        logger.severe("Error in Redis subscription: " + e.getMessage());
                        e.printStackTrace();
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            logger.info("Redis subscription thread terminated");
        }, "GotCraftWeb-Redis-Subscriber");

        subscriptionThread.setDaemon(true);
        subscriptionThread.start();

        logger.info("Redis subscription started successfully");
    }

    /**
     * Stops the Redis subscription and closes connections.
     */
    public void disconnect() {
        running = false;

        // Unsubscribe from channels
        if (redisListener != null && redisListener.isSubscribed()) {
            logger.info("Unsubscribing from Redis channel...");
            redisListener.unsubscribe();
        }

        // Wait for subscription thread to finish
        if (subscriptionThread != null && subscriptionThread.isAlive()) {
            try {
                subscriptionThread.join(3000);
                if (subscriptionThread.isAlive()) {
                    logger.warning("Redis subscription thread did not terminate gracefully");
                    subscriptionThread.interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Close connection pool
        if (jedisPool != null && !jedisPool.isClosed()) {
            logger.info("Closing Redis connection pool...");
            jedisPool.close();
            jedisPool = null;
        }

        logger.info("Disconnected from Redis");
    }

    /**
     * Checks if Redis is connected and subscription is running.
     *
     * @return true if connected and subscribed
     */
    public boolean isConnected() {
        return jedisPool != null && !jedisPool.isClosed() && running;
    }

    /**
     * Gets the Jedis pool for direct Redis operations (advanced usage).
     *
     * @return The Jedis connection pool
     */
    public JedisPool getJedisPool() {
        return jedisPool;
    }
}

