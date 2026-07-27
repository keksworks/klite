package klite.push

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
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
    // salt(16) + rs(4) + delimiter(1) + pubKey(65) + ciphertext(plaintext.size) + tag(16)
    val expectedSize = 16 + 4 + 1 + 65 + plaintext.size + 16
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
}
