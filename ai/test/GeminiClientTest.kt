package klite.ai

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.SnakeCase
import klite.ai.AIClient.TokenUsage
import klite.base64Encode
import klite.json.JsonMapper
import klite.json.parse
import klite.nodes.Node
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.time.Instant

class GeminiClientTest {
  @Test fun `usage is passed to the text response`() {
    val json = """{"total_tokens":16,"total_input_tokens":3,"total_cached_tokens":1,"total_output_tokens":9,"total_thought_tokens":4}"""
    val usage = JsonMapper(keys = SnakeCase).parse<Node>(json)
    val steps = listOf(GeminiClient.Step("thought", signature = "sig"),
      GeminiClient.Step("model_output", listOf(GeminiClient.Content("text", "hi"))))
    val response = GeminiClient.Response("i1", "completed", steps, usage, Instant.now(), "gemini-3.1-flash-lite")

    expect(response.toGenericResponse())
      .toEqual(AIClient.Response("i1", "completed", "gemini-3.1-flash-lite", "hi", TokenUsage("gemini-3.1-flash-lite", 3, 9, 1, 4)))
  }

  @Test fun `local document is inlined as base64 data`() {
    val file = File.createTempFile("report", ".pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    expect(file.toURI().toGeminiContent())
      .toEqual(GeminiClient.Content(type = "document", data = file.readBytes().base64Encode(), mimeType = "application/pdf"))
  }

  @Test fun `local image is inlined as base64 data`() {
    val file = File.createTempFile("scan", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    expect(file.toURI().toGeminiContent())
      .toEqual(GeminiClient.Content(type = "image", data = file.readBytes().base64Encode(), mimeType = "image/jpeg"))
  }

  @Test fun `data url document is inlined as is`() {
    val data = URI("data:application/pdf;base64,AAAA")

    expect(data.toGeminiContent()).toEqual(GeminiClient.Content(type = "document", data = "AAAA", mimeType = "application/pdf"))
  }

  @Test fun `data url image is inlined as is`() {
    val data = URI("data:image/png;base64,AAAA")

    expect(data.toGeminiContent()).toEqual(GeminiClient.Content(type = "image", data = "AAAA", mimeType = "image/png"))
  }

  @Test fun `data url without a media type has no mime type`() {
    val data = URI("data:;base64,AAAA")

    expect(data.toGeminiContent()).toEqual(GeminiClient.Content(type = "document", data = "AAAA"))
  }
}
