package klite.oauth

import klite.HttpExchange
import klite.info
import klite.json.parse
import klite.logger
import klite.oauth.JWT.Companion.jsonMapper
import klite.plus
import java.net.URI
import java.net.http.HttpClient

class OIDCConfig(
  val issuer: String,
  val authorizationEndpoint: URI,
  val deviceAuthorizationEndpoint: URI? = null,
  val tokenEndpoint: URI? = null,
  val tokenEndpointAuthMethodsSupported: Set<String> = emptySet(),
  val userinfoEndpoint: URI? = null,
  val revocationEndpoint: URI? = null,
  val jwksUri: URI,
  val idTokenSigningAlgValuesSupported: Set<String> = emptySet(),
  val subjectTypesSupported: Set<String> = emptySet(),
  val responseTypesSupported: Set<String> = emptySet(),
  val claimsSupported: Set<String> = emptySet(),
  val claimTypesSupported: Set<String> = emptySet(),
  val grantTypesSupported: Set<String> = emptySet(),
  val uiLocalesSupported: List<String> = emptyList(),
  val scopesSupported: List<String> = emptyList(),
) {
  fun toClient(provider: String, httpClient: HttpClient) = object: OAuthClient(
    provider,
    scopesSupported.joinToString(" "),
    authorizationEndpoint.toString(),
    tokenEndpoint?.toString() ?: "",
    userinfoEndpoint?.toString(),
    jwksUri.toString(),
    httpClient
  ) {
    override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
      val jwt = token.idToken ?: error("id_token is required for OpenID Connect")
      jwt.verify(key(jwt.header.kid!!).publicKey)
      val names = jwt.payload.name?.split(" ") ?: emptyList()
      return UserProfile(provider, jwt.payload.subject, jwt.payload.email!!, names.first(), names.drop(1).joinToString(" "), locale = jwt.payload.locale)
    }
  }
}

class OpenIDConnect(val issuerUrl: URI) {
  private val log = logger()
  private val discoveryUrl = issuerUrl + "/.well-known/openid-configuration"
  val config: OIDCConfig = readConfig()

  private fun readConfig() = discoveryUrl.toURL().openStream().use { stream ->
    log.info("Fetching config from $discoveryUrl")
    jsonMapper.parse<OIDCConfig>(stream)
  }
}
