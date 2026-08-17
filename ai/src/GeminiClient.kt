package klite.ai

import klite.Config
import klite.SnakeCase
import klite.http.timeout
import klite.json.JsonHttpClient
import klite.json.JsonMapper
import klite.nodes.Node
import java.net.URI
import java.net.http.HttpClient
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

// https://aistudio.google.com/prompts/new_chat
open class GeminiClient(httpClient: HttpClient, val params: Node = emptyMap()): AIClient {
  val model = Config["GEMINI_MODEL"]
  private val key = Config["GEMINI_API_KEY"]
  private val http = JsonHttpClient(
    Config.optional("GEMINI_URL", "https://generativelanguage.googleapis.com/v1beta"), http = httpClient,
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
