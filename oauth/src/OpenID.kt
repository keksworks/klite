package klite.oauth

import klite.json.parse
import java.net.URI

class OpenIDConfig(
  val issuer: String,
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

class OpenID(private val configUrl: URI) {
  val config: OpenIDConfig = readConfig()

  private fun readConfig() = configUrl.toURL().openStream().use { stream ->
    JWT.jsonMapper.parse<OpenIDConfig>(stream)
  }
}
