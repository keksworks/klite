package klite.ai

import klite.Config
import klite.SnakeCase
import klite.ValueConverter
import klite.http.timeout
import klite.json.JsonHttpClient
import klite.json.JsonMapper
import klite.nodes.Node
import klite.toBase64Url
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.time.Instant
import kotlin.reflect.KType
import kotlin.time.Duration.Companion.seconds

// https://platform.openai.com/docs/api-reference/making-requests
open class OpenAIClient(httpClient: HttpClient, val params: Node = emptyMap()): AIClient {
  val model = Config["OPENAI_MODEL"]
  private val auth = "Bearer " + Config["OPENAI_API_KEY"]
  private val http = JsonHttpClient(
    Config.optional("OPENAI_URL", "https://api.openai.com/v1"), http = httpClient,
    json = JsonMapper(keys = SnakeCase, values = instantAsInt),
    reqModifier = { header("Authorization", auth).timeout(30.seconds) })

  override fun query(input: String, imageUrl: URI?, params: Node): String =
    query(if (imageUrl != null) listOf(Input(listOf(
      Content(text = input, type = "input_text"),
      Content(imageUrl = if (imageUrl.scheme == "file") File(imageUrl.path).toBase64Url() else imageUrl, type = "input_image")
    ))) else input, params, null)
    .output.first { it.type == "message" }.content.first().text!!

  // TODO: try structured output with "text": {"format": {"type": "json_schema"}}}
  open fun query(input: Any /* String | List<Input | Output> */, params: Node = emptyMap(), prevResponseId: String? = null): Response =
    http.post("/responses", mapOf(
      "model" to model,
      "input" to input,
      "previous_response_id" to prevResponseId,
    ) + this.params + params)

  data class Input(val content: List<Content>, val role: String = "user")
  data class Output(val id: String, val type: String, val content: List<Content>, val role: String? = null)
  data class Content(val type: String, val text: String? = null, val imageUrl: URI? = null, val detail: String? = null)
  data class Response(val id: String, val createdAt: Instant, val status: String, val model: String, val output: List<Output>)
}

private val instantAsInt = object: ValueConverter<Any?>() {
  override fun to(o: Any?) = when (o) {
    is Instant -> o.epochSecond
    is Enum<*> -> o.name.lowercase()
    else -> o
  }
  override fun from(o: Any?, type: KType?) =
    if (o is String && type?.classifier == Instant::class) Instant.ofEpochSecond(o.toLong()) else o
}
