package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class OpenMetricsRendererTest {
  @Test fun render() {
    val out = ByteArrayOutputStream()
    OpenMetricsRenderer(keyPrefix = "test").render(out, mapOf("myNumber" to 42, "myMap" to mapOf("aValue" to 3.14), "myInfo" to "text"))
    expect(out.toString()).toEqual("""
      test_my_number 42
      test_my_map_a_value 3.14
      test_my_info{value="text"} 1
      # EOF
    """.trimIndent() + "\n")
  }
}
