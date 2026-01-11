package klite

import klite.RequestMethod.GET
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool

object Metrics {
  private val resolvers = ConcurrentHashMap<String, () -> Any?>().apply {
    val startedAt = Instant.now()
    put("startedAt") { startedAt }
  }

  fun register(name: String, value: () -> Any?) {
    resolvers[name] = value
  }

  val data: Map<String, Any?> get() = resolvers.mapValues { it.value() }
}

fun Server.metrics(path: String = "/metrics", befores: Router.() -> Unit = {}) {
  context(path) {
    befores()
    metrics("")
  }
}

context(server: Server)
fun Router.metrics(path: String = "/metrics", keyPrefix: String = "", annotations: List<Annotation> = emptyList()) {
  (server.workerPool as? ForkJoinPool)?.let {
    Metrics.register("workerPool") {
      mapOf("active" to it.activeThreadCount, "size" to it.poolSize, "max" to it.parallelism)
    }
  }

  Runtime.getRuntime().let {
    val mb = 1024f * 1024f
    Metrics.register("heapMb") {
      mapOf("used" to (it.totalMemory() - it.freeMemory()) / mb, "size" to it.totalMemory() / mb, "max" to it.maxMemory() / mb)
    }
  }

  this.renderers.add(0, OpenMetricsRenderer(keyPrefix = keyPrefix))
  add(Route(GET, this.pathParamRegexer.from(path), annotations) {
    Metrics.data
  })
}

class OpenMetricsRenderer(
  override val contentType: String = "application/openmetrics-text",
  private val keyPrefix: String = "",
): BodyRenderer {
  override fun render(e: HttpExchange, code: StatusCode, value: Any?) {
    val contentType = if (e.requestType?.startsWith(contentType) == true) contentType else MimeTypes.text
    e.startResponse(code, contentType = contentType).use { render(it, value) }
  }

  override fun render(output: OutputStream, value: Any?) {
    val data = value as? Map<*, *> ?: return
    render(output, keyPrefix, data)
    output.writeln("# EOF")
  }

  fun render(out: OutputStream, prefix: String, data: Map<*, *>) {
    data.forEach { (k, v) ->
      val snake = SnakeCase.to(k.toString())
      val key = if (prefix.isEmpty()) snake else "${prefix}_$snake"
      when (v) {
        is Map<*, *> -> render(out, key, v)
        is Number -> out.writeln("$key $v")
        else -> out.writeln("$key{value=\"$v\"} 1")
      }
    }
  }
}
