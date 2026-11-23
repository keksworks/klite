package klite.email

import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import klite.Config
import klite.Email
import klite.MimeTypes
import org.junit.jupiter.api.Test
import java.util.*
import javax.mail.*
import javax.mail.Flags.Flag.DELETED
import javax.mail.Folder.HOLDS_MESSAGES
import javax.mail.Message.RecipientType.CC
import javax.mail.Message.RecipientType.TO
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class ImapEmailReaderTest {
  init { Config.useEnvFile() }
  val session = mockk<Session>(relaxed = true)
  val store = mockk<Store>(relaxed = true)
  val folder = mockk<Folder>(relaxed = true)
  val reader = ImapEmailReader(session = session)

  init {
    every { session.store } returns store
    every { store.getFolder(any<String>()) } returns folder
    every { folder.search(any()) } returns emptyArray()
  }

  @Test fun `can be created with default config`() {
    expect(ImapEmailReader(session = session)).toBeAnInstanceOf<ImapEmailReader>()
  }

  @Test fun `parses plain text email`() {
    val email = message {
      setFrom(InternetAddress("from@example.com", "Sender"))
      setRecipient(TO, InternetAddress("to@example.com"))
      subject = "Hello"
      setText("Hi there")
    }.toEmailMessage()

    expect(email.subject).toEqual("Hello")
    expect(email.from).toEqual(Named(Email("from@example.com"), "Sender"))
    expect(email.to).toContainExactly(Named(Email("to@example.com"), null))
    expect(email.text).toEqual("Hi there")
    expect(email.html).toEqual(null)
    expect(email.attachments).toEqual(emptyMap())
  }

  @Test fun `parses multipart with html and attachment`() {
    val email = message {
      setFrom(InternetAddress("from@example.com"))
      subject = "Files"
      setContent(MimeMultipart().apply {
        addBodyPart(MimeBodyPart().apply {
          setContent("<p>Hello</p>", MimeTypes.withCharset(MimeTypes.html))
        })
        addBodyPart(MimeBodyPart().apply {
          setContent("PDF content".toByteArray(), "application/pdf")
          fileName = "hello.pdf"
          disposition = Part.ATTACHMENT
        })
      })
    }.toEmailMessage()

    expect(email.subject).toEqual("Files")
    expect(email.html).toEqual("<p>Hello</p>")
    expect(email.text).toEqual(null)
    expect(email.attachments.keys).toContainExactly("hello.pdf")
    expect(email.attachments["hello.pdf"]!!.decodeToString()).toEqual("PDF content")
  }

  @Test fun `parses attachments with duplicate names using numeric suffixes`() {
    val email = message {
      setContent(MimeMultipart().apply {
        listOf("First", "Second", "Third").forEach {
          addBodyPart(MimeBodyPart().apply {
            setContent(it.toByteArray(), "application/pdf")
            fileName = "hello.pdf"
            disposition = Part.ATTACHMENT
          })
        }
      })
    }.toEmailMessage()

    expect(email.attachments.keys).toContainExactly("hello.pdf", "hello-1.pdf", "hello-2.pdf")
    expect(email.attachments["hello-1.pdf"]!!.decodeToString()).toEqual("Second")
    expect(email.attachments["hello-2.pdf"]!!.decodeToString()).toEqual("Third")
  }

  @Test fun `parses cc recipients and message id`() {
    val email = message {
      setFrom(InternetAddress("from@example.com"))
      setRecipient(TO, InternetAddress("to@example.com"))
      setRecipient(CC, InternetAddress("cc@example.com"))
      subject = "Cc"
      setText("Body")
    }.apply { setHeader("Message-ID", "<id@example.com>") }.toEmailMessage()

    expect(email.cc.map { it.email }).toContainExactly(Email("cc@example.com"))
    expect(email.id).toEqual("<id@example.com>")
  }

  @Test fun `parses reply-to recipients`() {
    val email = message {
      setReplyTo(arrayOf(InternetAddress("reply@example.com", "Reply")))
    }.toEmailMessage()

    expect(email.replyTo).toEqual(Named(Email("reply@example.com"), "Reply"))
  }

  @Test fun `leaves reply-to empty when the email names none`() {
    expect(message().toEmailMessage().replyTo).toEqual(null)
  }

  @Test fun `decodes encoded reply-to name`() {
    val email = message {
      setHeader("Reply-To", "=?UTF-8?B?VmFzdHVzLCBOaW1p?= <reply@example.com>")
    }.toEmailMessage()

    expect(email.replyTo).toEqual(Named(Email("reply@example.com"), "Vastus, Nimi"))
  }

  @Test fun `parses delivered-to address`() {
    val email = message { setHeader("Delivered-To", "Someone <delivered@example.com>") }.toEmailMessage()
    expect(email.deliveredTo).toEqual(Email("delivered@example.com"))
  }

  @Test fun `leaves delivered-to empty when the email names none`() {
    expect(message().toEmailMessage().deliveredTo).toEqual(null)
  }

  @Test fun `exposes all headers by lower-cased name`() {
    val email = message {
      setHeader("X-Custom", "value")
      addHeader("Received", "first")
      addHeader("Received", "second")
    }.toEmailMessage()

    expect(email.headers("x-CUSTOM")).toContainExactly("value")
    expect(email.headers("Received").toSet()).toEqual(setOf("first", "second"))
    expect(email.id).toEqual(email.headers("message-id").single())
  }

  @Test fun `decodes encoded header values`() {
    val email = message { setHeader("X-Name", "=?UTF-8?B?VGVyZQ==?=") }.toEmailMessage()

    expect(decodeHeader(email.headers("X-Name").single())).toEqual("Tere")
    expect(email.headers("x-name")).toContainExactly("=?UTF-8?B?VGVyZQ==?=")
  }

  @Test fun `fetchUnseen does not mark messages as read`() {
    val message = spyk(message())
    every { folder.search(any()) } returns arrayOf(message)
    val emails = reader.fetch()
    expect(emails.size).toEqual(1)
    expect(emails.first().subject).toEqual("Subject")
    verify(exactly = 0) { message.setFlag(Flags.Flag.SEEN, true) }
  }

  @Test fun `processUnseen marks messages as read after handler`() {
    val message = spyk(message())
    every { folder.search(any()) } returns arrayOf(message)
    val processed = mutableListOf<String?>()
    reader.process { processed += it.subject }
    expect(processed).toContainExactly("Subject")
    verify { message.setFlag(Flags.Flag.SEEN, true) }
  }

  @Test fun `processUnseen keeps unread if handler fails`() {
    val message = spyk(message())
    every { folder.search(any()) } returns arrayOf(message)
    expect(runCatching { reader.process { error("boom") } }.isFailure).toEqual(true)
    verify(exactly = 0) { message.setFlag(Flags.Flag.SEEN, true) }
  }

  @Test fun `processAndDelete deletes messages that were processed`() {
    val message = spyk(message())
    every { folder.search(any()) } returns arrayOf(message)
    val processed = mutableListOf<String?>()
    reader.processAndDelete { processed += it.subject }
    expect(processed).toContainExactly("Subject")
    verify { folder.search(ImapEmailReader.notDeleted) }
    verify { message.setFlag(DELETED, true) }
    verify { folder.expunge() }
  }

  @Test fun `processAndDelete keeps messages if handler fails`() {
    val message = spyk(message())
    every { folder.search(any()) } returns arrayOf(message)
    expect(runCatching { reader.processAndDelete { error("boom") } }.isFailure).toEqual(true)
    verify(exactly = 0) { message.setFlag(DELETED, true) }
    verify(exactly = 0) { folder.expunge() }
  }

  @Test fun `processAndMove moves processed messages to another folder`() {
    val message = spyk(message())
    val archive = mockk<Folder>(relaxed = true)
    every { message.folder } returns folder
    every { folder.store } returns store
    every { store.getFolder("Archive") } returns archive
    every { archive.exists() } returns false
    every { folder.search(any()) } returns arrayOf(message)
    reader.processAndMove("Archive") { }
    verify { archive.create(HOLDS_MESSAGES) }
    verify { folder.copyMessages(match { it.single() === message }, archive) }
    verify { message.setFlag(DELETED, true) }
    verify { folder.expunge() }
  }

  @Test fun `processAndMove reuses existing target folder`() {
    val message = spyk(message())
    val archive = mockk<Folder>(relaxed = true)
    every { message.folder } returns folder
    every { folder.store } returns store
    every { store.getFolder("Archive") } returns archive
    every { archive.exists() } returns true
    every { folder.search(any()) } returns arrayOf(message)
    reader.processAndMove("Archive") { }
    verify(exactly = 0) { archive.create(any()) }
    verify { folder.copyMessages(any(), archive) }
  }

  @Test fun `processAndMove looks up target folder only once`() {
    val first = spyk(message())
    val second = spyk(message())
    val archive = mockk<Folder>(relaxed = true)
    every { first.folder } returns folder
    every { second.folder } returns folder
    every { folder.store } returns store
    every { store.getFolder("Archive") } returns archive
    every { archive.exists() } returns true
    every { folder.search(any()) } returns arrayOf(first, second)
    reader.processAndMove("Archive") { }
    verify(exactly = 1) { archive.exists() }
    verify(exactly = 2) { folder.copyMessages(any(), archive) }
  }

  private fun message(block: MimeMessage.() -> Unit = {}) = MimeMessage(Session.getInstance(Properties())).apply {
    setFrom(InternetAddress("from@example.com"))
    subject = "Subject"
    setText("Body")
    block()
    saveChanges()
  }
}
