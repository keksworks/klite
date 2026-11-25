package klite

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import klite.RequestMethod.GET
import org.junit.jupiter.api.Test
import kotlin.reflect.full.findAnnotation

annotation class Public

class RouteTest {
  @Test fun annotations() {
    val route = Route(GET, "".toRegex()) {}
    expect(route.findAnnotation<Public>()).toEqual(null)

    val annotated = Route(GET, "".toRegex(), listOf(Public())) {}
    expect(annotated.findAnnotation<Public>()).notToEqualNull()
  }

  @Test fun pathParams() {
    val route = Route(GET, PathParamRegexer().from("/hello/:name/:id")) {}
    val match = route.pathRegex.matchEntire("/hello/world/42")!!
    val params = PathParams(match.groups, route.namedGroups)
    expect(params["name"]).toEqual("world")
    expect(params["id"]).toEqual("42")
    expect(params["missing"]).toEqual(null)
    expect(params.containsKey("missing")).toEqual(false)
  }

  @Test fun `string route remembers its original path`() {
    val router = Router("/api", mockk(), PathParamRegexer(), emptyList(), emptyList(), emptyList())

    expect(router.get("/users/:id") {}.path).toEqual("/users/:id")
  }

  @Test fun `metrics use original path syntax`() {
    val router = Router("", mockk(), PathParamRegexer(), emptyList(), emptyList(), emptyList())
    val route = router.get("/users/:id") {}
    val exchange = mockk<HttpExchange> {
      every { method } returns GET
      every { statusCode } returns null
    }

    RequestMetrics().record(exchange, "", route, 0)

    val metrics = Metrics.data["http_server_requests_seconds"] as List<Labeled>
    expect(metrics.single().labels["uri"]).toEqual("/users/:id")
  }

  @Test fun `static and regex routes`() {
    val router = Router("/api", mockk(), PathParamRegexer(), emptyList(), emptyList(), emptyList())
    val static = router.get("/v1") {}
    val escaped = router.get("/v\\d".toRegex()) {}
    expect(router.route(exchange("/api/v1"))?.first).toEqual(static)
    expect(router.route(exchange("/api/v2"))?.first).toEqual(escaped)
    expect(router.route(exchange("/api/vx"))).toEqual(null)
  }

  private fun exchange(path: String) = mockk<HttpExchange> {
    every { method } returns GET
    every { this@mockk.path } returns path
  }
}
