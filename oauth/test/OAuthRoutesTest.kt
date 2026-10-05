package klite.oauth

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import klite.*
import klite.i18n.lang
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.*

class OAuthRoutesTest {
  val exchange = mockk<HttpExchange>(relaxed = true) {
    every { fullUrl(any()) } answers { URI("http://host/" + firstArg()) }
    every { redirect(any<URI>()) } throws RedirectException("/")
    every { lang } returns "en"
  }
  val user = UserProfile("GOOGLE", "uid", Email("e@mail"), "Test", "User")
  val token = OAuthTokenResponse("token", 100)
  val oauthClient = mockk<OAuthClient> {
    every { provider } returns user.provider
    coEvery { authenticate("code", any()) } returns token
    coEvery { profile(token, exchange) } returns user
  }
  val userProvder = mockk<OAuthUserProvider>()
  val registry = mockk<Registry> {
    every { requireAll<OAuthClient>() } returns listOf(oauthClient)
  }
  val routes = OAuthRoutes(userProvder, registry)

  @Test fun `accept user`() {
    // a real Session, as Session.get() cannot be stubbed on a mock: it is overloaded by the reified getter
    val session = Session(mutableMapOf<String, Comparable<*>?>("oauth_123" to "/path"))
    every { exchange.session } returns session
    every { userProvder.provide(any(), any(), any()) } returns user
    every { userProvder.initSession(any(), any()) } answers { callOriginal() }

    expect { runBlocking { routes.accept("code", "123", exchange) } }.toThrow<RedirectException>()

    verify {
      userProvder.provide(user.copy(locale = Locale.ENGLISH), token, exchange)
      userProvder.initSession(user, exchange)
      exchange.redirect(URI("/path"))
    }
    expect(session["userId"]).toEqual("uid")
  }

  @Test fun `safeRedirectParam allows path-absolute`() {
    every { exchange.query("redirect") } returns "/path?a=1"
    expect(exchange.safeRedirectParam).toEqual(URI("/path?a=1"))
    every { exchange.query("redirect") } returns "/"
    expect(exchange.safeRedirectParam).toEqual(URI("/"))
  }

  @Test fun `safeRedirectParam rejects open redirects`() {
    every { exchange.query("redirect") } returns null
    expect(exchange.safeRedirectParam).toEqual(null)
    for (bad in listOf("//evil.com", "/\\evil.com", "https://evil.com", "http://host/ok", "/path\r\nSet-Cookie: x", "\\/evil.com")) {
      every { exchange.query("redirect") } returns bad
      expect(exchange.safeRedirectParam).toEqual(null)
    }
  }
}
