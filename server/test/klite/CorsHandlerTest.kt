package klite

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import klite.RequestMethod.OPTIONS
import klite.RequestMethod.POST
import klite.StatusCode.Companion.OK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CorsHandlerTest {
  val exchange = mockk<HttpExchange>(relaxed = true)
  val cors = CorsHandler()

  @Test fun `no origin`() {
    every { exchange.header("Origin") } returns null
    runBlocking { cors.before(exchange) }
    verify(exactly = 0) { exchange.header(any(), any()) }
  }

  @Test fun `allow any origin without credentials`() {
    every { exchange.header("Origin") } returns "my.origin"
    runBlocking { cors.before(exchange) }
    verify {
      exchange.header("Access-Control-Allow-Origin", "my.origin")
    }
    verify(exactly = 0) { exchange.header("Access-Control-Allow-Credentials", any()) }
  }

  @Test fun `allow credentials only with explicit origins`() {
    val cors = CorsHandler(allowedOrigins = setOf("my.origin"))
    every { exchange.header("Origin") } returns "my.origin"
    runBlocking { cors.before(exchange) }
    verify {
      exchange.header("Access-Control-Allow-Origin", "my.origin")
      exchange.header("Access-Control-Allow-Credentials", "true")
    }
  }

  @Test fun `allow only specific origin`() {
    val cors = CorsHandler(allowedOrigins = setOf("my.origin"))
    every { exchange.header("Origin") } returns "my.origin"
    runBlocking { cors.before(exchange) }
    verify { exchange.header("Access-Control-Allow-Origin", "my.origin") }

    every { exchange.header("Origin") } returns "other.origin"
    runBlocking { assertThrows<ForbiddenException> { cors.before(exchange) } }
  }

  @Test fun `preflight request`() {
    every { exchange.method } returns OPTIONS
    every { exchange.header("Origin") } returns "my.origin"
    every { exchange.header("Access-Control-Request-Method") } returns POST.toString()
    every { exchange.header("Access-Control-Request-Headers") } returns "Custom-Header"

    runBlocking { cors.before(exchange) }

    verify {
      exchange.header("Access-Control-Allow-Methods", cors.allowedMethods.joinToString())
      exchange.header("Access-Control-Allow-Headers", "Custom-Header")
      exchange.header("Access-Control-Max-Age", cors.maxAge.inWholeSeconds.toString())
      exchange.send(OK)
    }
  }

  @Test fun `preflight without requested method`() {
    every { exchange.method } returns OPTIONS
    every { exchange.header("Origin") } returns "my.origin"
    every { exchange.header("Access-Control-Request-Method") } returns null
    runBlocking { assertThrows<BadRequestException> { cors.before(exchange) } }
  }

  @Test fun `preflight with invalid requested method`() {
    every { exchange.method } returns OPTIONS
    every { exchange.header("Origin") } returns "my.origin"
    every { exchange.header("Access-Control-Request-Method") } returns "FLY"
    runBlocking { assertThrows<BadRequestException> { cors.before(exchange) } }
  }

  @Test fun `preflight with disallowed method`() {
    every { exchange.method } returns OPTIONS
    every { exchange.header("Origin") } returns "my.origin"
    every { exchange.header("Access-Control-Request-Method") } returns "HEAD"
    runBlocking { assertThrows<ForbiddenException> { cors.before(exchange) } }
  }
}
