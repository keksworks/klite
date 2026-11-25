# klite-email

Provides a way to send plain text or html email over SMTP and read incoming mail over IMAP using *javax.mail*.

Depends on [klite-i18n](../i18n) for translations.

To use it, initialize the correct implementation when creating the Server instance:
```kotlin
register(if (Config.isDev) FakeEmailSender::class else SmtpEmailSender::class)
```

Add approprate content to your translation files, e.g. `en.json`:
```json
{
  "emails": {
    "welcome": {
      "subject": "Welcome to our service",
      "body": "Hello, {name}! Welcome to our service.",
      "action": "Click here to login"
    }
  }
}
```

Then you can send emails like this:
```kotlin
emailSender.send(Email("recipient@hello.ee"), EmailContent("en", "welcome", mapOf("name" to "John"), URI("https://github.com/login")))
```

You can redefine HTML email template by extending `EmailContent` class.

## Reading email over IMAP

Configure the mailbox via `IMAP_HOST`, `IMAP_PORT` (defaults to 993), `IMAP_USER`, `IMAP_PASS` and optional `IMAP_FOLDER` (defaults to `INBOX`)
and `IMAP_TIMEOUT_SEC` (connection, read and write timeouts, defaults to 30).
The timeout can also be passed to the constructor, e.g. `ImapEmailReader(timeout = 1.minutes)`.

```kotlin
val reader = ImapEmailReader()

// process unseen messages, marking each one as seen once the handler has succeeded
reader.process { email ->
  println("${email.from}: ${email.subject}, text=${email.text}, attachments=${email.attachments.keys}")
}

// process all messages that are not deleted, deleting each one after the handler has succeeded
reader.processAndDelete { email -> }

// or move each successfully processed message to another folder (created when missing)
reader.processAndMove("Processed") { email -> }
```

Any failure thrown by the handler stops the processing, leaving the failed message untouched in the folder.

To fetch messages without changing any flags, use `fetch()`, e.g. `reader.fetch(ImapEmailReader.notDeleted)` for all messages
not marked as deleted, including the ones already seen.

Use `ImapEmailReader.useFolder` for lower-level access (e.g. to set custom flags).
