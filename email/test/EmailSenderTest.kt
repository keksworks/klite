package klite.email

import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.spyk
import io.mockk.verify
import klite.Config
import klite.Email
import klite.MimeTypes
import org.junit.jupiter.api.Test

val email = Email("some@email.com")

class EmailSenderTest {
  init { Config.useEnvFile() }

  @Test fun `instances can be created with default config`() {
    expect(FakeEmailSender()).toBeAnInstanceOf<EmailSender>()
    expect(SmtpEmailSender()).toBeAnInstanceOf<EmailSender>()
  }

  @Test fun translates() {
    val sender = spyk(FakeEmailSender())
    val content = EmailContent("en", "hello", mapOf("name" to "World"))
    sender.send(email, content)
    verify { sender.send(email, content.subject, content.fullHtml(), MimeTypes.html) }
  }

  @Test fun `substitutions are escaped in bodyHtml`() {
    val content = EmailContent("en", "welcome", mapOf("name" to "<a href=\"https://evil\">Bob</a>"))
    expect(content.bodyHtml).toEqual("<p>Hello, <b>&lt;a href=&quot;https://evil&quot;&gt;Bob&lt;/a&gt;</b>!</p>")
    expect(content.fullHtml()).notToContain("<a href=\"https://evil\"")
  }

  @Test fun `trusted markup can be substituted by overriding htmlSubstitutions`() {
    val content = object: EmailContent("en", "welcome", mapOf("name" to "Bob")) {
      override val htmlSubstitutions get() = mapOf("name" to "<i>Bob</i>")
    }
    expect(content.bodyHtml).toEqual("<p>Hello, <b><i>Bob</i></b>!</p>")
  }

  @Test fun `arbitrary headers are passed to the message`() {
    val sender = FakeEmailSender()
    sender.send(email, EmailContent("en", "hello", headers = mapOf("In-Reply-To" to "<1@example.com>")))
    expect(sender.lastSentEmail).toContain("Headers: {In-Reply-To=<1@example.com>}")
  }
}
