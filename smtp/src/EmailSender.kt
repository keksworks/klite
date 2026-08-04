package klite.smtp

import klite.Email
import klite.MimeTypes
import klite.info
import klite.logger
import javax.mail.internet.InternetAddress

interface EmailSender {
  fun send(to: Email, subject: String, body: String, bodyMimeType: String = MimeTypes.text, attachments: Map<String, ByteArray> = emptyMap(),
           cc: List<Email> = emptyList(), bcc: List<Email> = emptyList(), from: InternetAddress? = null)

  fun send(to: Email, content: EmailContent, attachments: Map<String, ByteArray> = emptyMap(), cc: List<Email> = emptyList(), bcc: List<Email> = emptyList()) =
    send(to, content.subject, content.fullHtml(), MimeTypes.html, attachments, cc, bcc, content.from)
}

open class FakeEmailSender: EmailSender {
  private val log = logger()
  lateinit var lastSentEmail: String

  override fun send(to: Email, subject: String, body: String, bodyMimeType: String, attachments: Map<String, ByteArray>, cc: List<Email>, bcc: List<Email>, from: InternetAddress?) {
    lastSentEmail = """
      Email to $to, CC: $cc, BCC: $bcc
      Subject: $subject
      Body ($bodyMimeType): $body
      ${if (attachments.isNotEmpty()) "Attachments: ${attachments.keys}" else ""}
    """
    log.info(lastSentEmail)
  }
}
