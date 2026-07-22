package klite.push

import klite.Config
import klite.base64UrlDecode
import klite.http.httpClient
import klite.oauth.JWT
import klite.oauth.JWT.Header
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
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
  private val vapidKeyPair: VapidKeyPair = VapidKeyPair.load() ?: error("WEB_PUSH_VAPID_PUBLIC_KEY not configured"),
  private val http: HttpClient = httpClient(),
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

  fun send(subscription: PushSubscription, payload: ByteArray?, ttl: Duration = this.ttl): HttpResponse<String> {
    val encrypted = if (payload != null) encrypt(payload, subscription.keys) else null
    val jwt = createVapidJwt(subscription.endpoint)
    val key = vapidKeyPair.publicKey
    val req = HttpRequest.newBuilder()
      .uri(subscription.endpoint)
      .header("Content-Type", "webpush; enc=aes128gcm")
      .header("Content-Encoding", "aes128gcm")
      .header("TTL", ttl.inWholeSeconds.toString())
      .header("Urgency", "normal")
      .header("Authorization", "vapid t=$jwt, k=$key")
      .POST(if (encrypted != null) BodyPublishers.ofByteArray(encrypted) else BodyPublishers.noBody())
      .build()
    return http.send(req, HttpResponse.BodyHandlers.ofString())
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
    val sharedSecret = ecdh(vapidKeyPair.privateKey, browserPub)
    val authSecret = keys.auth.base64UrlDecode()
    val ikm = hkdfExtract(salt, sharedSecret)
    val prk = hkdfExtract(authSecret, ikm)
    var key = hkdfExpand(prk, "Content-Encoding: auth\u0000".toByteArray(), 32)
    key = hkdfExtract(salt, key)
    key = hkdfExpand(key, "Content-Encoding: aes128gcm\u0000".toByteArray(), 16)
    val nonce = ByteArray(12) { i -> (salt[i].toInt() xor RS_BYTES[i].toInt()).toByte() }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(byteArrayOf(0x02))
    val encrypted = cipher.doFinal(plaintext)
    return salt + RS_BYTES.copyOfRange(8, 12) + 0x02.toByte() + browserPubRaw + encrypted
  }

  private fun decodeEcPublicKey(encoded: ByteArray): ECPublicKey {
    require(encoded[0] == 0x04.toByte()) { "Expected uncompressed EC point" }
    val x = BigInteger(1, encoded.copyOfRange(1, 33))
    val y = BigInteger(1, encoded.copyOfRange(33, 65))
    val kf = KeyFactory.getInstance("EC")
    return kf.generatePublic(ECPublicKeySpec(ECPoint(x, y), P256_PARAMS)) as ECPublicKey
  }

  private fun ecdh(priv: ECPrivateKey, pub: ECPublicKey): ByteArray {
    val ka = KeyAgreement.getInstance("ECDH")
    ka.init(priv)
    ka.doPhase(pub, true)
    return ka.generateSecret()
  }

  private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt, "HmacSHA256"))
    return mac.doFinal(ikm)
  }

  private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
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
