# Web Integration Guide

This guide shows how to integrate GotCraftWeb with various web frameworks and payment processors.

## Table of Contents

1. [Basic Redis Publishing](#basic-redis-publishing)
2. [Node.js + Express + Stripe](#nodejs--express--stripe)
3. [PHP + Laravel + Stripe](#php--laravel--stripe)
4. [Python + Flask + Stripe](#python--flask--stripe)
5. [Direct Stripe Webhook](#direct-stripe-webhook)

---

## Basic Redis Publishing

Any backend that can connect to Redis can send notifications:

```javascript
// Minimal example
const payload = {
    event_type: "payment_success",
    username: playerMinecraftUsername,
    product_name: purchasedProductId,
    amount: paymentAmount,
    transaction_id: stripePaymentIntentId
};

redisClient.publish('gotcraft_notifications', JSON.stringify(payload));
```

---

## Node.js + Express + Stripe

### Installation

```bash
npm install express stripe redis
```

### Complete Example

```javascript
// server.js
const express = require('express');
const stripe = require('stripe')(process.env.STRIPE_SECRET_KEY);
const redis = require('redis');

const app = express();
const redisClient = redis.createClient({
    host: process.env.REDIS_HOST || 'localhost',
    port: process.env.REDIS_PORT || 6379
});

redisClient.connect();

app.use(express.json());

// Product ID to Minecraft product name mapping
const PRODUCT_MAPPING = {
    'prod_vip_rank': 'VIP',
    'prod_super_vip': 'SUPER_VIP',
    'prod_starter_kit': 'StarterKit',
    'prod_monthly_crate': 'MonthlyCrate'
};

// Stripe webhook endpoint
app.post('/webhook/stripe', express.raw({ type: 'application/json' }), async (req, res) => {
    const sig = req.headers['stripe-signature'];
    let event;

    try {
        event = stripe.webhooks.constructEvent(
            req.body,
            sig,
            process.env.STRIPE_WEBHOOK_SECRET
        );
    } catch (err) {
        console.error('Webhook signature verification failed:', err.message);
        return res.status(400).send(`Webhook Error: ${err.message}`);
    }

    // Handle successful payment
    if (event.type === 'checkout.session.completed') {
        const session = event.data.object;
        
        // Get metadata from session
        const minecraftUsername = session.metadata.minecraft_username;
        const productId = session.metadata.product_id;
        const userId = session.metadata.user_id;
        
        if (!minecraftUsername || !productId) {
            console.error('Missing required metadata');
            return res.status(400).send('Missing metadata');
        }

        // Map product ID to Minecraft product name
        const productName = PRODUCT_MAPPING[productId];
        if (!productName) {
            console.error('Unknown product ID:', productId);
            return res.status(400).send('Unknown product');
        }

        // Create payload for Minecraft plugin
        const payload = {
            event_type: 'payment_success',
            purchase_id: session.id,
            user_id: parseInt(userId),
            username: minecraftUsername,
            product_name: productName,
            amount: session.amount_total / 100, // Convert from cents
            status: 'completed',
            transaction_id: session.payment_intent,
            metadata: {
                stripe_session_id: session.id,
                stripe_payment_intent_id: session.payment_intent,
                customer_email: session.customer_email
            }
        };

        // Publish to Redis
        try {
            await redisClient.publish('gotcraft_notifications', JSON.stringify(payload));
            console.log('Published payment notification:', payload);
        } catch (err) {
            console.error('Failed to publish to Redis:', err);
            return res.status(500).send('Redis error');
        }
    }

    // Handle refunds
    if (event.type === 'charge.refunded') {
        const charge = event.data.object;
        
        // You'll need to look up the original purchase in your database
        // to get the Minecraft username and product
        const purchase = await getPurchaseByChargeId(charge.id);
        
        if (purchase) {
            const payload = {
                event_type: 'refund_processed',
                purchase_id: purchase.id,
                user_id: purchase.user_id,
                username: purchase.minecraft_username,
                product_name: purchase.product_name,
                amount: charge.amount_refunded / 100,
                status: 'refunded',
                transaction_id: charge.id
            };

            await redisClient.publish('gotcraft_notifications', JSON.stringify(payload));
            console.log('Published refund notification:', payload);
        }
    }

    res.json({ received: true });
});

// Create checkout session endpoint
app.post('/api/checkout', async (req, res) => {
    const { productId, minecraftUsername, userId } = req.body;

    const productName = PRODUCT_MAPPING[productId];
    if (!productName) {
        return res.status(400).json({ error: 'Invalid product' });
    }

    try {
        const session = await stripe.checkout.sessions.create({
            payment_method_types: ['card'],
            line_items: [{
                price: productId, // Your Stripe Price ID
                quantity: 1,
            }],
            mode: 'payment',
            success_url: `${process.env.CLIENT_URL}/success?session_id={CHECKOUT_SESSION_ID}`,
            cancel_url: `${process.env.CLIENT_URL}/cancel`,
            metadata: {
                minecraft_username: minecraftUsername,
                product_id: productId,
                user_id: userId.toString()
            }
        });

        res.json({ sessionId: session.id });
    } catch (err) {
        console.error('Error creating checkout session:', err);
        res.status(500).json({ error: err.message });
    }
});

app.listen(3000, () => {
    console.log('Server running on port 3000');
});
```

---

## PHP + Laravel + Stripe

### Installation

```bash
composer require stripe/stripe-php
composer require predis/predis
```

### Webhook Controller

```php
<?php
// app/Http/Controllers/StripeWebhookController.php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Redis;
use Stripe\Webhook;
use Stripe\Exception\SignatureVerificationException;

class StripeWebhookController extends Controller
{
    private const PRODUCT_MAPPING = [
        'prod_vip_rank' => 'VIP',
        'prod_super_vip' => 'SUPER_VIP',
        'prod_starter_kit' => 'StarterKit',
        'prod_monthly_crate' => 'MonthlyCrate',
    ];

    public function handle(Request $request)
    {
        $payload = $request->getContent();
        $sigHeader = $request->header('Stripe-Signature');
        $webhookSecret = config('services.stripe.webhook_secret');

        try {
            $event = Webhook::constructEvent($payload, $sigHeader, $webhookSecret);
        } catch (SignatureVerificationException $e) {
            return response()->json(['error' => 'Invalid signature'], 400);
        }

        switch ($event->type) {
            case 'checkout.session.completed':
                $this->handlePaymentSuccess($event->data->object);
                break;

            case 'charge.refunded':
                $this->handleRefund($event->data->object);
                break;
        }

        return response()->json(['status' => 'success']);
    }

    private function handlePaymentSuccess($session)
    {
        $minecraftUsername = $session->metadata->minecraft_username ?? null;
        $productId = $session->metadata->product_id ?? null;
        $userId = $session->metadata->user_id ?? null;

        if (!$minecraftUsername || !$productId) {
            \Log::error('Missing metadata in Stripe session', ['session' => $session->id]);
            return;
        }

        $productName = self::PRODUCT_MAPPING[$productId] ?? null;
        if (!$productName) {
            \Log::error('Unknown product ID', ['product_id' => $productId]);
            return;
        }

        $payload = [
            'event_type' => 'payment_success',
            'purchase_id' => $session->id,
            'user_id' => (int) $userId,
            'username' => $minecraftUsername,
            'product_name' => $productName,
            'amount' => $session->amount_total / 100,
            'status' => 'completed',
            'transaction_id' => $session->payment_intent,
            'metadata' => [
                'stripe_session_id' => $session->id,
                'stripe_payment_intent_id' => $session->payment_intent,
                'customer_email' => $session->customer_email,
            ],
        ];

        Redis::publish('gotcraft_notifications', json_encode($payload));
        \Log::info('Published payment notification', $payload);
    }

    private function handleRefund($charge)
    {
        // Look up purchase in database
        $purchase = \App\Models\Purchase::where('charge_id', $charge->id)->first();

        if ($purchase) {
            $payload = [
                'event_type' => 'refund_processed',
                'purchase_id' => $purchase->id,
                'user_id' => $purchase->user_id,
                'username' => $purchase->minecraft_username,
                'product_name' => $purchase->product_name,
                'amount' => $charge->amount_refunded / 100,
                'status' => 'refunded',
                'transaction_id' => $charge->id,
            ];

            Redis::publish('gotcraft_notifications', json_encode($payload));
            \Log::info('Published refund notification', $payload);
        }
    }
}
```

### Routes

```php
// routes/web.php
Route::post('/webhook/stripe', [StripeWebhookController::class, 'handle']);
```

---

## Python + Flask + Stripe

### Installation

```bash
pip install flask stripe redis
```

### Complete Example

```python
# app.py
from flask import Flask, request, jsonify
import stripe
import redis
import json
import os

app = Flask(__name__)
stripe.api_key = os.getenv('STRIPE_SECRET_KEY')
webhook_secret = os.getenv('STRIPE_WEBHOOK_SECRET')

# Redis connection
redis_client = redis.Redis(
    host=os.getenv('REDIS_HOST', 'localhost'),
    port=int(os.getenv('REDIS_PORT', 6379)),
    decode_responses=True
)

PRODUCT_MAPPING = {
    'prod_vip_rank': 'VIP',
    'prod_super_vip': 'SUPER_VIP',
    'prod_starter_kit': 'StarterKit',
    'prod_monthly_crate': 'MonthlyCrate'
}

@app.route('/webhook/stripe', methods=['POST'])
def stripe_webhook():
    payload = request.data
    sig_header = request.headers.get('Stripe-Signature')

    try:
        event = stripe.Webhook.construct_event(
            payload, sig_header, webhook_secret
        )
    except ValueError as e:
        return jsonify({'error': 'Invalid payload'}), 400
    except stripe.error.SignatureVerificationError as e:
        return jsonify({'error': 'Invalid signature'}), 400

    if event['type'] == 'checkout.session.completed':
        session = event['data']['object']
        handle_payment_success(session)

    elif event['type'] == 'charge.refunded':
        charge = event['data']['object']
        handle_refund(charge)

    return jsonify({'status': 'success'})

def handle_payment_success(session):
    minecraft_username = session.get('metadata', {}).get('minecraft_username')
    product_id = session.get('metadata', {}).get('product_id')
    user_id = session.get('metadata', {}).get('user_id')

    if not minecraft_username or not product_id:
        app.logger.error('Missing metadata in session')
        return

    product_name = PRODUCT_MAPPING.get(product_id)
    if not product_name:
        app.logger.error(f'Unknown product ID: {product_id}')
        return

    payload = {
        'event_type': 'payment_success',
        'purchase_id': session['id'],
        'user_id': int(user_id) if user_id else 0,
        'username': minecraft_username,
        'product_name': product_name,
        'amount': session['amount_total'] / 100,
        'status': 'completed',
        'transaction_id': session.get('payment_intent', ''),
        'metadata': {
            'stripe_session_id': session['id'],
            'stripe_payment_intent_id': session.get('payment_intent', ''),
            'customer_email': session.get('customer_email', '')
        }
    }

    redis_client.publish('gotcraft_notifications', json.dumps(payload))
    app.logger.info(f'Published payment notification: {payload}')

def handle_refund(charge):
    # Look up purchase in database to get minecraft_username and product_name
    # This is just a placeholder - implement based on your database
    purchase = get_purchase_by_charge_id(charge['id'])
    
    if purchase:
        payload = {
            'event_type': 'refund_processed',
            'purchase_id': purchase['id'],
            'user_id': purchase['user_id'],
            'username': purchase['minecraft_username'],
            'product_name': purchase['product_name'],
            'amount': charge['amount_refunded'] / 100,
            'status': 'refunded',
            'transaction_id': charge['id']
        }

        redis_client.publish('gotcraft_notifications', json.dumps(payload))
        app.logger.info(f'Published refund notification: {payload}')

if __name__ == '__main__':
    app.run(port=3000)
```

---

## Environment Variables

Create a `.env` file:

```env
# Stripe
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# App
CLIENT_URL=http://localhost:3000
```

---

## Testing Webhooks Locally

Use Stripe CLI to forward webhooks to your local server:

```bash
# Install Stripe CLI
brew install stripe/stripe-cli/stripe

# Login
stripe login

# Forward webhooks
stripe listen --forward-to localhost:3000/webhook/stripe
```

This will give you a webhook secret like `whsec_...` - use this in your `.env`.

---

## Best Practices

1. **Always verify webhook signatures** - Never trust webhook data without verification
2. **Store purchases in database** - You'll need this for refunds and support
3. **Handle idempotency** - Stripe may send the same webhook multiple times
4. **Log everything** - Make debugging easier
5. **Validate Minecraft usernames** - Check if they exist before accepting payment
6. **Use metadata** - Store all necessary data in Stripe metadata
7. **Test thoroughly** - Use Stripe test mode and test all scenarios

---

## Security Checklist

- [ ] Webhook endpoint uses HTTPS in production
- [ ] Webhook signatures are verified
- [ ] Redis connection is secured (password, firewall)
- [ ] Sensitive keys are in environment variables, not code
- [ ] Error messages don't leak sensitive information
- [ ] Rate limiting is implemented on endpoints
- [ ] Minecraft usernames are validated before purchase

