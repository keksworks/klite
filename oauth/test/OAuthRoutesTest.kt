package klite.oauth

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
    every { userProvder.provide(any(), any(), any()) } returns user
    every { userProvder.initSession(any(), any()) } answers { callOriginal() }
    every { exchange.session["oauth_123"] } returns "/path"

    expect { runBlocking { routes.accept("code", "123", exchange) } }.toThrow<RedirectException>()

    verify {
      userProvder.provide(user.copy(locale = Locale.ENGLISH), token, exchange)
      userProvder.initSession(user, exchange)
      exchange.session["userId"] = "uid"
      exchange.redirect(URI("/path"))
    }
  }
}
