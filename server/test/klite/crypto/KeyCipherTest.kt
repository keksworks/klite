package klite.crypto

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException

class KeyCipherTest {
  val keyGenerator = KeyGenerator()
  val keyCipher = KeyCipher(keyGenerator.keyFromSecret("my secret"))

  @Test fun `encrypt and decrypt`() {
    val enc1 = keyCipher.encrypt("Hello")
    val enc2 = keyCipher.encrypt("Hello")
    expect(enc1).notToEqual(enc2)
    expect(keyCipher.decrypt(enc1)).toEqual("Hello")
    expect(keyCipher.decrypt(enc2)).toEqual("Hello")
    expect { keyCipher.decrypt("1" + enc2.substring(1)) }.toThrow<AEADBadTagException>()

    // backwards-compatibility
    expect(keyCipher.decrypt("Toy5Uw8i-HGTYN49WJtarw")).toEqual("Hello")
    expect(keyCipher.decrypt("Toy5Uw8i+HGTYN49WJtarw==")).toEqual("Hello")
  }
}
