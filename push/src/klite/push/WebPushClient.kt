package klite.push

import klite.Config
import klite.MimeTypes
import klite.base64UrlDecode
import klite.http.httpClient
import klite.http.post
import klite.json.JsonMapper
import klite.oauth.JWT
import klite.oauth.JWT.Header
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.time.Instant
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

data class PushSubscription(val endpoint: URI, val keys: SubscriptionKeys, val expirationTime: Instant? = null)
data class SubscriptionKeys(val p256dh: String, val auth: String)

class WebPushClient(
  private val vapidKeyPair: VapidKeyPair = VapidKeyPair.fromConfig(),
  private val http: HttpClient = httpClient(),
  private val jsonMapper: JsonMapper = JsonMapper(),
  private val ttl: Duration = 24.hours,
  private val jwtSub: String = Config.optional("WEB_PUSH_SUB", "mailto:push@klite.dev"),
) {
  companion object {
    private val P256_PARAMS: ECParameterSpec by lazy {
      val dummyKpg = KeyPairGenerator.getInstance("EC")
      dummyKpg.initialize(ECGenParameterSpec("secp256r1"))
      (dummyKpg.generateKeyPair().public as ECPublicKey).params
    }

    /** 4096 as 12-byte big-endian */
    internal val RS_BYTES = ByteArray(12).also { it[10] = 16 }
  }

  @IgnorableReturnValue
  fun send(subscription: PushSubscription, payload: Any, ttl: Duration = this.ttl): HttpResponse<String> {
    val jwt = createVapidJwt(subscription.endpoint)
    val key = vapidKeyPair.publicKey
    val data = when (payload) {
      is ByteArray -> payload
      is String -> payload.toByteArray()
      else -> jsonMapper.render(payload).toByteArray()
    }
    val body = encrypt(data, subscription.keys)
    return http.post(subscription.endpoint, body) {
      header("Content-Type", MimeTypes.binary)
      header("Content-Encoding", "aes128gcm")
      header("TTL", ttl.inWholeSeconds.toString())
      header("Urgency", "normal")
      header("Authorization", "vapid t=$jwt, k=$key")
    }
  }

  internal fun createVapidJwt(endpoint: URI): JWT {
    val now = Instant.now().epochSecond
    val header = Header(mapOf("alg" to "ES256", "typ" to "JWT"))
    val claims: Map<String, Any?> = mapOf(
      "aud" to "${endpoint.scheme}://${endpoint.host}",
      "exp" to now + 43200,
      "sub" to jwtSub
    )
    val jwt = JWT(header, JWT.Payload(claims))
    return jwt.sign(vapidKeyPair.privateKey)
  }

  internal fun encrypt(plaintext: ByteArray, keys: SubscriptionKeys): ByteArray {
    val salt = ByteArray(16).also { Random().nextBytes(it) }
    val browserPubRaw = keys.p256dh.base64UrlDecode()
    val browserPub = decodeEcPublicKey(browserPubRaw)
    val senderPubRaw = vapidKeyPair.publicKey.base64UrlDecode()
    val sharedSecret = ecdh(vapidKeyPair.privateKey, browserPub)
    val authSecret = keys.auth.base64UrlDecode()

    // Phase 1: Combine ECDH shared secret with auth secret per RFC 8291 Section 3.3
    val prkKey = hkdfExtract(authSecret, sharedSecret)
    val keyInfo = "WebPush: info\u0000".toByteArray() + browserPubRaw + senderPubRaw
    val ikm = hkdfExpand(prkKey, keyInfo, 32)

    // Phase 2: Derive content encryption key and nonce per RFC 8188 Section 2.2/2.3
    val prk = hkdfExtract(salt, ikm)
    val key = hkdfExpand(prk, "Content-Encoding: aes128gcm\u0000".toByteArray(), 16)
    val nonce = hkdfExpand(prk, "Content-Encoding: nonce\u0000".toByteArray(), 12)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    val encrypted = cipher.doFinal(plaintext + 0x02.toByte())
    return salt + RS_BYTES.copyOfRange(8, 12) + 65.toByte() + senderPubRaw + encrypted
  }

  private fun decodeEcPublicKey(encoded: ByteArray): ECPublicKey {
    require(encoded[0] == 0x04.toByte()) { "Expected uncompressed EC point" }
    val x = BigInteger(1, encoded.copyOfRange(1, 33))
    val y = BigInteger(1, encoded.copyOfRange(33, 65))
    val kf = KeyFactory.getInstance("EC")
    return kf.generatePublic(ECPublicKeySpec(ECPoint(x, y), P256_PARAMS)) as ECPublicKey
  }

  internal fun ecdh(priv: ECPrivateKey, pub: ECPublicKey): ByteArray {
    val ka = KeyAgreement.getInstance("ECDH")
    ka.init(priv)
    ka.doPhase(pub, true)
    return ka.generateSecret()
  }

  internal fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt, "HmacSHA256"))
    return mac.doFinal(ikm)
  }

  internal fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(prk, "HmacSHA256"))
    val result = ByteArray(length)
    var prev = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < length) {
      mac.reset()
      mac.update(prev)
      mac.update(info)
      mac.update(counter.toByte())
      prev = mac.doFinal()
      val len = minOf(prev.size, length - offset)
      System.arraycopy(prev, 0, result, offset, len)
      offset += len
      counter++
    }
    return result
  }
}
