package klite.http

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.net.http.HttpClient

class TypedHttpClientTest {
  @Test fun `logger name should get container class name`() {
    expect(object: TypedHttpClient(http = HttpClient.newHttpClient(), contentType = "") {}.logger.name)
      .toEqual(javaClass.name)
  }
}
