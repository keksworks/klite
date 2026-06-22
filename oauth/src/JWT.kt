package klite.oauth

import klite.Email
import klite.SnakeCase
import klite.base64UrlDecode
import klite.json.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
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

  fun verify(openID: OpenID) = verify(openID.key(header.kid ?: error("Missing kid in JWT header"))?.publicKey ?: error("No key found for kid ${header.kid}"))

  // TODO: check performance and maybe cache successful/unsuccessful verifications
  fun verify(publicKey: PublicKey) {
    checkExpiry()
    val jcaAlg = when (header.alg) {
      "RS256" -> "SHA256withRSA"
      "RS384" -> "SHA384withRSA"
      "RS512" -> "SHA512withRSA"
      else -> throw UnsupportedOperationException("Unsupported algorithm: ${header.alg}, expected RS256/RS384/RS512")
    }
    val sig = Signature.getInstance(jcaAlg)
    sig.initVerify(publicKey)
    sig.update("${rawParts[0]}.${rawParts[1]}".toByteArray())
    require(sig.verify(signature)) { "Invalid JWT signature" }
  }

  /** Checks only expiry without signature verification, e.g. for tokens verified by external provider */
  fun verify() = checkExpiry()

  data class Header(val fields: JsonNode): JsonNode by fields {
    val alg: String by fields
    val typ: String by fields
    val kid: String? by fields
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

data class JwksKeysResponse(val keys: List<JwkKey>)

data class JwkKey(val kty: String, val use: String?, val kid: String, val n: String, val e: String) {
  val publicKey: PublicKey by lazy {
    if (kty != "RSA") throw UnsupportedOperationException("Unsupported key type: $kty")
    val modulus = BigInteger(1, n.base64UrlDecode())
    val exponent = BigInteger(1, e.base64UrlDecode())
    KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
  }
}
