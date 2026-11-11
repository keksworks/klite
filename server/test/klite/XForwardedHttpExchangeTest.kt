package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class XForwardedHttpExchangeTest {
  abstract class MockableHttpExchange: OriginalHttpExchange()
  val original = mockk<MockableHttpExchange>(relaxed = true) {
    every { requestMethod } returns "GET"
  }
  val config = mockk<RouterConfig>(relaxed = true)
  val exchange = XForwardedHttpExchange(original, config, null, "req-id")

  @Test fun `prefers X-Forwarded-Host`() {
    every { exchange.header("X-Forwarded-Host") } returns "forwarded.com:8080"
    expect(exchange.host).toEqual("forwarded.com:8080")
  }

  @Test fun `falls back to Host`() {
    every { exchange.header("X-Forwarded-Host") } returns null
    every { exchange.header("Host") } returns "host.domain"
    expect(exchange.host).toEqual("host.domain")
  }

  @Test fun `takes last X-Forwarded-For address, with or without spaces after commas`() {
    every { exchange.header("X-Forwarded-For") } returns "1.1.1.1, 2.2.2.2"
    expect(exchange.remoteAddress).toEqual("2.2.2.2")
    every { exchange.header("X-Forwarded-For") } returns "6.6.6.6,1.1.1.1,2.2.2.2"
    expect(exchange.remoteAddress).toEqual("2.2.2.2")
  }

  @Test fun `validates forwarded host`() {
    every { exchange.header("X-Forwarded-Host") } returns "evil.com/path"
    assertThrows<IllegalArgumentException> { exchange.host }
  }
}
