package klite.ai

import klite.Config
import klite.SnakeCase
import klite.ValueConverter
import klite.http.timeout
import klite.json.JsonHttpClient
import klite.json.JsonMapper
import klite.nodes.Node
import klite.nodes.text
import klite.toBase64Url
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.time.Instant
import kotlin.reflect.KType

// https://platform.openai.com/docs/api-reference/making-requests
open class OpenAIClient(
  httpClient: HttpClient,
  baseUrl: String = Config.optional("OPENAI_URL", "https://api.openai.com/v1"),
  apiKey: String = Config["OPENAI_API_KEY"],
  val model: String = Config["OPENAI_MODEL"],
  val params: Node = emptyMap()
): AIClient {
  private val auth = "Bearer " + apiKey
  private val http = JsonHttpClient(baseUrl, http = httpClient, json = JsonMapper(keys = SnakeCase, values = instantAsInt),
    maxLoggedLen = maxLoggedLen, reqModifier = { header("Authorization", auth).timeout(timeout) })

  override fun query(input: String, vararg fileUrl: URI, prevResponseId: String?, params: Node): AIClient.Response =
    query(toInput(input, fileUrl), params, prevResponseId).toTextResponse()

  override fun stream(input: String, vararg fileUrl: URI, params: Node): Sequence<String> =
    http.postSSE<Node>("/responses", mapOf("model" to model, "input" to toInput(input, fileUrl), "stream" to true) + this.params + params).mapNotNull { node ->
      if (node.text("type") == "response.output_text.delta") node.text("delta") else null
    }

  private fun toInput(input: String, fileUrl: Array<out URI>): Any = if (fileUrl.isNotEmpty()) listOf(Input(listOf(
    Content(text = input, type = "input_text")
  ) + fileUrl.map { it.toOpenAIContent() })) else input

  // TODO: try structured output with "text": {"format": {"type": "json_schema"}}}
  open fun query(input: Any /* String | List<Input | Output> */, params: Node = emptyMap(), prevResponseId: String? = null): Response =
    http.post("/responses", mapOf("model" to model, "input" to input, "previous_response_id" to prevResponseId) + this.params + params)

  data class Input(val content: List<Content>, val role: String = "user")
  data class Output(val id: String, val type: String, val content: List<Content> = emptyList(), val role: String? = null)
  data class Content(val type: String, val text: String? = null, val imageUrl: URI? = null, val fileUrl: URI? = null,
                     val fileData: URI? = null, val filename: String? = null, val detail: String? = null)
  data class Response(val id: String, val createdAt: Instant, val status: String, val model: String, val output: List<Output>) {
    fun toTextResponse() = AIClient.Response(id, status, model, output.first { it.type == "message" }.content.first().text!!)
  }
}

internal fun URI.toOpenAIContent(): OpenAIClient.Content = when {
  isImage -> OpenAIClient.Content(imageUrl = if (scheme == "file") File(path).toBase64Url() else this, type = "input_image")
  scheme == "http" || scheme == "https" -> OpenAIClient.Content(fileUrl = this, filename = fileName, type = "input_file")
  else -> OpenAIClient.Content(fileData = if (scheme == "file") File(path).toBase64Url() else this, filename = fileName, type = "input_file")
}

private val URI.fileName: String? get() = path?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

private val instantAsInt = object: ValueConverter<Any?>() {
  override fun to(o: Any?) = when (o) {
    is Instant -> o.epochSecond
    is Enum<*> -> o.name.lowercase()
    else -> o
  }
  override fun from(o: Any?, type: KType?) =
    if (o is String && type?.classifier == Instant::class) Instant.ofEpochSecond(o.toLong()) else o
}
