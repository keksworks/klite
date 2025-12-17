package klite.smtp

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toStartWith
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import klite.Config
import klite.MimeTypes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class SmtpEmailSenderTest {
  init { Config.useEnvFile() }
  val session = mockk<Session>(relaxed = true)
  val sender = SmtpEmailSender(session = session)

  @Test fun defaultFrom() {
    val from = sender.defaultFrom
    expect(from.address).toEqual("klite@azib.net")
  }

  @Test fun `send plain text`() = runTest {
    sender.send(email, subject = "Subject", body = "Body")
    val message = slot<MimeMessage>()
    val toAddress = InternetAddress(email.value)
    verify { session.getTransport(toAddress).sendMessage(capture(message), arrayOf(toAddress)) }
    expect(message.captured.from.toList()).toContainExactly(sender.defaultFrom)
    expect(message.captured.subject).toEqual("Subject")
    expect(message.captured.contentType).toEqual("text/plain; charset=UTF-8")
    expect(message.captured.content).toEqual("Body")
  }

  @Test fun `send html`() = runTest {
    sender.send(email, subject = "Subject", body = "<Body>", bodyMimeType = MimeTypes.html)
    val message = slot<MimeMessage>()
    val toAddress = InternetAddress(email.value)
    verify { session.getTransport(toAddress).sendMessage(capture(message), arrayOf(toAddress)) }
    expect(message.captured.contentType).toEqual("text/html; charset=UTF-8")
    expect(message.captured.content).toEqual("<Body>")
  }

  @Test fun `send with attachment`() = runTest {
    sender.send(email, subject = "Subject", body = "Body", attachments = mapOf("hello.pdf" to ByteArray(0)))
    val message = slot<MimeMessage>()
    val toAddress = InternetAddress(email.value)
    verify { session.getTransport(toAddress).sendMessage(capture(message), arrayOf(toAddress)) }
    expect(message.captured.from.size).toEqual(1)
    expect(message.captured.subject).toEqual("Subject")
    expect(message.captured.getHeader("Content-Type")[0]).toStartWith("multipart/mixed")
    val multipart = (message.captured.content as MimeMultipart)
    expect(multipart.getBodyPart(0).contentType).toEqual("text/plain; charset=UTF-8")
    expect(multipart.getBodyPart(0).content).toEqual("Body")
    expect(multipart.getBodyPart(1).contentType).toEqual("application/pdf; name=hello.pdf")
  }
}
