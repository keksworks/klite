package klite.push

import klite.Config
import klite.base64UrlDecode
import klite.base64UrlEncode
import klite.html.escapeJs
import klite.http.httpClient
import kotlinx.coroutines.future.await
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
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

data class PushSubscription(val endpoint: URI, val keys: SubscriptionKeys)
data class SubscriptionKeys(val p256dh: String, val auth: String)
data class VapidKeyPair(val publicKey: String, val privateKey: ECPrivateKey)

private fun ByteArray.paddedTo(targetLen: Int): ByteArray {
  return when {
    size == targetLen -> this
    size > targetLen -> copyOfRange(size - targetLen, size)
    else -> {
      val result = ByteArray(targetLen)
      System.arraycopy(this, 0, result, targetLen - size, size)
      result
    }
  }
}

class WebPushClient(
  private val vapidKeyPair: VapidKeyPair,
  private val http: HttpClient = httpClient(),
  private val ttl: Int = 86400,
  private val jwtSub: String = Config.optional("WEB_PUSH_SUB", "mailto:push@klite.dev"),
) {
  companion object {
    fun generateKeyPair(): VapidKeyPair {
      val kpg = KeyPairGenerator.getInstance("EC")
      kpg.initialize(ECGenParameterSpec("secp256r1"))
      val kp = kpg.generateKeyPair()
      val pub = kp.public as ECPublicKey
      val x = pub.w.affineX.toByteArray().paddedTo(32)
      val y = pub.w.affineY.toByteArray().paddedTo(32)
      val uncompressed = byteArrayOf(0x04) + x + y
      return VapidKeyPair(uncompressed.base64UrlEncode(), kp.private as ECPrivateKey)
    }

    private val P256_PARAMS: ECParameterSpec by lazy {
      val dummyKpg = KeyPairGenerator.getInstance("EC")
      dummyKpg.initialize(ECGenParameterSpec("secp256r1"))
      (dummyKpg.generateKeyPair().public as ECPublicKey).params
    }
  }

  suspend fun send(subscription: PushSubscription, payload: ByteArray?, ttl: Int = this.ttl): HttpResponse<String> {
    val encrypted = if (payload != null) encrypt(payload, subscription.keys) else null
    val jwt = createVapidJwt(subscription.endpoint)
    val key = vapidKeyPair.publicKey
    val req = HttpRequest.newBuilder()
      .uri(subscription.endpoint)
      .header("Content-Type", "webpush; enc=aes128gcm")
      .header("Content-Encoding", "aes128gcm")
      .header("TTL", ttl.toString())
      .header("Urgency", "normal")
      .header("Authorization", "vapid t=$jwt, k=$key")
      .POST(if (encrypted != null) BodyPublishers.ofByteArray(encrypted) else BodyPublishers.noBody())
      .build()
    return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).await()
  }

  internal fun createVapidJwt(endpoint: URI): String {
    val header = """{"alg":"ES256","typ":"JWT"}"""
    val now = Instant.now().epochSecond
    val claims = """{"aud":"${endpoint.scheme}://${endpoint.host}","exp":${now + 43200},"sub":"${jwtSub.escapeJs()}"}"""
    val headerB64 = header.toByteArray().base64UrlEncode()
    val claimsB64 = claims.toByteArray().base64UrlEncode()
    val signedPart = "$headerB64.$claimsB64"
    val sigBytes = es256Sign(signedPart.toByteArray(), vapidKeyPair.privateKey)
    return "$signedPart.${sigBytes.base64UrlEncode()}"
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
    val rs = 4096
    val rsBytes = ByteArray(12)
    rsBytes[8] = ((rs shr 24) and 0xff).toByte()
    rsBytes[9] = ((rs shr 16) and 0xff).toByte()
    rsBytes[10] = ((rs shr 8) and 0xff).toByte()
    rsBytes[11] = (rs and 0xff).toByte()
    val nonce = ByteArray(12) { i -> (salt[i].toInt() xor rsBytes[i].toInt()).toByte() }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD(byteArrayOf(0x02))
    val encrypted = cipher.doFinal(plaintext)
    val keyId = salt + byteArrayOf(
      ((rs shr 24) and 0xff).toByte(), ((rs shr 16) and 0xff).toByte(),
      ((rs shr 8) and 0xff).toByte(), (rs and 0xff).toByte(), 0x02
    ) + browserPubRaw
    return keyId + encrypted
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

  private fun es256Sign(data: ByteArray, priv: ECPrivateKey): ByteArray {
    val sig = Signature.getInstance("SHA256withECDSA")
    sig.initSign(priv)
    sig.update(data)
    val der = sig.sign()
    return derToP1363(der)
  }

  private fun derToP1363(der: ByteArray): ByteArray {
    var idx = 0
    idx++ // SEQUENCE tag
    idx++ // length byte
    if (der[idx - 1].toInt().and(0x80) != 0) {
      val lenBytes = der[idx - 1].toInt().and(0x7f)
      idx += lenBytes
    }
    idx++ // INTEGER tag for r
    var len = der[idx].toInt().and(0xff); idx++
    if (len == 0) { len = der[idx].toInt().and(0xff) or (der[idx + 1].toInt().and(0xff) shl 8); idx += 2 }
    var r = der.copyOfRange(idx, idx + len); idx += len
    if (r[0].toInt() == 0) r = r.copyOfRange(1, r.size)
    idx++ // INTEGER tag for s
    len = der[idx].toInt().and(0xff); idx++
    if (len == 0) { len = der[idx].toInt().and(0xff) or (der[idx + 1].toInt().and(0xff) shl 8); idx += 2 }
    var s = der.copyOfRange(idx, idx + len)
    if (s[0].toInt() == 0) s = s.copyOfRange(1, s.size)
    return r.paddedTo(32) + s.paddedTo(32)
  }
}
