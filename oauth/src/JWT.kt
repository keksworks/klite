package klite.oauth

import klite.Email
import klite.SnakeCase
import klite.base64UrlDecode
import klite.json.*
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JWT(private val token: String) {
  companion object {
    internal val jsonMapper = JsonMapper(keys = SnakeCase)
    private val hsAlgorithms = mapOf("HS256" to "HmacSHA256", "HS384" to "HmacSHA384", "HS512" to "HmacSHA512")
  }

  private val rawParts = token.split(".")
  private val parts = rawParts.map { it.base64UrlDecode() }
  val headerJson get() = parts[0].decodeToString()
  val payloadJson get() = parts[1].decodeToString()
  val signature get() = parts[2]

  val header by lazy { Header(jsonMapper.parse<JsonNode>(headerJson)) }
  val payload by lazy { Payload(jsonMapper.parse<JsonNode>(payloadJson)) }

  override fun toString() = token
  override fun equals(other: Any?) = (other as? JWT)?.token == token
  override fun hashCode() = token.hashCode()

  private fun checkExpiry() {
    payload.expiresAt?.let { require(it.isAfter(Instant.now())) { "Token expired" } }
  }

  fun verify(secret: String) {
    checkExpiry()
    val jcaAlg = hsAlgorithms[header.alg] ?: throw UnsupportedOperationException("Unsupported algorithm: ${header.alg}, expected one of ${hsAlgorithms.keys}")
    val mac = Mac.getInstance(jcaAlg)
    mac.init(SecretKeySpec(secret.toByteArray(), jcaAlg))
    val expected = mac.doFinal("${rawParts[0]}.${rawParts[1]}".toByteArray())
    require(expected.contentEquals(signature)) { "Invalid JWT signature" }
  }

  /** Checks only expiry without signature verification, e.g. for tokens verified by external provider */
  fun verify() = checkExpiry()

  data class Header(val fields: JsonNode): JsonNode by fields {
    val alg by fields
    val typ by fields
  }

  /** https://www.iana.org/assignments/jwt/jwt.xhtml#claims */
  data class Payload(val claims: JsonNode): JsonNode by claims {
    val subject get() = getString("sub")
    val audience get() = getString("aud")
    val issuedAt get() = getOrNull<Number>("iat")?.let { Instant.ofEpochSecond(it.toLong()) }
    val issuer get() = getOrNull<String>("iss")
    val expiresAt get() = getOrNull<Number>("exp")?.let { Instant.ofEpochSecond(it.toLong()) }
    val name get() = getOrNull<String>("name")
    val email get() = getOrNull<String>("email")?.let { Email(it) }
    val emailVerified get() = getOrNull<Boolean>("email_verified")
    val locale get() = getOrNull<String>("locale")?.let { Locale.forLanguageTag(it) }
  }
}
