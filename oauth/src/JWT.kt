package klite.oauth

import klite.Email
import klite.base64UrlDecode
import klite.json.*
import klite.logger
import klite.warn
import java.time.Instant
import java.util.*

class JWT(private val token: String) {
  companion object {
    private val jsonMapper = JsonMapper()
  }

  private val parts = token.split(".").map { it.base64UrlDecode() }
  val headerJson get() = parts[0].decodeToString()
  val payloadJson get() = parts[1].decodeToString()
  val signature get() = parts[2]

  val header by lazy { Header(jsonMapper.parse<JsonNode>(headerJson)) }
  val payload by lazy { Payload(jsonMapper.parse<JsonNode>(payloadJson)) }

  override fun toString() = token
  override fun equals(other: Any?) = (other as? JWT)?.token == token
  override fun hashCode() = token.hashCode()

  fun verify() {
    payload.expiresAt?.let { require(it.isAfter(Instant.now())) { "Token expired" } }
    logger().warn("JWT signature verification not implemented yet")
  }

  data class Header(val fields: JsonNode): JsonNode by fields {
    val alg by fields
    val typ by fields
  }

  /** https://www.iana.org/assignments/jwt/jwt.xhtml#claims */
  data class Payload(val claims: JsonNode): JsonNode by claims {
    val subject get() = getString("sub")
    val audience get() = getString("aud")
    val issuedAt get() = getOrNull<Number>("iat")?.let { Instant.ofEpochSecond(it.toLong()) }
    val issuer = getOrNull<String>("iss")
    val expiresAt get() = getOrNull<Number>("exp")?.let { Instant.ofEpochSecond(it.toLong()) }
    val name get() = getOrNull<String>("name")
    val email get() = getOrNull<String>("email")?.let { Email(it) }
    val emailVerified get() = getOrNull<Boolean>("email_verified")
    val locale get() = getOrNull<String>("locale")?.let { Locale.forLanguageTag(it) }
  }
}
