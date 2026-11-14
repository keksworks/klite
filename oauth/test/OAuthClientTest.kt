package klite.oauth

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import klite.Config
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import klite.Email
import klite.HttpExchange
import klite.http.httpClient
import klite.queryParams
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpResponse

class OAuthClientTest {
  init {
    Config["GOOGLE_OAUTH_CLIENT_ID"] = "c-id"
    Config["GOOGLE_OAUTH_CLIENT_SECRET"] = "secret"
  }

  val client = GoogleOAuthClient(httpClient())

  @Test fun `startAuthUrl includes hl so Google UI opens in given lang`() {
    val url = client.startAuthUrl("state", URI("https://app/oauth/google"), "et")
    expect(url.queryParams["hl"]).toEqual("et")
    expect(url.queryParams["client_id"]).toEqual("c-id")
  }

  @Test fun `does not log secret, tokens or profile`() {
    val http = mockk<HttpClient> {
      every { send(any(), any<HttpResponse.BodyHandler<String>>()) } returns mockk {
        every { statusCode() } returns 200
        every { body() } returns """{"access_token": "token", "expires_in": 3599}"""
      }
    }
    val logger = mockk<System.Logger>(relaxed = true)
    val client = object: OAuthClient("GOOGLE", authUrl = "https://auth", tokenUrl = "https://token", httpClient = http) {
      init { this.http.logger = logger }
      override fun profile(token: OAuthTokenResponse, exchange: HttpExchange) = TODO()
    }
    expect(client.authenticate("code", URI("https://app/oauth/google")).accessToken).toEqual("token")
    verify { logger.log(any(), match<String> { "https://token" in it && "secret" !in it && "token\"" !in it }) }
  }

  @Test fun `Microsoft email is accepted only with verified domain`() {
    fun payload(vararg claims: Pair<String, Any>) = JWT.Payload(mapOf("email" to "user@example.com", *claims))
    expect(payload("xms_edov" to true).domainVerifiedEmail()).toEqual(Email("user@example.com"))
    expect(payload("xms_edov" to "1").domainVerifiedEmail()).toEqual(Email("user@example.com"))
    expect { payload("xms_edov" to false).domainVerifiedEmail() }.toThrow<IllegalStateException>()
    expect { payload().domainVerifiedEmail() }.toThrow<IllegalStateException>()
    expect { JWT.Payload(mapOf("xms_edov" to true)).domainVerifiedEmail() }.toThrow<IllegalStateException>()
  }
}
