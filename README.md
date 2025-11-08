# GotCraftWeb

A professional Paper/Spigot plugin that integrates Minecraft servers with web payment systems via Redis pub/sub. Automatically execute in-game commands when players make purchases on your website.

## 🎯 Features

- **Redis Pub/Sub Integration** - Real-time notifications from web applications
- **Flexible Command Execution** - Map events and products to custom command lists
- **Placeholder Support** - Dynamic command generation with player and payment data
- **Async Processing** - Non-blocking Redis subscription on separate thread
- **Configurable** - Easy YAML configuration with hot-reload support
- **Debug Tools** - Built-in commands to view mappings and connection status
- **Error Handling** - Graceful fallback if Redis is unavailable

## 📦 Installation

1. Download the plugin JAR file
2. Place it in your server's `plugins/` folder
3. Install and start Redis server (if not already running)
4. Start/restart your Minecraft server
5. Configure `plugins/GotCraftWeb/config.yml`
6. Run `/gotcraftweb reload` to apply changes

## ⚙️ Configuration

### Redis Settings

```yaml
redis:
  host: "localhost"      # Redis server address
  port: 6379            # Redis server port
  password: ""          # Redis password (optional)
  channel: "gotcraft_notifications"  # Channel to subscribe to
  timeout: 2000         # Connection timeout in ms
```

### Event Mappings

Map event types and product names to lists of commands:

```yaml
events:
  payment_success:
    VIP:
      - "lp user {player} parent add vip"
      - "give {player} diamond 10"
      - "broadcast &a{player} purchased VIP!"
    
    SUPER_VIP:
      - "lp user {player} parent add supervip"
      - "give {player} nether_star 1"
```

### Available Placeholders

| Placeholder | Description |
|-------------|-------------|
| `{player}` or `{username}` | Player's username |
| `{product_name}` | Name of purchased product |
| `{amount}` | Purchase amount |
| `{quantity}` | Quantity of items purchased |
| `{transaction_id}` | Payment transaction ID |
| `{purchase_id}` | Internal purchase ID |
| `{user_id}` | User's ID |
| `{status}` | Payment status |
| `{stripe_session_id}` | Stripe session ID |
| `{stripe_payment_intent_id}` | Stripe payment intent ID |
| `{metadata.<key>}` | Any custom metadata value |

Color codes are supported using `&` (e.g., `&a` for green, `&c` for red).

## 📡 JSON Payload Format

Your web application should publish JSON messages to the configured Redis channel:

```json
{
  "event_type": "payment_success",
  "purchase_id": 1234,
  "user_id": 5678,
  "username": "Steve123",
  "product_name": "VIP",
  "amount": 19.99,
  "quantity": 1,
  "status": "completed",
  "transaction_id": "pi_123456789",
  "metadata": {
    "stripe_session_id": "sess_123",
    "stripe_payment_intent_id": "pi_987"
  }
}
```

### Required Fields

- `event_type` - Type of event (e.g., "payment_success", "refund_processed")
- `product_name` - Product identifier matching your config
- `username` - Minecraft player username

### Optional Fields

All other fields are optional but can be used in command placeholders.

## 🎮 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/gotcraftweb help` | `gotcraftweb.use` | Show help message |
| `/gotcraftweb reload` | `gotcraftweb.reload` | Reload configuration |
| `/gotcraftweb status` | `gotcraftweb.status` | Show connection status |
| `/gotcraftweb debug` | `gotcraftweb.debug` | List all event mappings |

Alias: `/gcw`

## 🔐 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `gotcraftweb.*` | op | All permissions |
| `gotcraftweb.use` | op | Use base command |
| `gotcraftweb.reload` | op | Reload config |
| `gotcraftweb.status` | op | View status |
| `gotcraftweb.debug` | op | View debug info |

## 🏗️ Architecture

### Project Structure

