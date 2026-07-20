# klite-push

Web Push notifications using VAPID protocol for Klite.

## Setup

### 1. Generate VAPID keys

Run the `VapidKeyPair.main()` method to generate a key pair:

This outputs two lines for your `.env` file:

```
WEB_PUSH_VAPID_PUBLIC_KEY=<public_key>
WEB_PUSH_VAPID_PRIVATE_KEY=<private_key>
```

### 2. Add service worker push handler

```js
addEventListener('push', event => {
  const data = event.data?.json() || {}
  event.waitUntil(
    self.registration.showNotification(data.title || 'App', {
      body: data.body,
      icon: '/icon.png'
    })
  )
})
```

## Usage

### Subscribe a browser

```kotlin
@POST("/push/subscribe")
fun subscribe(sub: PushSubscription, user: User) {
  // Store sub.endpoint, sub.keys.p256dh, sub.keys.auth in database
}
```

### Send push notification

```kotlin
val client = require<WebPushClient>()
val subscription = PushSubscription(endpoint, SubscriptionKeys(p256dh, auth))
client.send(subscription, """{"title":"Hello","body":"World"}""".toByteArray())
```

## Configuration

| Env var | Description |
|---------|-------------|
| `WEB_PUSH_VAPID_PUBLIC_KEY` | Base64url-encoded EC public key (87 chars) |
| `WEB_PUSH_VAPID_PRIVATE_KEY` | Base64url-encoded PKCS8 EC private key |
| `WEB_PUSH_SUB` | JWT `sub` claim, defaults to `mailto:push@klite.dev` |
