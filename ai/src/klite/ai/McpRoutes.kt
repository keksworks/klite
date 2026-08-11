package klite.ai

import klite.*
import klite.StatusCode.Companion.MethodNotAllowed
import klite.annotations.GET
import klite.annotations.POST
import klite.json.JsonMapper
import klite.nodes.text
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.valueParameters

abstract class McpRoutes(
  protected val info: ServerInfo = ServerInfo(),
) {
  protected val jsonMapper = JsonMapper()
  protected val log = logger()

  abstract val tools: List<Pair<KFunction<*>, String>>
  abstract fun authenticate(exchange: HttpExchange): Any?

  @GET fun get(e: HttpExchange) {
    e.send(MethodNotAllowed)
  }

  @POST fun rpc(e: HttpExchange, request: JsonRpcRequest): JsonRpcResponse {
    val context = authenticate(e) ?: throw UnauthorizedException()
    if (request.method == "notifications/initialized") return JsonRpcResponse(id = request.id)
    log.info(request.toString())
    return try {
      JsonRpcResponse(id = request.id, result = handleRequest(context, request.method, request.params))
    } catch (e: Exception) {
      JsonRpcResponse(id = request.id, error = JsonRpcError(-32603, e.message ?: "Internal error"))
    }
  }

  open fun handleRequest(context: Any, method: String, params: Map<String, Any?>): Any = when (method) {
    "initialize" -> InitializeResult(serverInfo = info)
    "tools/list" -> ToolsListResult(tools.map { it.toTool() })
    "tools/call" -> handleToolCall(context, params)
    "resources/list" -> ResourcesListResult()
    else -> throw IllegalArgumentException("Unknown method: $method")
  }

  @Suppress("UNCHECKED_CAST")
  open fun handleToolCall(context: Any, params: Map<String, Any?>): ToolCallResult {
    val toolName = params.text("name")
    val args = (params["arguments"] as? Map<String, Any?>) ?: emptyMap()
    val func = tools.firstOrNull { it.first.name == toolName }?.first
      ?: throw IllegalArgumentException("Unknown tool: $toolName")
    val funcParams = mutableMapOf<KParameter, Any?>()
    funcParams[func.valueParameters.first()] = context
    for (param in func.valueParameters.drop(1)) {
      val value = args[param.name] ?: continue
      funcParams[param] = convertValue(value, param.type)
    }
    val result = func.callBy(funcParams)
    val json = jsonMapper.render(result)
    return ToolCallResult(listOf(ToolContent(text = json)))
  }

  open fun convertValue(value: Any, targetType: KType): Any? = when (value) {
    is String -> Converter.from(value, targetType)
    else -> value
  }
}
