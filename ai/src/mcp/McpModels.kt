package klite.ai.mcp

import klite.Config
import klite.json.toJsonSchema
import klite.nodes.Node
import kotlin.reflect.KFunction
import kotlin.reflect.full.valueParameters

data class JsonRpcResponse(val jsonrpc: String = "2.0", val id: Any?, val result: Any? = null, val error: JsonRpcError? = null)

data class JsonRpcError(val code: Int, val message: String)

data class InitializeResult(
  val protocolVersion: String = "2025-03-26",
  val capabilities: Node = mapOf("tools" to emptyMap<String, Any>()),
  val serverInfo: ServerInfo = ServerInfo(),
)

data class ServerInfo(val name: String = "MCP Server", val version: String = Config.optional("VERSION", "dev"))

data class ToolsListResult(val tools: List<Tool>)

data class Tool(val name: String, val description: String, val inputSchema: ToolSchema)

data class ToolSchema(val type: String = "object", val properties: Node, val required: List<String> = emptyList())

data class ToolCallResult(val content: List<ToolContent>)

data class ToolContent(val type: String = "text", val text: String)

data class ResourcesListResult(val resources: List<Any> = emptyList())

data class JsonRpcRequest(val jsonrpc: String = "2.0", val id: Any? = null, val method: String, val params: Node = emptyMap())

fun Pair<KFunction<*>, String>.toTool(): Tool {
  val (f, description) = this
  val params = f.valueParameters.drop(1) // skip context parameter
  val required = params.filter { !it.isOptional && !it.type.isMarkedNullable }.map { it.name!! }
  val properties = params.associate { param ->
    param.name!! to (param.type.toJsonSchema() ?: mapOf("type" to "string"))
  }
  return Tool(f.name, description, ToolSchema(properties = properties, required = required))
}
