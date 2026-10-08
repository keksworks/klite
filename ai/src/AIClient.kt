package klite.ai

import klite.Config
import klite.MimeTypes
import klite.nodes.Node
import java.net.URI
import kotlin.time.Duration.Companion.seconds

interface AIClient {
  val timeout get() = Config.optional("AI_TIMEOUT_SEC", "30").toInt().seconds
  val maxLoggedLen get() = Config.optional("AI_LOG_LEN", "20000").toInt()

  fun query(input: String, vararg fileUrl: URI, prevResponseId: String? = null, params: Node = emptyMap()): Response

  fun stream(input: String, vararg fileUrl: URI, params: Node = emptyMap()): Sequence<String>

  data class Response(val id: String?, val status: String, val model: String, val text: String)
}

internal val URI.isImage get() = when (scheme) {
  "data" -> schemeSpecificPart.startsWith("image/")
  else -> MimeTypes.typeFor(path ?: "").orEmpty().startsWith("image/")
}
