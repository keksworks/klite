package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEndWith
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import klite.RequestMethod.GET
import klite.StatusCode.Companion.Forbidden
import org.junit.jupiter.api.Test
import java.io.IOException

class RequestLogFormatterTest {
  val exchange = mockk<HttpExchange> {
    every { remoteAddress } returns "127.0.0.1"
    every { method } returns GET
    every { path } returns "/api/path"
    every { query } returns "?query"
    every { statusCode } returns Forbidden
    every { header("User-Agent") } returns "Googlebot/2"
    every { failure } returns null
  }

  @Test fun default() {
    expect(defaultRequestLogFormatter(exchange, 123)).toEqual(
      "127.0.0.1 GET /api/path?query: 403 in 123 ms - Googlebot/2")
  }

  @Test fun failure() {
    val failure = IOException("Kaboom")
    every { exchange.failure } returns failure
    expect(defaultRequestLogFormatter(exchange, 123)!!).toEndWith(" - $failure")
  }
}
