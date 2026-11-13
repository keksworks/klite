package klite.ai

import klite.*
import klite.ai.AIClient.TokenUsage
import klite.nodes.Node
import klite.nodes.child
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

interface AIClient {
  val timeout get() = Config.optional("AI_TIMEOUT_SEC", "30").toInt().seconds
  val maxLoggedLen get() = Config.optional("AI_LOG_LEN", "20000").toInt()

  fun query(input: String, vararg fileUrl: URI, prevResponseId: String? = null, params: Node = emptyMap()): Response

  fun stream(input: String, vararg fileUrl: URI, params: Node = emptyMap()): Sequence<String>

  data class Response(val id: String?, val status: String, val model: String, val text: String, val usage: TokenUsage? = null)
  data class TokenUsage(val model: String, val input: Long, val output: Long, val cached: Long = 0, val thought: Long = 0, val params: Node = emptyMap()) {
    operator fun plus(other: TokenUsage) = TokenUsage(model, input + other.input, output + other.output, cached + other.cached, thought + other.thought, params + other.params)
  }
}

object TokenMetrics {
  private val totals = ConcurrentHashMap<String, TokenUsage>()

  init {
    Metrics.register("gen_ai_client_token_usage_total") {
      totals.values.flatMap { u ->
        mapOf("input" to u.input, "output" to u.output, "cached" to u.cached, "thought" to u.thought).map { (type, n) ->
          Labeled(mapOf("gen_ai_response_model" to u.model, "gen_ai_token_type" to type), n)
        }
      }
    }
  }

  fun record(usage: TokenUsage) = totals.merge(usage.model, usage.copy(params = emptyMap()), TokenUsage::plus)
}

operator fun TokenUsage?.plus(other: TokenUsage?): TokenUsage? = if (this == null) other else other?.let { this + it } ?: this

internal fun Node.tokens(key: String) = child<Number>(key).toLong()

internal val URI.isImage get() = mimeType.orEmpty().startsWith("image/")

internal val URI.mimeType: String? get() = when (scheme) {
  "data" -> schemeSpecificPart.substringBefore(';').substringBefore(',').ifEmpty { null }
  else -> MimeTypes.typeFor(path ?: "")
}

internal fun URI.inlineBase64() = if (scheme == "data") schemeSpecificPart.substringAfter(',') else toURL().readBytes().base64Encode()
