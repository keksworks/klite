package klite.ai

import klite.Config
import klite.SnakeCase
import klite.ValueConverter
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
  fun query(input: String): String
}

// https://platform.openai.com/docs/api-reference/making-requests
open class OpenAIClient(httpClient: HttpClient, json: JsonMapper = defaultSnakeMapper, val extraParams: Map<String, Any?> = emptyMap()): AIClient {
  private val defaultModel = Config["OPENAI_MODEL"]
  private val auth = "Bearer " + Config["OPENAI_API_KEY"]
  private val http = JsonHttpClient(Config.optional("OPENAI_URL", "https://api.openai.com/v1"), http = httpClient, json = json,
    reqModifier = { header("Authorization", auth).timeout(30.seconds) })

  override fun query(input: String): String = query(input, defaultModel)

  open fun query(input: String, model: String, temperature: Double = 1.0): String =
    http.post<Map<String, Any>>("/responses", extraParams + mapOf(
      "model" to model,
      "input" to input,
      "temperature" to temperature
    )).children<Node>("output").first().children<Node>("content").first().text("text")
}

// https://aistudio.google.com/prompts/new_chat
open class GeminiClient(httpClient: HttpClient, json: JsonMapper = defaultSnakeMapper, val extraParams: Map<String, Any?> = emptyMap()): AIClient {
  private val defaultModel = Config["GEMINI_MODEL"]
  private val key = Config["GEMINI_API_KEY"]
  private val http = JsonHttpClient(Config.optional("GEMINI_URL", "https://generativelanguage.googleapis.com/v1beta"), http = httpClient, json = json,
    reqModifier = { timeout(30.seconds) })

  override fun query(input: String): String = query(input, defaultModel)

  open fun query(input: String, model: String): String =
    http.post<Map<String, Any>>("/interactions?key=$key", extraParams + mapOf(
      "model" to model,
      "input" to input,
      "generation_config" to mapOf(
        "temperature" to 1,
        "max_output_tokens" to 65536,
        "top_p" to 0.95,
        "thinking_level" to "minimal"
      )
    )).children<Node>("steps").first { it.containsKey("content") }.children<Node>("content").first().text("text")
}
