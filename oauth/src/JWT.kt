package klite.oauth

import klite.*
import klite.SnakeCase
import klite.json.*
import klite.oauth.JWT.Companion.jsonMapper
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class JWT(val headerPart: String, val payloadPart: String, val signaturePart: String? = null) {
  companion object {
    internal val jsonMapper = JsonMapper(keys = SnakeCase, trimToNull = false)
    private val hsAlgorithms = mapOf("HS256" to "HmacSHA256", "HS384" to "HmacSHA384", "HS512" to "HmacSHA512")
    private val pkiAlgorithms = mapOf("RS256" to "SHA256withRSA", "RS384" to "SHA384withRSA", "RS512" to "SHA512withRSA")
    init { Converter.use { JWT(it) } }
  }

  val headerJson get() = headerPart.base64UrlDecode().decodeToString()
  val payloadJson get() = payloadPart.base64UrlDecode().decodeToString()

  val header by lazy { Header(jsonMapper.parse<JsonNode>(headerJson)) }
  val payload by lazy { Payload(jsonMapper.parse<JsonNode>(payloadJson)) }
  val signature by lazy { signaturePart?.base64UrlDecode() ?: error("JTW signature part missing") }

  val signedPart get() = "$headerPart.$payloadPart"
  override fun toString() = "$signedPart.$signaturePart"

  private fun checkExpiry() {
    payload.expiresAt?.let { require(it.isAfter(Instant.now())) { "Token expired" } }
  }

  fun verify(secret: String) {
    checkExpiry()
    val jcaAlg = hsAlgorithms[header.alg] ?: throw UnsupportedOperationException("Unsupported algorithm: ${header.alg}, expected ${hsAlgorithms.keys}")
    val mac = Mac.getInstance(jcaAlg)
    mac.init(SecretKeySpec(secret.toByteArray(), jcaAlg))
    val expected = mac.doFinal(signedPart.toByteArray())
    require(expected.contentEquals(signature)) { "Invalid JWT signature" }
  }

  fun verify(keys: Map<String, JwkKey>) =
    verify(keys[header.kid ?: error("Missing kid in JWT header")]?.publicKey ?: error("No key found for kid ${header.kid}"))

  // TODO: check performance and maybe cache successful/unsuccessful verifications
  fun verify(publicKey: PublicKey) {
    checkExpiry()
    val sig = Signature.getInstance(pkiAlgorithms[header.alg] ?: throw UnsupportedOperationException("Unsupported algorithm: ${header.alg}, expected ${pkiAlgorithms.keys}"))
    sig.initVerify(publicKey)
    sig.update(signedPart.toByteArray())
    require(sig.verify(signature)) { "Invalid JWT signature" }
  }

  /** Checks only expiry without signature verification, e.g. for tokens verified by external provider */
  fun verify() = checkExpiry()

  fun sign(privateKey: PrivateKey): JWT {
    require(signaturePart == null) { "Already signed" }
    val sig = Signature.getInstance(pkiAlgorithms[header.alg]).apply {
      initSign(privateKey)
      update(signedPart.toByteArray())
    }
    return copy(signaturePart = sig.sign().base64UrlEncode())
  }

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

fun JWT(token: String): JWT {
  val parts = token.split(".")
  return JWT(parts[0], parts[1], parts[2])
}

fun JWT(header: JWT.Header, payload: JWT.Payload) =
  JWT(jsonMapper.render(header.fields).base64UrlEncode(), jsonMapper.render(payload.claims).base64UrlEncode())

data class JwksKeysResponse(val keys: List<JwkKey>)

data class JwkKey(val kty: String, val use: String?, val kid: String, val n: String, val e: String) {
  val publicKey: PublicKey by lazy {
    if (kty != "RSA") throw UnsupportedOperationException("Unsupported key type: $kty")
    val modulus = BigInteger(1, n.base64UrlDecode())
    val exponent = BigInteger(1, e.base64UrlDecode())
    KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
  }
}
