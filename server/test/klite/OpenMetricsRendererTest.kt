package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import klite.StatusCode.Companion.OK
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class OpenMetricsRendererTest {
  val renderer = OpenMetricsRenderer()
  val exchange = mockk<HttpExchange>(relaxed = true)

  @Test fun `openmetrics content type if accepted`() {
    every { exchange.header("Accept") } returns "application/openmetrics-text;version=1.0.0,text/plain;version=0.0.4;q=0.5"
    renderer.render(exchange, OK, emptyMap<String, Any>())
    verify { exchange.startResponse(OK, contentType = "application/openmetrics-text; version=1.0.0; charset=utf-8") }
  }

  @Test fun `text content type by default`() {
    every { exchange.header("Accept") } returns null
    renderer.render(exchange, OK, emptyMap<String, Any>())
    verify { exchange.startResponse(OK, contentType = MimeTypes.text) }
  }

  @Test fun render() {
    val out = ByteArrayOutputStream()
    OpenMetricsRenderer(keyPrefix = "test").render(out, mapOf("myNumber" to 42, "myMap" to mapOf("aValue" to 3.14), "myInfo" to "text", "missing" to null,
      "jvm_memory_used_bytes" to Labeled("area", mapOf("heap" to 100, "nonheap" to 50))))
    expect(out.toString()).toEqual("""
      test_my_number 42
      test_my_map_a_value 3.14
      test_my_info{value="text"} 1
      test_jvm_memory_used_bytes{area="heap"} 100
      test_jvm_memory_used_bytes{area="nonheap"} 50
      # EOF
    """.trimIndent() + "\n")
  }
}
