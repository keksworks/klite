package klite.ai

import klite.nodes.Node
import java.net.URI

interface AIClient {
  fun query(input: String, imageUrl: URI? = null, params: Node = emptyMap()): String
}
