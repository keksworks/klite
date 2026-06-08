package klite

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ETagTest {
  private val exchange = mockk<HttpExchange>(relaxed = true)

  @Test fun `eTag header is set`() {
    val tag = exchange.eTagHashCode("body")
    verify { exchange.header("ETag", tag) }
    expect(tag).toEqual("W/\"${"body".hashCode().toUInt().toString(36)}\"")
  }

  @Test fun `checkETagHashCode throws NotModified when matching`() {
    every { exchange.responseType } returns MimeTypes.json
    exchange.checkETagHashCode("body")

    val expected = exchange.eTagHashCode("body", MimeTypes.json)
    every { exchange.header("If-None-Match") } returns expected
    assertThrows<NotModifiedException> { exchange.checkETagHashCode("body") }
  }

  @Test fun `different content-types produce different etags`() {
    val tag1 = exchange.eTagHashCode("body", MimeTypes.text)
    val tag2 = exchange.eTagHashCode("body", MimeTypes.json)
    expect(tag1).notToEqual(tag2)
  }
}

