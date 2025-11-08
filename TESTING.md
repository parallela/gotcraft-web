# Testing Guide for GotCraftWeb

This guide will help you test the GotCraftWeb plugin locally.

## Prerequisites

1. Paper/Spigot server running
2. Redis server installed and running
3. GotCraftWeb plugin installed

## Step 1: Install Redis

### macOS (using Homebrew)
```bash
brew install redis
brew services start redis
```

### Ubuntu/Debian
```bash
sudo apt-get update
sudo apt-get install redis-server
sudo systemctl start redis-server
```

### Windows
Download from: https://github.com/microsoftarchive/redis/releases

## Step 2: Verify Redis is Running

```bash
redis-cli ping
```

Expected output: `PONG`

## Step 3: Configure the Plugin

Edit `plugins/GotCraftWeb/config.yml`:

```yaml
redis:
  host: "localhost"
  port: 6379
  password: ""
  channel: "gotcraft_notifications"
  timeout: 2000

events:
  payment_success:
    VIP:
      - "say {player} just purchased VIP for ${amount}!"
      - "give {player} diamond 10"
```

## Step 4: Reload the Plugin

In-game or console:
```
/gotcraftweb reload
```

## Step 5: Test Publishing Events

### Option 1: Using redis-cli

```bash
redis-cli
```

Then in the Redis CLI:

```redis
PUBLISH gotcraft_notifications '{"event_type":"payment_success","username":"YourMinecraftUsername","product_name":"VIP","amount":19.99,"transaction_id":"test_12345","purchase_id":1,"user_id":100,"status":"completed"}'
```

### Option 2: Using Python Script

Create `test_publisher.py`:

```python
import redis
import json
import time

# Connect to Redis
r = redis.Redis(host='localhost', port=6379, decode_responses=True)

# Test payload
payload = {
    "event_type": "payment_success",
    "purchase_id": 1234,
    "user_id": 5678,
    "username": "Steve123",  # Change to your Minecraft username
    "product_name": "VIP",
    "amount": 19.99,
    "status": "completed",
    "transaction_id": "pi_123456789",
    "metadata": {
        "stripe_session_id": "sess_123",
        "stripe_payment_intent_id": "pi_987"
    }
}

# Publish to channel
channel = "gotcraft_notifications"
message = json.dumps(payload)

print(f"Publishing to channel '{channel}':")
print(message)

result = r.publish(channel, message)
print(f"\nSubscribers received: {result}")
```

Run it:
```bash
pip install redis
python test_publisher.py
```

### Option 3: Using Node.js Script

Create `test_publisher.js`:

```javascript
const redis = require('redis');

const client = redis.createClient({
    host: 'localhost',
    port: 6379
});

client.on('connect', () => {
    console.log('Connected to Redis');
    
    const payload = {
        event_type: 'payment_success',
        purchase_id: 1234,
        user_id: 5678,
        username: 'Steve123', // Change to your Minecraft username
        product_name: 'VIP',
        amount: 19.99,
        status: 'completed',
        transaction_id: 'pi_123456789',
        metadata: {
            stripe_session_id: 'sess_123',
            stripe_payment_intent_id: 'pi_987'
        }
    };
    
    const channel = 'gotcraft_notifications';
    const message = JSON.stringify(payload);
    
    console.log(`Publishing to channel '${channel}':`);
    console.log(message);
    
    client.publish(channel, message, (err, reply) => {
        if (err) {
            console.error('Error:', err);
        } else {
            console.log(`Subscribers received: ${reply}`);
        }
        client.quit();
    });
});

client.connect();
```

Run it:
```bash
npm install redis
node test_publisher.js
```

## Step 6: Verify in Minecraft

1. Make sure you're logged into the server as the player specified in `username`
2. Watch the server console for log messages
3. Watch the in-game chat for command execution results
4. Check your inventory for items given by commands

## Step 7: Use Debug Commands

Check plugin status:
```
/gotcraftweb status
```

View all event mappings:
```
/gotcraftweb debug
```

## Expected Console Output

When working correctly, you should see:

```
[GotCraftWeb] Successfully subscribed to Redis channel: gotcraft_notifications
[GotCraftWeb] Received event: payment_success | Product: VIP | User: Steve123 | Amount: $19.99
[GotCraftWeb] Executing command: say Steve123 just purchased VIP for $19.99!
[GotCraftWeb] Executing command: give Steve123 diamond 10
```

## Common Test Scenarios

### 1. Test Different Products

```json
{"event_type":"payment_success","username":"TestPlayer","product_name":"SUPER_VIP","amount":49.99}
```

### 2. Test Placeholder Replacement

```json
{"event_type":"payment_success","username":"TestPlayer","product_name":"VIP","amount":19.99,"transaction_id":"txn_abc123","metadata":{"stripe_session_id":"sess_xyz"}}
```

### 3. Test Missing Product (should log "No commands configured")

```json
{"event_type":"payment_success","username":"TestPlayer","product_name":"NONEXISTENT_PRODUCT"}
```

### 4. Test Refund Event

```json
{"event_type":"refund_processed","username":"TestPlayer","product_name":"VIP"}
```

## Troubleshooting

### No Commands Execute

1. Check server logs for errors
2. Verify player is online
3. Run `/gotcraftweb debug` to see if mappings loaded
4. Ensure `event_type` and `product_name` match config exactly

### Redis Connection Error

1. Verify Redis is running: `redis-cli ping`
2. Check config.yml has correct host/port
3. Check firewall settings
4. Try: `/gotcraftweb reload`

### Commands Execute But Don't Work

1. Test the command manually in console
2. Check for typos in command syntax
3. Verify placeholders are being replaced (check logs)

## Performance Testing

Send multiple events rapidly:

```bash
for i in {1..10}; do
  redis-cli PUBLISH gotcraft_notifications '{"event_type":"payment_success","username":"TestPlayer","product_name":"VIP","amount":19.99}'
  sleep 1
done
```

All events should be processed without blocking the server.

