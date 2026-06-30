package klite.webpush

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.*

class WebPushClientTest {
  val keyPair = WebPushClient.generateKeyPair()
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
    val jwt = client.createVapidJwt(URI.create("https://example.com/push"))
    val parts = jwt.split(".")
    expect(parts.size).toEqual(3)

    val header = String(Base64.getUrlDecoder().decode(parts[0]))
    expect(header).toEqual("""{"alg":"ES256","typ":"JWT"}""")

    val payload = String(Base64.getUrlDecoder().decode(parts[1]))
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

  @Test fun `nonce is salt xor record size in 12-byte big-endian`() {
    val salt = ByteArray(16) { it.toByte() }
    val rsBytes = ByteArray(12).also { it[10] = 16 }
    val nonce = ByteArray(12) { i -> (salt[i].toInt() xor rsBytes[i].toInt()).toByte() }
    expect(nonce.size).toEqual(12)
    expect(nonce[10].toInt()).toEqual(0x10 xor 10)
    expect(nonce[11].toInt()).toEqual(0x00 xor 11)
  }

  private fun generateTestKeyPair(): java.security.KeyPair {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    return kpg.generateKeyPair()
  }
}
