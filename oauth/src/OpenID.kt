package klite.oauth

import klite.info
import klite.json.parse
import klite.logger
import klite.oauth.JWT.Companion.jsonMapper
import klite.plus
import java.net.URI

class OpenIDConfig(
  val issuer: URI,
  val authorizationEndpoint: URI,
  val tokenEndpoint: URI,
  val tokenEndpointAuthMethodsSupported: Set<String> = emptySet(),
  val userinfoEndpoint: URI,
  val jwksUri: URI,
  val idTokenSigningAlgValuesSupported: Set<String> = emptySet(),
  val subjectTypesSupported: Set<String> = emptySet(),
  val responseTypesSupported: Set<String> = emptySet(),
  val claimsSupported: Set<String> = emptySet(),
  val claimTypesSupported: Set<String> = emptySet(),
  val grantTypesSupported: Set<String> = emptySet(),
  val uiLocalesSupported: List<String> = emptyList(),
  val scopesSupported: List<String> = emptyList(),
)

class OpenID(val issuerUrl: URI) {
  private val log = logger()
  private val discoveryUrl = issuerUrl + "/.well-known/openid-configuration"
  val config: OpenIDConfig = readConfig()
  val keys: List<JwkKey> = readKeys()

  fun key(kid: String) = keys.find { it.kid == kid }

  private fun readConfig() = discoveryUrl.toURL().openStream().use { stream ->
    log.info("Fetching config from $discoveryUrl")
    jsonMapper.parse<OpenIDConfig>(stream)
  }

  private fun readKeys() = config.jwksUri.toURL().openStream().use { stream ->
    log.info("Fetching keys ${config.jwksUri}")
    jsonMapper.parse<JwksKeysResponse>(stream).keys
  }
}
