package klite.email

import java.io.InputStream
import java.time.Instant
import javax.mail.Address
import javax.mail.Message
import javax.mail.Message.RecipientType
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility

data class EmailMessage(
  val from: Named,
  val to: List<Named>,
  val cc: List<Named> = emptyList(),
  val subject: String,
  val receivedAt: Instant,
  val text: String? = null,
  val html: String? = null,
  val attachments: Map<String, ByteArray> = emptyMap(),
  val id: String?,
  /** All raw message headers, keyed by lower-cased name */
  val headers: Map<String, List<String>> = emptyMap(),
) {
  val replyTo: Named? get() = rawHeader("Reply-To")?.let { InternetAddress.parse(it).firstOrNull() }?.toNamed()

  /** First value of the header, with RFC 2047 encoded words decoded */
  fun header(name: String) = rawHeader(name)?.let(::decodeHeader)

  private fun rawHeader(name: String) = headers[name.lowercase()]?.firstOrNull()
}

fun Message.toEmailMessage(): EmailMessage {
  val text = StringBuilder()
  val html = StringBuilder()
  val attachments = mutableMapOf<String, ByteArray>()
  collectBody(text, html, attachments)
  val headers = allHeadersByName()
  return EmailMessage(
    from.first().toNamed(),
    recipientsOfType(RecipientType.TO),
    recipientsOfType(RecipientType.CC),
    subject,
    receivedDate?.toInstant() ?: Instant.now(),
    text.toString().takeIf { it.isNotEmpty() },
    html.toString().takeIf { it.isNotEmpty() },
    attachments,
    headers["message-id"]?.firstOrNull(),
    headers,
  )
}

private fun Part.allHeadersByName() = allHeaders.toList().groupBy({ it.name.lowercase() }, { it.value })

private fun Message.recipientsOfType(type: RecipientType) = getRecipients(type).addresses

private val Array<Address>?.addresses get() = this?.filterIsInstance<InternetAddress>()?.map { it.toNamed() } ?: emptyList()

private fun Part.collectBody(text: StringBuilder, html: StringBuilder, attachments: MutableMap<String, ByteArray>) {
  when {
    isMimeType("multipart/*") -> {
      val content = content as Multipart
      for (i in 0 until content.count) content.getBodyPart(i).collectBody(text, html, attachments)
    }
    fileName != null || disposition == Part.ATTACHMENT -> attachments.add(fileName ?: "attachment", readBytes())
    isMimeType("text/html") -> html.append(contentAsString())
    isMimeType("text/*") -> text.append(contentAsString())
  }
}

private fun MutableMap<String, ByteArray>.add(name: String, content: ByteArray) {
  val extensionAt = name.lastIndexOf('.').takeIf { it > 0 }
  val base = if (extensionAt == null) name else name.substring(0, extensionAt)
  val extension = if (extensionAt == null) "" else name.substring(extensionAt)
  var uniqueName = name
  var i = 0
  while (containsKey(uniqueName)) uniqueName = "$base-${++i}$extension"
  this[uniqueName] = content
}

private fun Part.contentAsString() = content.let { if (it is String) it else readBytes().toString(Charsets.UTF_8) }

/** Decodes RFC 2047 encoded words (e.g. `=?UTF-8?B?VGVyZQ==?=`) in a raw header value, returning it as is when decoding fails */
fun decodeHeader(value: String) = if (value.contains("=?")) runCatching { MimeUtility.decodeText(value) }.getOrDefault(value) else value

private fun Part.readBytes(): ByteArray = when (val content = content) {
  is ByteArray -> content
  is String -> content.toByteArray()
  is InputStream -> content.readBytes()
  else -> inputStream.readBytes()
}
