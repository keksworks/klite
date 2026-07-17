package klite.push

import klite.Config
import klite.base64UrlDecode
import klite.base64UrlEncode
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

data class VapidKeyPair(val publicKey: String, val privateKey: ECPrivateKey) {
  companion object {
    fun generate(): VapidKeyPair {
      val kpg = KeyPairGenerator.getInstance("EC")
      kpg.initialize(ECGenParameterSpec("secp256r1"))
      val kp = kpg.generateKeyPair()
      val pub = kp.public as ECPublicKey
      val x = pub.w.affineX.toByteArray().padTo(32)
      val y = pub.w.affineY.toByteArray().padTo(32)
      val uncompressed = byteArrayOf(0x04) + x + y
      return VapidKeyPair(uncompressed.base64UrlEncode(), kp.private as ECPrivateKey)
    }

    /** Load VapidKeyPair from WEB_PUSH_VAPID_PUBLIC_KEY and WEB_PUSH_VAPID_PRIVATE_KEY config/env vars */
    fun load(): VapidKeyPair? {
      val pubKey = Config.optional("WEB_PUSH_VAPID_PUBLIC_KEY") ?: return null
      val privKeyBytes = Config["WEB_PUSH_VAPID_PRIVATE_KEY"].base64UrlDecode()
      val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privKeyBytes)) as ECPrivateKey
      return VapidKeyPair(pubKey, privateKey)
    }

    /** Generate VAPID key pair and print env vars for .env file */
    @JvmStatic fun main(args: Array<String>) {
      val keyPair = generate()
      val privKeyBytes = keyPair.privateKey.encoded
      println("WEB_PUSH_VAPID_PUBLIC_KEY=${keyPair.publicKey}")
      println("WEB_PUSH_VAPID_PRIVATE_KEY=${privKeyBytes.base64UrlEncode()}")
    }
  }
}

private fun ByteArray.padTo(n: Int) = when {
  size == n -> this
  size > n -> copyOfRange(size - n, size)
  else -> ByteArray(n - size) + this
}
