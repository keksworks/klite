package klite.ai

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import io.mockk.verify
import klite.HttpExchange
import klite.StatusCode
import klite.UnauthorizedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KFunction

class McpRoutesTest {
  val exchange = mockk<HttpExchange>(relaxed = true)

  class TestRoutes(info: ServerInfo = ServerInfo()) : McpRoutes(info) {
    var authenticatedUser: String? = "test-user"
    override val tools: List<Pair<KFunction<*>, String>> by lazy { listOf(
      this::greet to "Greet someone",
      this::add to "Add two numbers",
      this::nullableTool to "Nullable tool",
    ) }
    override fun authenticate(exchange: HttpExchange): Any? = authenticatedUser

    fun greet(context: String, name: String): String = "Hello, $name! (from $context)"
    fun add(context: String, a: Int, b: Int): Int = a + b
    fun nullableTool(context: String, value: String? = null): String = value ?: "default"
  }

  val routes = TestRoutes()

  @Test fun `GET returns MethodNotAllowed`() {
    routes.get(exchange)
    verify { exchange.send(StatusCode.MethodNotAllowed) }
  }

  @Test fun `rpc returns error when unauthorized`() {
    routes.authenticatedUser = null
    val request = JsonRpcRequest(id = 1, method = "initialize")
    assertThrows<UnauthorizedException> { routes.rpc(exchange, request) }
  }

  @Test fun `rpc handles notifications_initialized`() {
    val request = JsonRpcRequest(id = 5, method = "notifications/initialized")
    val response = routes.rpc(exchange, request)
    expect(response).toEqual(JsonRpcResponse(id = 5))
  }

  @Test fun `rpc wraps result in JsonRpcResponse`() {
    val request = JsonRpcRequest(id = 10, method = "initialize")
    val response = routes.rpc(exchange, request)
    expect(response.id).toEqual(10)
    expect(response.result).toEqual(InitializeResult(serverInfo = ServerInfo()))
    expect(response.error).toEqual(null)
  }

  @Test fun `rpc catches exceptions and returns error response`() {
    val request = JsonRpcRequest(id = 20, method = "unknown")
    val response = routes.rpc(exchange, request)
    expect(response.id).toEqual(20)
    expect(response.result).toEqual(null)
    expect(response.error).toEqual(JsonRpcError(-32603, "Unknown method: unknown"))
  }

  @Test fun `handleRequest initialize returns InitializeResult`() {
    val result = routes.handleRequest("ctx", "initialize", emptyMap())
    expect(result).toEqual(InitializeResult(serverInfo = ServerInfo()))
  }

  @Test fun `handleRequest tools_list returns ToolsListResult`() {
    val result = routes.handleRequest("ctx", "tools/list", emptyMap())
    val tools = (result as ToolsListResult).tools
    expect(tools.size).toEqual(3)
    expect(tools[0].name).toEqual("greet")
    expect(tools[0].description).toEqual("Greet someone")
    expect(tools[1].name).toEqual("add")
    expect(tools[1].description).toEqual("Add two numbers")
  }

  @Test fun `handleRequest resources_list returns empty ResourcesListResult`() {
    val result = routes.handleRequest("ctx", "resources/list", emptyMap())
    expect(result).toEqual(ResourcesListResult())
  }

  @Test fun `handleRequest unknown method throws`() {
    expect { routes.handleRequest("ctx", "bogus", emptyMap()) }
      .toThrow<IllegalArgumentException>()
  }

  @Test fun `handleToolCall with valid tool and string args`() {
    val params = mapOf("name" to "greet", "arguments" to mapOf("name" to "World"))
    val result = routes.handleToolCall("test-user", params)
    expect(result.content.size).toEqual(1)
    expect(result.content[0].text).toEqual("\"Hello, World! (from test-user)\"")
  }

  @Test fun `handleToolCall converts int arguments`() {
    val params = mapOf("name" to "add", "arguments" to mapOf("a" to 3, "b" to 4))
    val result = routes.handleToolCall("ctx", params)
    expect(result.content[0].text).toEqual("7")
  }

  @Test fun `handleToolCall with missing optional arg uses default`() {
    val params = mapOf("name" to "nullableTool", "arguments" to emptyMap<String, Any?>())
    val result = routes.handleToolCall("ctx", params)
    expect(result.content[0].text).toEqual("\"default\"")
  }

  @Test fun `handleToolCall with unknown tool throws`() {
    val params = mapOf("name" to "nonexistent", "arguments" to emptyMap<String, Any?>())
    expect { routes.handleToolCall("ctx", params) }
      .toThrow<IllegalArgumentException>()
  }

  @Test fun `handleToolCall with no arguments map uses defaults`() {
    val params = mapOf<String, Any?>("name" to "nullableTool")
    val result = routes.handleToolCall("ctx", params)
    expect(result.content[0].text).toEqual("\"default\"")
  }
}
