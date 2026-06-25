import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.oauth.OpenIDConnect
import org.junit.jupiter.api.Test
import java.net.URI

class OpenIDConnectTest {
  @Test fun `read Google config`() {
    val openId = OpenIDConnect(URI("https://accounts.google.com"))
    expect(openId.config.issuer).toEqual("https://accounts.google.com")
    expect(openId.config.authorizationEndpoint).toEqual(URI("https://accounts.google.com/o/oauth2/v2/auth"))
    expect(openId.config.tokenEndpoint).toEqual(URI("https://oauth2.googleapis.com/token"))
    expect(openId.config.jwksUri).toEqual(URI("https://www.googleapis.com/oauth2/v3/certs"))
    expect(openId.keys.map { it.publicKey }.size).toBeGreaterThan(2)
  }

  @Test fun `read Microsoft config`() {
    val openId = OpenIDConnect(URI("https://login.microsoftonline.com/common/v2.0"))
    expect(openId.config.issuer).toEqual("https://login.microsoftonline.com/{tenantid}/v2.0")
    expect(openId.config.authorizationEndpoint).toEqual(URI("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"))
    expect(openId.config.tokenEndpoint).toEqual(URI("https://login.microsoftonline.com/common/oauth2/v2.0/token"))
    expect(openId.config.jwksUri).toEqual(URI("https://login.microsoftonline.com/common/discovery/v2.0/keys"))
    expect(openId.keys.map { it.publicKey }.size).toBeGreaterThan(2)
  }

  @Test fun `read Facebook config`() {
    val openId = OpenIDConnect(URI("https://www.facebook.com"))
    expect(openId.config.authorizationEndpoint).toEqual(URI("https://facebook.com/dialog/oauth/"))
    expect(openId.config.jwksUri).toEqual(URI("https://www.facebook.com/.well-known/oauth/openid/jwks/"))
    expect(openId.keys.map { it.publicKey }.size).toBeGreaterThan(2)
  }

  @Test fun `read Apple config`() {
    val openId = OpenIDConnect(URI("https://appleid.apple.com"))
    expect(openId.config.authorizationEndpoint).toEqual(URI("https://appleid.apple.com/auth/authorize"))
    expect(openId.config.tokenEndpoint).toEqual(URI("https://appleid.apple.com/auth/token"))
    expect(openId.config.jwksUri).toEqual(URI("https://appleid.apple.com/auth/keys"))
    expect(openId.keys.map { it.publicKey }.size).toBeGreaterThan(2)
  }

  @Test fun `read TARA config`() {
    val openId = OpenIDConnect(URI("https://tara.ria.ee/oidc"))
    expect(openId.config.issuer).toEqual("https://tara.ria.ee")
    expect(openId.config.authorizationEndpoint).toEqual(URI("https://tara.ria.ee/oidc/authorize"))
    expect(openId.config.tokenEndpoint).toEqual(URI("https://tara.ria.ee/oidc/token"))
    expect(openId.config.jwksUri).toEqual(URI("https://tara.ria.ee/oidc/jwks"))
    expect(openId.keys.map { it.publicKey }.size).toBeGreaterThan(1)
  }
}
