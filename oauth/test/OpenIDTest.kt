import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import klite.oauth.OpenID
import org.junit.jupiter.api.Test
import java.net.URI

class OpenIDTest {
  @Test fun `read Google config`() {
    val openId = OpenID(URI("https://accounts.google.com"))
    expect(openId.config.issuer).toEqual(URI("https://accounts.google.com"))
    expect(openId.config.authorizationEndpoint).toEqual(URI("https://accounts.google.com/o/oauth2/v2/auth"))
    expect(openId.config.tokenEndpoint).toEqual(URI("https://oauth2.googleapis.com/token"))
    expect(openId.config.jwksUri).toEqual(URI("https://www.googleapis.com/oauth2/v3/certs"))
    expect(openId.keys.map { it.publicKey }).toHaveSize(4)
  }

  @Test fun `read TARA config`() {
    val openId = OpenID(URI("https://tara.ria.ee/oidc"))
    expect(openId.config.issuer).toEqual(URI("https://tara.ria.ee"))
    expect(openId.config.authorizationEndpoint).toEqual(URI("https://tara.ria.ee/oidc/authorize"))
    expect(openId.config.tokenEndpoint).toEqual(URI("https://tara.ria.ee/oidc/token"))
    expect(openId.config.jwksUri).toEqual(URI("https://tara.ria.ee/oidc/jwks"))
    expect(openId.keys.map { it.publicKey }).toHaveSize(2)
  }
}
