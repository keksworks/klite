package klite.ai

import klite.Config
import klite.MimeTypes
import klite.SnakeCase
import klite.base64Encode
import klite.http.parseSSE
import klite.http.postStreaming
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

  override fun query(input: String, imageUrl: URI?, prevResponseId: String?, params: Node): AIClient.Response =
    query(toInput(input, imageUrl), params, prevResponseId).toTextResponse()

  override fun stream(input: String, imageUrl: URI?, params: Node): Sequence<String> {
    val body = http.json.render(mapOf(
      "model" to model, "input" to toInput(input, imageUrl),
      "generation_config" to GenerationConfig(), "stream" to true
    ) + this.params + params)
    return http.http.postStreaming(URI(http.baseUrl + "/interactions?key=$key"), body, http.reqModifier)
      .parseSSE().parseJSON(http.json) { node ->
        ((node["candidates"] as? List<*>)?.firstOrNull() as? Node)
          ?.let { (it["content"] as? Node) }
          ?.let { (it["parts"] as? List<*>)?.firstOrNull() as? Node }
          ?.let { it["text"] as? String }
      }
  }

  private fun toInput(input: String, imageUrl: URI?): Any = if (imageUrl != null) listOf(
    Content("text", input),
    Content("image", data = imageUrl.toURL().readBytes().base64Encode(), mimeType = MimeTypes.typeFor(imageUrl.path)!!)
  ) else input

  fun query(input: Any /* String | List<Content | Step> */, params: Node = emptyMap(), prevInteractionId: String? = null): Response =
    http.post("/interactions?key=$key", mapOf(
      "model" to model,
      "input" to input,
      "generation_config" to GenerationConfig(),
      "previous_interaction_id" to prevInteractionId
    ) + this.params + params)

  data class GenerationConfig(val thinkingLevel: String? = null, val temperature: Int = 1, val maxOutputTokens: Int? = null)
  data class Step(val type: String, val content: List<Content>? = null, val signature: String? = null)
  data class Content(val type: String, val text: String? = null, val uri: URI? = null, val data: String? = null, val mimeType: String? = null)
  data class Response(val id: String, val status: String, val steps: List<Step>, val usage: Node, val created: Instant, val model: String) {
    fun toTextResponse() = AIClient.Response(id, status, model, steps.first { it.content != null }.content!!.first().text!!)
  }
}
