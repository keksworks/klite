import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.Converter
import klite.base64UrlDecode
import klite.base64UrlEncode
import klite.oauth.JWT
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant

class JWTTest {
  // signed with secret "your-256-bit-secret" at jwt.io
  val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

  @Test fun parse() {
    val jwt = JWT(token)
    expect(jwt.header.alg).toEqual("HS256")
    expect(jwt.header.typ).toEqual("JWT")
    expect(jwt.payload.subject).toEqual("1234567890")
    expect(jwt.payload.name).toEqual("John Doe")
    expect(jwt.payload.issuedAt).toEqual(Instant.ofEpochSecond(1516239022))
    expect(jwt.signature.decodeToString()).toEqual("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c".base64UrlDecode().decodeToString())
  }

  @Test fun `verify with valid secret`() {
    JWT(token).verify("your-256-bit-secret")
  }

  @Test fun `verify with wrong secret`() {
    assertThrows<IllegalArgumentException> { JWT(token).verify("wrong-secret") }
  }

  @Test fun converter() {
    expect(Converter.from<JWT>(token)).toEqual(JWT(token))
  }

  @Test fun `verify with RSA public key`() {
    val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val header = """{"alg":"RS256","typ":"JWT"}"""
    val payload = """{"sub":"1234567890","name":"John Doe","iat":1516239022}"""
    val signingInput = "${header.toByteArray().base64UrlEncode()}.${payload.toByteArray().base64UrlEncode()}"
    val sig = rsa().apply {
      initSign(kp.private)
      update(signingInput.toByteArray())
    }
    val signature = sig.sign().base64UrlEncode()
    val rsaToken = "$signingInput.$signature"
    JWT(rsaToken).verify(kp.public)
  }

  @Test fun `verify with wrong RSA public key`() {
    val kp1 = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val kp2 = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val header = """{"alg":"RS256","typ":"JWT"}"""
    val payload = """{"sub":"1234567890","name":"John Doe","iat":1516239022}"""
    val signingInput = "${header.toByteArray().base64UrlEncode()}.${payload.toByteArray().base64UrlEncode()}"
    val sig = rsa().apply {
      initSign(kp1.private)
      update(signingInput.toByteArray())
    }
    val signature = sig.sign().base64UrlEncode()
    val rsaToken = "$signingInput.$signature"
    assertThrows<IllegalArgumentException> { JWT(rsaToken).verify(kp2.public) }
  }

  private fun rsa() = Signature.getInstance("SHA256withRSA")
}
