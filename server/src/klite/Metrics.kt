package klite

import klite.RequestMethod.GET
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

context(Server)
fun Router.metrics(path: String = "/metrics", annotations: List<Annotation> = emptyList()) {
  (workerPool as? ForkJoinPool)?.let {
    Metrics.register("workerPool") {
      mapOf("active" to it.activeThreadCount, "size" to it.poolSize, "max" to it.parallelism)
    }
  }

  Runtime.getRuntime().let {
    val mb = 1024f * 1024f
    Metrics.register("heapMb") {
      mapOf("free" to it.freeMemory() / mb, "total" to it.totalMemory() / mb, "max" to it.maxMemory() / mb)
    }
  }

  // TODO: add Prometheus-style output formatter
  add(Route(GET, pathParamRegexer.from(path), annotations) {
    Metrics.data
  })
}
