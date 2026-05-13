package klite.crypto

import klite.Config
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class KeyGenerator(
  val keyFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256"),
  private val keySalt: String? = Config.optional("KEY_SALT", "SaltyKlite"),
) {
  fun hash(password: String, salt: String?): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt?.toByteArray(), 65536, 256)
    return keyFactory.generateSecret(spec).encoded
  }

  fun keyFromSecret(password: String, salt: String? = keySalt): SecretKey {
    return SecretKeySpec(hash(password, salt), "AES")
  }
}
