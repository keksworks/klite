package klite.ai

import klite.Config
import klite.MimeTypes
import klite.SnakeCase
import klite.base64Encode
import klite.http.timeout
import klite.json.JsonHttpClient
import klite.json.JsonMapper
import klite.nodes.Node
import klite.nodes.at
import klite.nodes.textOrNull
import java.net.URI
import java.net.http.HttpClient
import java.time.Instant

// https://aistudio.google.com/prompts/new_chat
open class GeminiClient(
  httpClient: HttpClient,
  baseUrl: String = Config.optional("GEMINI_URL", "https://generativelanguage.googleapis.com/v1beta"),
  val apiKey: String = Config["GEMINI_API_KEY"],
  val model: String = Config["GEMINI_MODEL"],
  val params: Node = emptyMap()
): AIClient {
  private val http = JsonHttpClient(baseUrl, http = httpClient, json = JsonMapper(keys = SnakeCase),
    maxLoggedLen = maxLoggedLen, reqModifier = { timeout(timeout) })

  override fun query(input: String, vararg fileUrl: URI, prevResponseId: String?, params: Node): AIClient.Response =
    query(toInput(input, fileUrl), params, prevResponseId).toTextResponse()

  override fun stream(input: String, vararg fileUrl: URI, params: Node): Sequence<String> =
    http.postSSE<Node>("/interactions?key=$apiKey", mapOf("model" to model, "input" to toInput(input, fileUrl), "stream" to true) + this.params + params, eventName = "step.delta").mapNotNull { node ->
      node.at("delta").textOrNull("text")
    }

  private fun toInput(input: String, fileUrl: Array<out URI>): Any = if (fileUrl.isNotEmpty()) listOf(
    Content("text", input)
  ) + fileUrl.map {
    Content(if (it.isImage) "image" else "document", data = it.toURL().readBytes().base64Encode(), mimeType = MimeTypes.typeFor(it.path))
  } else input

  fun query(input: Any /* String | List<Content | Step> */, params: Node = emptyMap(), prevInteractionId: String? = null): Response =
    http.post("/interactions?key=$apiKey", mapOf(
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
