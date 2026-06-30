import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.Converter
import klite.base64UrlDecode
import klite.oauth.JWT
import klite.oauth.JWT.Header
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.time.Instant

class JWTTest {
  // signed with secret "your-256-bit-secret" at jwt.io
  val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
  val jwt = JWT(token)

  @Test fun parse() {
    expect(jwt.header.alg).toEqual("HS256")
    expect(jwt.header.typ).toEqual("JWT")
    expect(jwt.payload.subject).toEqual("1234567890")
    expect(jwt.payload.name).toEqual("John Doe")
    expect(jwt.payload.issuedAt).toEqual(Instant.ofEpochSecond(1516239022))
    expect(jwt.signature.decodeToString()).toEqual("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c".base64UrlDecode().decodeToString())
  }

  @Test fun `verify with valid secret`() {
    jwt.verify("your-256-bit-secret")
  }

  @Test fun `verify with wrong secret`() {
    assertThrows<IllegalArgumentException> { JWT(token).verify("wrong-secret") }
  }

  @Test fun converter() {
    expect(Converter.from<JWT>(token)).toEqual(JWT(token))
  }

  @Test fun `verify with RSA public key`() {
    val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val jwt = JWT(Header(mapOf("alg" to "RS256", "typ" to "JWT")), jwt.payload)
    val signed = jwt.sign(kp.private)
    signed.verify(kp.public)
  }

  @Test fun `verify with wrong RSA public key`() {
    val kp1 = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val kp2 = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val jwt = JWT(Header(mapOf("alg" to "RS256", "typ" to "JWT")), jwt.payload)
    val signed = jwt.sign(kp1.private)
    assertThrows<IllegalArgumentException> { signed.verify(kp2.public) }
  }

  @Test fun `sign and verify with ES256`() {
    val kp = KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val jwt = JWT(Header(mapOf("alg" to "ES256", "typ" to "JWT")), jwt.payload)
    val signed = jwt.sign(kp.private)
    signed.verify(kp.public)
  }

  @Test fun `verify with wrong EC public key`() {
    val kp1 = KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val kp2 = KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val jwt = JWT(Header(mapOf("alg" to "ES256", "typ" to "JWT")), jwt.payload)
    val signed = jwt.sign(kp1.private)
    assertThrows<IllegalArgumentException> { signed.verify(kp2.public) }
  }
}
