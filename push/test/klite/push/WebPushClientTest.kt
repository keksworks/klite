package klite.push

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.*

class WebPushClientTest {
  val keyPair = VapidKeyPair.generate()
  val client = WebPushClient(keyPair)

  @Test fun `generates valid P-256 key pair`() {
    expect(keyPair.publicKey.length).toEqual(87)
    expect(keyPair.privateKey.algorithm).toEqual("EC")
  }

  @Test fun `public key decodes to uncompressed EC point`() {
    val decoded = Base64.getUrlDecoder().decode(keyPair.publicKey)
    expect(decoded[0].toInt()).toEqual(0x04)
    expect(decoded.size).toEqual(65)
  }

  @Test fun `creates valid VAPID JWT`() {
    val jwt = client.createVapidJwt(URI.create("https://example.com/push")).toString()
    val dot1 = jwt.indexOf('.')
    val dot2 = jwt.indexOf('.', dot1 + 1)
    val headerB64 = jwt.substring(0, dot1)
    val payloadB64 = jwt.substring(dot1 + 1, dot2)

    val header = String(Base64.getUrlDecoder().decode(headerB64))
    expect(header).toEqual("""{"alg":"ES256","typ":"JWT"}""")

    val payload = String(Base64.getUrlDecoder().decode(payloadB64))
    expect(payload.contains("\"aud\":\"https://example.com\"")).toEqual(true)
    expect(payload.contains("\"sub\":\"mailto:push@klite.dev\"")).toEqual(true)
    expect(payload.contains("\"exp\":")).toEqual(true)
  }

  @Test fun `encrypts payload in aes128gcm format`() {
    val subKeyPair = generateTestKeyPair()
    val subPub = subKeyPair.public as ECPublicKey
    val x = subPub.w.affineX.toByteArray().let { if (it.size > 32) it.copyOfRange(1, 33) else it }
    val y = subPub.w.affineY.toByteArray().let { if (it.size > 32) it.copyOfRange(1, 33) else it }
    val p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(0x04) + x + y)
    val auth = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { it.toByte() })
    val sub = PushSubscription(URI.create("https://example.com/push"), SubscriptionKeys(p256dh, auth))
    val plaintext = "Hello, World!".toByteArray()
    val encrypted = client.encrypt(plaintext, sub.keys)
    // salt(16) + rs(4) + idlen(1) + pubKey(65) + (plaintext + 0x02 delimiter) + gcm_tag(16)
    val expectedSize = 16 + 4 + 1 + 65 + plaintext.size + 1 + 16
    expect(encrypted.size).toEqual(expectedSize)
  }

  @Test fun `encrypted message header has correct aes128gcm format`() {
    val subKeyPair = generateTestKeyPair()
    val subPub = subKeyPair.public as ECPublicKey
    val x = subPub.w.affineX.toByteArray().let { if (it.size > 32) it.copyOfRange(1, 33) else it }
    val y = subPub.w.affineY.toByteArray().let { if (it.size > 32) it.copyOfRange(1, 33) else it }
    val p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(0x04) + x + y)
    val auth = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { it.toByte() })
    val sub = PushSubscription(URI.create("https://example.com/push"), SubscriptionKeys(p256dh, auth))
    val encrypted = client.encrypt("test".toByteArray(), sub.keys)

    // salt(16) + rs(4) + idlen(1) + keyid(65) + ciphertext + tag(16)
    expect(encrypted.size > 16 + 4 + 1 + 65).toEqual(true)

    // idlen must be 65 (uncompressed P-256 point)
    expect(encrypted[20].toInt()).toEqual(65)

    // keyid must be sender's uncompressed public key (starts with 0x04)
    expect(encrypted[21].toInt()).toEqual(0x04)
    val senderPubRaw = Base64.getUrlDecoder().decode(keyPair.publicKey)
    expect(encrypted.copyOfRange(21, 86).contentEquals(senderPubRaw)).toEqual(true)
  }

  private fun generateTestKeyPair(): java.security.KeyPair {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    return kpg.generateKeyPair()
  }

  private fun decodeUncompressedPoint(base64: String, params: java.security.spec.ECParameterSpec): ECPublicKey {
    val raw = Base64.getUrlDecoder().decode(base64)
    val x = java.math.BigInteger(1, raw.copyOfRange(1, 33))
    val y = java.math.BigInteger(1, raw.copyOfRange(33, 65))
    return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
  }

  @Test fun `RFC 8291 Appendix A test vector`() {
    val asPublicB64 = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"
    val uaPublicB64 = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
    val authSecretB64 = "BTBZMqHH6r4Tts7J_aSIgg"
    val saltB64 = "DGv6ra1nlYgDCS1FRnbzlw"
    val expectedEcdhSecret = "kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs"
    val expectedPrkKey = "Snr3JMxaHVDXHWJn5wdC52WjpCtd2EIEGBykDcZW32k"
    val expectedIkm = "S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg"
    val expectedPrk = "09_eUZGrsvxChDCGRCdkLiDXrReGOEVeSCdCcPBSJSc"
    val expectedCek = "oIhVW04MRdy2XN9CiKLxTg"
    val expectedNonce = "4h_95klXJ5E_qnoN"

    val p256Params = (generateTestKeyPair().public as ECPublicKey).params
    val senderPrivScalar = java.math.BigInteger(1, Base64.getUrlDecoder().decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"))
    val senderPriv = KeyFactory.getInstance("EC").generatePrivate(
      java.security.spec.ECPrivateKeySpec(senderPrivScalar, p256Params)
    ) as ECPrivateKey
    val uaPub = decodeUncompressedPoint(uaPublicB64, p256Params)
    val testClient = WebPushClient(VapidKeyPair(asPublicB64, senderPriv))

    // Verify ECDH shared secret
    val ecdhSecret = testClient.ecdh(senderPriv, uaPub)
    expect(Base64.getUrlEncoder().withoutPadding().encodeToString(ecdhSecret)).toEqual(expectedEcdhSecret)

    // Phase 1: Combine ECDH + auth secrets
    val authBytes = Base64.getUrlDecoder().decode(authSecretB64)
    val prkKey = testClient.hkdfExtract(authBytes, ecdhSecret)
    expect(Base64.getUrlEncoder().withoutPadding().encodeToString(prkKey)).toEqual(expectedPrkKey)

    val uaPubRaw = Base64.getUrlDecoder().decode(uaPublicB64)
    val senderPubRaw = Base64.getUrlDecoder().decode(asPublicB64)
    val keyInfo = "WebPush: info\u0000".toByteArray() + uaPubRaw + senderPubRaw
    val ikm = testClient.hkdfExpand(prkKey, keyInfo, 32)
    expect(Base64.getUrlEncoder().withoutPadding().encodeToString(ikm)).toEqual(expectedIkm)

    // Phase 2: Derive CEK and nonce using salt
    val salt = Base64.getUrlDecoder().decode(saltB64)
    val prk = testClient.hkdfExtract(salt, ikm)
    expect(Base64.getUrlEncoder().withoutPadding().encodeToString(prk)).toEqual(expectedPrk)

    val cek = testClient.hkdfExpand(prk, "Content-Encoding: aes128gcm\u0000".toByteArray(), 16)
    expect(Base64.getUrlEncoder().withoutPadding().encodeToString(cek)).toEqual(expectedCek)

    val nonce = testClient.hkdfExpand(prk, "Content-Encoding: nonce\u0000".toByteArray(), 12)
    expect(Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)).toEqual(expectedNonce)
  }
}
