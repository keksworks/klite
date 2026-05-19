package klite.crypto

import klite.base64UrlDecode
import klite.base64UrlEncode
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.Cipher.ENCRYPT_MODE
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyCipher(private val key: SecretKey) {
  private val random = SecureRandom()
  private val algorithm = key.algorithm.substringBefore("/") + "/GCM/NoPadding"

  private fun cipherFor(mode: Int, iv: ByteArray) = Cipher.getInstance(algorithm).apply {
    init(mode, key, GCMParameterSpec(128, iv))
  }

  fun encrypt(input: String): String {
    val iv = ByteArray(12).also { random.nextBytes(it) }
    val cipher = cipherFor(ENCRYPT_MODE, iv)
    val encrypted = cipher.doFinal(input.toByteArray())
    return (iv + encrypted).base64UrlEncode()
  }

  fun decrypt(input: String): String {
    val bytes = input.base64UrlDecode()
    try {
      val iv = bytes.copyOfRange(0, 12)
      val cipher = cipherFor(DECRYPT_MODE, iv)
      return cipher.doFinal(bytes, 12, bytes.size - 12).decodeToString()
    } catch (e: Exception) {
      try {
        // Backwards-compatibility with non-GCM mode
        val cipher = Cipher.getInstance(key.algorithm).apply { init(DECRYPT_MODE, key) }
        return cipher.doFinal(bytes).decodeToString()
      } catch (_: Exception) {
        throw e
      }
    }
  }
}
