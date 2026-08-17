package klite.ai

import klite.Config
import klite.SnakeCase
import klite.ValueConverter
import klite.http.httpClient
import klite.http.timeout
import klite.json.JsonHttpClient
import klite.json.JsonMapper
import klite.nodes.Node
import java.net.URI
import java.net.http.HttpClient
import java.time.Instant
import kotlin.reflect.KType
import kotlin.time.Duration.Companion.seconds

private val instantAsInt = object: ValueConverter<Any?>() {
  override fun to(o: Any?) = when (o) {
    is Instant -> o.epochSecond
    is Enum<*> -> o.name.lowercase()
    else -> o
  }
  override fun from(o: Any?, type: KType?) =
    if (o is String && type?.classifier == Instant::class) Instant.ofEpochSecond(o.toLong()) else o
}

interface AIClient {
  fun query(input: String, params: Node = emptyMap()): String
}

// https://platform.openai.com/docs/api-reference/making-requests
open class OpenAIClient(httpClient: HttpClient, val params: Node = emptyMap()): AIClient {
  val model = Config["OPENAI_MODEL"]
  private val auth = "Bearer " + Config["OPENAI_API_KEY"]
  private val http = JsonHttpClient(Config.optional("OPENAI_URL", "https://api.openai.com/v1"), http = httpClient,
    json = JsonMapper(keys = SnakeCase, values = instantAsInt),
    reqModifier = { header("Authorization", auth).timeout(30.seconds) })

  override fun query(input: String, params: Node): String =
    query(input, params, null).output.first { it.type == "message" }.content.first().text!!

  // TODO: try structured output with "text": {"format": {"type": "json_schema"}}}
  open fun query(input: Any /* String | List<Input> */, params: Node = emptyMap(), prevResponseId: String? = null): Response =
    http.post("/responses", mapOf(
      "model" to model,
      "input" to input,
      "previous_response_id" to prevResponseId,
    ) + this.params + params)

  data class Input(val content: List<Content>, val role: String = "user")
  data class Output(val id: String, val type: String, val content: List<Content>, val role: String? = null)
  data class Content(val type: String, val text: String? = null)
  data class Response(val id: String, val createdAt: Instant, val status: String, val model: String, val output: List<Output>)
}

// https://aistudio.google.com/prompts/new_chat
open class GeminiClient(httpClient: HttpClient, val params: Node = emptyMap()): AIClient {
  val model = Config["GEMINI_MODEL"]
  private val key = Config["GEMINI_API_KEY"]
  private val http = JsonHttpClient(Config.optional("GEMINI_URL", "https://generativelanguage.googleapis.com/v1beta"), http = httpClient,
    json = JsonMapper(keys = SnakeCase),
    reqModifier = { timeout(30.seconds) })

  override fun query(input: String, params: Node): String =
    query(input as Any, params).steps.first { it.content != null }.content!!.first().text!!

  fun query(input: Any /* String | List<Content | Step> */, params: Node = emptyMap(), prevInteractionId: String? = null): Response =
    http.post("/interactions?key=$key", mapOf(
      "model" to model,
      "input" to input,
      "generation_config" to GenerationConfig(),
      "previous_interaction_id" to prevInteractionId
    ) + this.params + params)

  data class GenerationConfig(val thinkingLevel: String? = null, val temperature: Int = 1, val maxOutputTokens: Int? = null)
  data class Response(val id: String, val status: String, val steps: List<Step>, val usage: Node, val created: Instant, val model: String)
  data class Step(val type: String, val content: List<Content>? = null, val signature: String? = null)
  data class Content(val type: String, val text: String? = null, val uri: URI? = null, val mimeType: String? = null)
}
