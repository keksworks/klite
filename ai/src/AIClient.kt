package klite.ai

import klite.json.JsonMapper
import klite.json.parse
import klite.nodes.Node
import java.net.URI

interface AIClient {
  fun query(input: String, imageUrl: URI? = null, prevResponseId: String? = null, params: Node = emptyMap()): Response

  fun stream(input: String, imageUrl: URI? = null, params: Node = emptyMap()): Sequence<String> =
    throw UnsupportedOperationException("Streaming not supported by ${this::class.simpleName}")

  data class Response(val id: String?, val status: String, val model: String, val text: String)
}

internal fun Sequence<String>.parseJSON(json: JsonMapper, extractText: (Node) -> String?): Sequence<String> =
  map { json.parse<Node>(it) }.mapNotNull { extractText(it) }
