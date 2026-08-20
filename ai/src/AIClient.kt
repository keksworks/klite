package klite.ai

import klite.nodes.Node
import java.net.URI

interface AIClient {
  fun query(input: String, imageUrl: URI? = null, prevResponseId: String? = null, params: Node = emptyMap()): Response

  data class Response(val id: String?, val status: String, val model: String, val text: String)
}
