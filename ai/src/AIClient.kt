package klite.ai

import klite.nodes.Node

interface AIClient {
  fun query(input: String, params: Node = emptyMap()): String
}
