package klite.ai

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.SnakeCase
import klite.base64Encode
import klite.json.JsonMapper
import klite.json.parse
import klite.nodes.Node
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.time.Instant

class OpenAIClientTest {
  @Test fun `usage is passed to the text response`() {
    val json = """{"input_tokens":1152,"input_tokens_details":{"cache_write_tokens":1149,"cached_tokens":5},
      "output_tokens":251,"output_tokens_details":{"reasoning_tokens":146},"total_tokens":1403}"""
    val usage = JsonMapper(keys = SnakeCase).parse<Node>(json)
    val output = OpenAIClient.Output("o1", "message", listOf(OpenAIClient.Content("output_text", "hi")))
    val response = OpenAIClient.Response("r1", Instant.now(), "completed", "gpt-5", listOf(output), usage)

    expect(response.toGenericResponse())
      .toEqual(AIClient.Response("r1", "completed", "gpt-5", "hi", AIClient.TokenUsage("gpt-5", 1152, 251, 5, 146)))
  }

  @Test fun `usage without token details defaults them to zero`() {
    val usage = JsonMapper(keys = SnakeCase).parse<Node>("""{"input_tokens":10,"output_tokens":20}""")
    val output = OpenAIClient.Output("o1", "message", listOf(OpenAIClient.Content("output_text", "hi")))
    val response = OpenAIClient.Response("r1", Instant.now(), "completed", "gpt-5", listOf(output), usage)

    expect(response.toGenericResponse().usage).toEqual(AIClient.TokenUsage("gpt-5", 10, 20))
  }

  @Test fun `text response has no usage when it is absent`() {
    val output = OpenAIClient.Output("o1", "message", listOf(OpenAIClient.Content("output_text", "hi")))
    val response = OpenAIClient.Response("r1", Instant.now(), "completed", "gpt-5", listOf(output))

    expect(response.toGenericResponse().usage).toEqual(null)
  }

  @Test fun `remote document is referenced by url`() {
    val url = URI("https://files.pixit.vet/p1/visit/report.pdf")

    // OpenAI rejects filename together with file_url as mutually exclusive parameters
    expect(url.toOpenAIContent()).toEqual(OpenAIClient.Content(type = "input_file", fileUrl = url))
  }

  @Test fun `remote image is referenced by url`() {
    val url = URI("https://files.pixit.vet/p1/vaccination/scan.jpg")

    expect(url.toOpenAIContent()).toEqual(OpenAIClient.Content(type = "input_image", imageUrl = url))
  }

  @Test fun `local document is inlined as base64 data`() {
    val file = File.createTempFile("report", ".pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    val content = file.toURI().toOpenAIContent()

    expect(content).toEqual(OpenAIClient.Content(type = "input_file", fileData = URI("data:application/pdf;base64,${file.readBytes().base64Encode()}"), filename = file.name))
  }

  @Test fun `local image is inlined as base64 data`() {
    val file = File.createTempFile("scan", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    val content = file.toURI().toOpenAIContent()

    expect(content).toEqual(OpenAIClient.Content(type = "input_image", imageUrl = URI("data:image/jpeg;base64,${file.readBytes().base64Encode()}")))
  }

  @Test fun `data url document is inlined as is`() {
    val data = URI("data:application/pdf;base64,AAAA")
    expect(data.toOpenAIContent()).toEqual(OpenAIClient.Content(type = "input_file", fileData = data, filename = "file.pdf"))
  }

  @Test fun `error message is taken from a rejected request`() {
    val body = """{"error":{"message":"Your input exceeds the context window of this model.","type":"invalid_request_error",
      "param":"input","code":"context_length_exceeded"}}"""

    expect(JsonMapper().errorMessageOrNull(body)).toEqual("Your input exceeds the context window of this model.")
  }

  @Test fun `error message is absent for a body that is not an error json`() {
    expect(JsonMapper().errorMessageOrNull("Bad Gateway")).toEqual(null)
  }
}
