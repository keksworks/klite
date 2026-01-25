import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.Email
import klite.oauth.extractName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class OAuthClientKtTest {

  @ParameterizedTest
  @MethodSource("emailTestCases")
  fun `use capitalized email local part as name`(emailAddress: String, expectedName: String) {
    val email = Email(emailAddress)
    expect(email.extractName()).toEqual(expectedName)
  }

  companion object {
    @JvmStatic
    fun emailTestCases(): Stream<Arguments> =
      Stream.of(
        Arguments.of("foo@bar.com", "Foo"),
        Arguments.of("fOo@bar.com", "Foo"),
        Arguments.of("john.doe@example.com", "John.doe"),
        Arguments.of("alice@example.org", "Alice"),
        Arguments.of("bob-smith@mail.co.uk", "Bob-smith"),
        Arguments.of("1234@numeric.com", "1234"),
        Arguments.of("UPPERCASE@example.com", "Uppercase")
      )
  }
}
