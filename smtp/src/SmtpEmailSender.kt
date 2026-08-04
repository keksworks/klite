package klite.smtp

import klite.*
import klite.i18n.Lang
import klite.i18n.Lang.translate
import java.util.*
import javax.activation.DataHandler
import javax.mail.*
import javax.mail.Message.RecipientType.*
import javax.mail.Session
import javax.mail.internet.*
import javax.mail.util.ByteArrayDataSource

open class SmtpEmailSender(
  smtpUser: String? = Config.optional("SMTP_USER"),
  smtpPort: String? = Config.optional("SMTP_PORT", "25"),
  props: Properties = Properties().apply {
    this["mail.smtp.host"] = Config.optional("SMTP_HOST", "localhost")
    this["mail.smtp.port"] = smtpPort
    this["mail.smtp.starttls.enable"] = smtpPort == "587"
    this["mail.smtp.auth"] = smtpUser != null
    this["mail.smtp.ssl.protocols"] = "TLSv1.2"
  },
  private val authenticator: Authenticator = object: Authenticator() {
    override fun getPasswordAuthentication() = PasswordAuthentication(smtpUser, Config.required("SMTP_PASS"))
  },
  private val session: Session = Session.getInstance(props, authenticator.takeIf { smtpUser != null }),
): EmailSender {
  val defaultFrom = InternetAddress(Config["MAIL_FROM"], Config.optional("MAIL_FROM_NAME", translate(Lang.available.first(), "title")))
  val bccTo = Config.optional("MAIL_BCC_TO")?.let { InternetAddress(it) }
  private val log = logger()

  override fun send(to: Email, subject: String, body: String, bodyMimeType: String, attachments: Map<String, ByteArray>,
                    cc: List<Email>, bcc: List<Email>, from: InternetAddress?) {
    send(to, subject, from ?: defaultFrom) {
      cc.forEach { setRecipient(CC, InternetAddress(it.value)) }
      bcc.forEach { setRecipient(BCC, InternetAddress(it.value)) }
      if (attachments.isEmpty())
        setBody(body, bodyMimeType)
      else
        setContent(MimeMultipart().apply {
          addBodyPart(MimeBodyPart().apply { setBody(body, bodyMimeType) })
          attachments.forEach {
            addBodyPart(MimeBodyPart().apply {
              dataHandler = DataHandler(ByteArrayDataSource(it.value, MimeTypes.typeFor(it.key)))
              fileName = it.key
              disposition = Part.ATTACHMENT
              setHeader("Content-ID", UUID.randomUUID().toString())
            })
          }
        })
    }
  }

  private fun MimePart.setBody(body: String, bodyMimeType: String) = setContent(body, MimeTypes.withCharset(bodyMimeType))

  protected open fun send(to: Email, subject: String, from: InternetAddress, block: MimeMessage.() -> Unit) = MimeMessage(session).apply {
    try {
      setFrom(from)
      if (bccTo != null) setRecipient(BCC, bccTo)
      setRecipient(TO, InternetAddress(to.value))
      setSubject(subject, MimeTypes.textCharset.name())
      block()
      Transport.send(this)
      log.info("Email sent to $to, subject=$subject")
    } catch (e: Exception) {
      log.error("Failed to send to $to, subject=$subject: $e")
      throw e
    }
  }
}
