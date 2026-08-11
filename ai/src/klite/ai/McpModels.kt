package klite.ai

import klite.Config
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure

data class JsonRpcResponse(val jsonrpc: String = "2.0", val id: Any?, val result: Any? = null, val error: JsonRpcError? = null)

data class JsonRpcError(val code: Int, val message: String)

data class InitializeResult(
  val protocolVersion: String = "2025-03-26",
  val capabilities: Map<String, Any> = mapOf("tools" to emptyMap<String, Any>()),
  val serverInfo: ServerInfo = ServerInfo(),
)

data class ServerInfo(val name: String = "MCP Server", val version: String = Config.optional("VERSION", "dev"))

data class ToolsListResult(val tools: List<Tool>)

data class Tool(val name: String, val description: String, val inputSchema: ToolSchema)

data class ToolSchema(val type: String = "object", val properties: Map<String, Any>, val required: List<String> = emptyList())

data class ToolCallResult(val content: List<ToolContent>)

data class ToolContent(val type: String = "text", val text: String)

data class ResourcesListResult(val resources: List<Any> = emptyList())

data class JsonRpcRequest(val jsonrpc: String = "2.0", val id: Any? = null, val method: String, val params: Map<String, Any?> = emptyMap())

fun Pair<KFunction<*>, String>.toTool(): Tool {
  val (f, description) = this
  val params = f.valueParameters.drop(1) // skip context parameter
  val required = mutableListOf<String>()
  val properties = mutableMapOf<String, Any>()
  for (param in params) {
    val name = param.name!!
    if (!param.type.isMarkedNullable) required += name
    val prop = mutableMapOf<String, Any>("type" to param.type.toJsonSchemaType())
    param.type.jvmErasure.enumValues()?.let { prop["enum"] = it }
    properties[name] = prop
  }
  return Tool(f.name, description, ToolSchema(properties = properties, required = required))
}

private fun KClass<*>.enumValues(): List<String>? =
  if (isSubclassOf(Enum::class)) java.enumConstants.map { (it as Enum<*>).name } else null

private fun KType.toJsonSchemaType(): String = when (classifier) {
  Long::class, Int::class, Short::class, Byte::class -> "number"
  Double::class, Float::class -> "number"
  Boolean::class -> "boolean"
  else -> "string"
}