```
GotCraftWeb/
├─ src/main/java/me/lubomirstankov/gotCraftWeb/
│  ├─ GotCraftWeb.java                  # Main plugin class
│  ├─ command/
│  │  └─ GotCraftWebCommand.java        # Command handler
│  ├─ config/
│  │  └─ ConfigManager.java             # Configuration management
│  ├─ listener/
│  │  └─ RedisNotificationListener.java # Redis pub/sub listener
│  ├─ service/
│  │  ├─ RedisService.java              # Redis connection & subscription
│  │  └─ NotificationService.java       # Event processing & command execution
│  ├─ model/
│  │  └─ NotificationPayload.java       # Event payload structure
│  └─ util/
│     └─ DIContainer.java               # Dependency injection
└─ src/main/resources/
   ├─ plugin.yml                         # Plugin metadata
   └─ config.yml                         # Default configuration
```

### Component Overview

- **ConfigManager**: Loads and manages all configuration settings
- **RedisService**: Handles Redis connection, pooling, and subscription lifecycle
- **NotificationService**: Parses JSON payloads and executes mapped commands
- **RedisNotificationListener**: JedisPubSub implementation that forwards messages
- **DIContainer**: Simple dependency injection for service management

### Thread Safety

- Redis subscription runs on a separate daemon thread to avoid blocking the main server thread
- Command execution is scheduled on the main Bukkit thread for thread safety
- Automatic reconnection if Redis connection is lost

## 🔧 Development

### Building from Source

```bash
git clone <repository-url>
cd GotCraftWeb
mvn clean package
```

The compiled JAR will be in `target/GotCraftWeb-1.0-SNAPSHOT.jar`.

### Dependencies

- **Paper API 1.21** (provided by server)
- **Jedis 5.1.0** (Redis client, shaded)
- **Gson 2.10.1** (JSON parsing, shaded)

### Testing Redis Connection

You can test publishing to Redis using `redis-cli`:

```bash
redis-cli
> PUBLISH gotcraft_notifications '{"event_type":"payment_success","username":"TestPlayer","product_name":"VIP","amount":19.99,"transaction_id":"test_123"}'
```

## 📝 Example Use Cases

### 1. Rank Purchases

```yaml
events:
  payment_success:
    VIP_RANK:
      - "lp user {player} parent add vip"
      - "broadcast &a{player} is now VIP!"
```

### 2. Crate Keys

```yaml
events:
  payment_success:
    LEGENDARY_KEY:
      - "crate give {player} legendary 1"
      - "tell {player} &aYou received a Legendary Key!"
```

### 3. Refund Handling

```yaml
events:
  refund_processed:
    VIP_RANK:
      - "lp user {player} parent remove vip"
      - "tell {player} &cYour VIP rank has been revoked."
```

### 4. Subscription Management

```yaml
events:
  subscription_created:
    MONTHLY_PREMIUM:
      - "lp user {player} parent add premium"
      - "broadcast &a{player} subscribed to Premium!"
  
  subscription_cancelled:
    MONTHLY_PREMIUM:
      - "lp user {player} parent remove premium"
      - "tell {player} &cYour subscription has ended."
```

## 🐛 Troubleshooting

### Redis Connection Failed

1. Verify Redis is running: `redis-cli ping` should return `PONG`
2. Check host/port in config.yml
3. Verify firewall allows connection
4. Check Redis password if required

### Commands Not Executing

1. Run `/gotcraftweb debug` to verify mappings are loaded
2. Check server logs for errors
3. Verify `event_type` and `product_name` match exactly (case-sensitive)
4. Test command manually to ensure it's valid

### Player Not Found

- Ensure the player is online when the event is received
- Use `tell` instead of `broadcast` for player-specific messages
- Consider using delayed execution for offline players

## 📄 License

[Your License Here]

## 👤 Author

**lubomirstankov**

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

## ⭐ Support

If you find this plugin useful, please consider giving it a star!

