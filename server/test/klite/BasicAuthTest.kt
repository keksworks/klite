package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.*

class BasicAuthTest {
  private val decorators = mutableListOf<Decorator>()
  private val config = mockk<RouterConfig>()
  private val exchange = mockk<HttpExchange>(relaxed = true)

  init {
    every { config.before(any()) } answers { decorators += firstArg<Before>().toDecorator() }
  }

  @Test fun `valid credentials`() {
    every { exchange.header("Authorization") } returns basic("user", "pass")
    expect(asUser("user" to Password("pass"))).toEqual("allowed")
    verify { exchange.attr("user", "user") }
  }

  @Test fun `password may contain colons`() {
    every { exchange.header("Authorization") } returns basic("user", "pa:ss:word")
    expect(asUser("user" to Password("pa:ss:word"))).toEqual("allowed")
  }

  @Test fun `wrong password`() {
    every { exchange.header("Authorization") } returns basic("user", "wrong")
    expect { asUser("user" to Password("pass")) }.toThrow<UnauthorizedException>()
  }

  @Test fun `unknown user`() {
    every { exchange.header("Authorization") } returns basic("other", "pass")
    expect { asUser("user" to Password("pass")) }.toThrow<UnauthorizedException>()
  }

  @Test fun `no authorization header`() {
    every { exchange.header("Authorization") } returns null
    expect { asUser() }.toThrow<UnauthorizedException>()
    verify { exchange.header("WWW-Authenticate", "Basic realm=\"Auth\"") }
  }

  @Test fun `non-basic authorization header`() {
    every { exchange.header("Authorization") } returns "Bearer token"
    expect { asUser() }.toThrow<UnauthorizedException>()
  }

  @Test fun `malformed base64`() {
    every { exchange.header("Authorization") } returns "Basic !!!not-base64!!!"
    expect { asUser() }.toThrow<UnauthorizedException>()
  }

  @Test fun `no colon separator`() {
    every { exchange.header("Authorization") } returns "Basic " + Base64.getEncoder().encodeToString("nocolon".toByteArray())
    expect { asUser() }.toThrow<UnauthorizedException>()
  }

  @Test fun `realm is escaped`() {
    every { exchange.header("Authorization") } returns null
    expect { asUser(realm = "Auth\"x") }.toThrow<UnauthorizedException>()
    verify { exchange.header("WWW-Authenticate", "Basic realm=\"Auth\\\"x\"") }
  }

  private fun asUser(vararg users: Pair<String, Password>, realm: String = "Auth"): Any? {
    config.basicAuth(users.toMap(), realm)
    return decorators.single()(exchange) { "allowed" }
  }

  private fun basic(user: String, password: String) =
    "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
}
