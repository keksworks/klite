package klite.ai

import klite.Config
import klite.SnakeCase
import klite.ValueConverter
import klite.http.httpClient
import klite.http.timeout
import klite.json.JsonHttpClient
import klite.json.JsonMapper
import klite.nodes.Node
import klite.nodes.children
import klite.nodes.text
import java.net.http.HttpClient
import java.time.Instant
import kotlin.reflect.KType
import kotlin.time.Duration.Companion.seconds

val defaultSnakeMapper = JsonMapper(keys = SnakeCase, values = object: ValueConverter<Any?>() {
  override fun to(o: Any?) = when (o) {
    is Instant -> o.epochSecond
    is Enum<*> -> o.name.lowercase()
    else -> o
  }
  override fun from(o: Any?, type: KType?) =
    if (o is String && type?.classifier == Instant::class) Instant.ofEpochSecond(o.toLong()) else o
})

interface AIClient {
  fun query(input: String, params: Node = emptyMap()): String
}

// https://platform.openai.com/docs/api-reference/making-requests
open class OpenAIClient(httpClient: HttpClient, json: JsonMapper = defaultSnakeMapper, val params: Node = emptyMap()): AIClient {
  val model = Config["OPENAI_MODEL"]
  private val auth = "Bearer " + Config["OPENAI_API_KEY"]
  private val http = JsonHttpClient(Config.optional("OPENAI_URL", "https://api.openai.com/v1"), http = httpClient, json = json,
    reqModifier = { header("Authorization", auth).timeout(30.seconds) })

  override fun query(input: String, params: Node): String =
    query(input, params, null).output.first { it.type == "message" }.content.first().text

  // TODO: try structured output with "text": {"format": {"type": "json_schema"}}}
  open fun query(input: Any /* String | List<Input> */, params: Node = emptyMap(), prevResponseId: String? = null): Response =
    http.post("/responses", mapOf(
      "model" to model,
      "input" to input,
      "previous_response_id" to prevResponseId,
    ) + this.params + params)

  data class Input(val content: List<Content>, val role: String = "user")
  data class Output(val id: String, val type: String, val content: List<Content>, val role: String? = null)
  data class Content(val type: String, val text: String)
  data class Response(val id: String, val createdAt: Instant, val status: String, val model: String, val output: List<Output>)
}

// https://aistudio.google.com/prompts/new_chat
open class GeminiClient(httpClient: HttpClient, json: JsonMapper = defaultSnakeMapper, val params: Node = emptyMap()): AIClient {
  val model = Config["GEMINI_MODEL"]
  private val key = Config["GEMINI_API_KEY"]
  private val http = JsonHttpClient(Config.optional("GEMINI_URL", "https://generativelanguage.googleapis.com/v1beta"), http = httpClient, json = json,
    reqModifier = { timeout(30.seconds) })

  override fun query(input: String, params: Node): String =
    http.post<Map<String, Any>>("/interactions?key=$key", mapOf(
      "model" to model,
      "input" to input,
      "generation_config" to mapOf(
        "temperature" to 1,
        "max_output_tokens" to 65536,
        "top_p" to 0.95,
        "thinking_level" to "minimal"
      )
    ) + this.params + params).children<Node>("steps").first { it.containsKey("content") }.children<Node>("content").first().text("text")
}
