package klite

import com.sun.management.OperatingSystemMXBean
import com.sun.management.UnixOperatingSystemMXBean
import klite.RequestMethod.GET
import klite.StatusCode.Companion.OK
import java.io.File
import java.io.OutputStream
import java.lang.System.nanoTime
import java.lang.management.ManagementFactory
import java.lang.management.MemoryUsage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

object Metrics {
  private val resolvers = ConcurrentHashMap<String, () -> Any?>()

  fun register(name: String, value: () -> Any?) {
    resolvers[name] = value
  }

  val data: Map<String, Any?> get() = resolvers.mapValues { it.value() }
}

data class Labeled(val labels: Map<String, Any?>, val value: Any?)

class Timer {
  private val count = LongAdder()
  private val totalNanos = LongAdder()
  private val maxNanos = AtomicLong()

  fun record(nanos: Long) {
    count.increment()
    totalNanos.add(nanos)
    maxNanos.accumulateAndGet(nanos, Math::max)
  }

  inline fun <T> time(block: () -> T): T {
    val start = nanoTime()
    try { return block() } finally { record(nanoTime() - start) }
  }

  fun snapshot() = mapOf("count" to count.sum(), "sum" to totalNanos.sum() / 1e9, "max" to maxNanos.getAndSet(0) / 1e9)
}

internal class RequestMetrics(private val pathParamRegexer: PathParamRegexer) {
  private data class Key(val method: RequestMethod, val prefix: String, val route: Route, val status: Int)
  private val timers = ConcurrentHashMap<Key, Timer>()

  init {
    Metrics.register("http_server_requests_seconds") { timers.map { (key, timer) -> Labeled(labels(key), timer.snapshot()) } }
  }

  fun record(e: HttpExchange, prefix: String, route: Route, nanos: Long) =
    timers.getOrPut(Key(e.method, prefix, route, e.statusCode?.value ?: 500)) { Timer() }.record(nanos)

  private fun labels(key: Key) = mapOf(
    "method" to key.method,
    "uri" to if (key.route is NotFoundRoute) "NOT_FOUND" else key.prefix + pathParamRegexer.toOpenApi(key.route.path),
    "status" to key.status,
    "outcome" to outcomes[key.status / 100 - 1]
  )

  private val outcomes = listOf("INFORMATIONAL", "SUCCESS", "REDIRECTION", "CLIENT_ERROR", "SERVER_ERROR")
}

fun Server.metrics(path: String = "/metrics", befores: Router.() -> Unit = {}) {
  context(path) {
    befores()
    metrics("")
  }
}

fun Router.metrics(path: String = "/metrics", keyPrefix: String = "", annotations: List<Annotation> = emptyList()) {
  registerJvmMetrics()
  val renderer = OpenMetricsRenderer(keyPrefix = keyPrefix)
  add(Route(GET, pathParamRegexer.from(path), annotations) {
    if (accept(renderer.contentType) || accept(MimeTypes.text) || accept.isRelaxed) renderer.render(this, OK, Metrics.data)
    else Metrics.data
  })
}

private fun registerJvmMetrics() {
  val runtime = ManagementFactory.getRuntimeMXBean()
  val memory = ManagementFactory.getMemoryMXBean()
  val threads = ManagementFactory.getThreadMXBean()
  val classes = ManagementFactory.getClassLoadingMXBean()
  val gcs = ManagementFactory.getGarbageCollectorMXBeans()
  val os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
  val linuxSelfStatus = File("/proc/self/status").takeIf { it.canRead() }

  Metrics.register("process_start_time_seconds") { runtime.startTime / 1000.0 }
  Metrics.register("process_uptime_seconds") { runtime.uptime / 1000.0 }
  Metrics.register("process_cpu_usage") { os.processCpuLoad }
  if (os is UnixOperatingSystemMXBean) {
    Metrics.register("process_files_open_files") { os.openFileDescriptorCount }
    Metrics.register("process_files_max_files") { os.maxFileDescriptorCount }
  }
  if (linuxSelfStatus != null) Metrics.register("process_resident_memory_bytes") {
    linuxSelfStatus.readLines().firstOrNull { it.startsWith("VmRSS") }?.substringAfter(":")?.substringBefore("kB")?.trim()?.toLongOrNull()?.times(1024)
  }

  Metrics.register("system_cpu_usage") { os.cpuLoad }
  Metrics.register("system_cpu_count") { os.availableProcessors }
  Metrics.register("system_load_average_1m") { os.systemLoadAverage }

  fun byArea(value: (MemoryUsage) -> Long) = listOf(Labeled(mapOf("area" to "heap"), value(memory.heapMemoryUsage)), Labeled(mapOf("area" to "nonheap"), value(memory.nonHeapMemoryUsage)))
  Metrics.register("jvm_memory_used_bytes") { byArea { it.used } }
  Metrics.register("jvm_memory_committed_bytes") { byArea { it.committed } }
  Metrics.register("jvm_memory_max_bytes") { byArea { it.max } }

  Metrics.register("jvm_gc_collection_seconds") { gcs.map { Labeled(mapOf("gc" to it.name), mapOf("count" to it.collectionCount, "sum" to it.collectionTime / 1000.0)) } }

  Metrics.register("jvm_threads_live_threads") { threads.threadCount }
  Metrics.register("jvm_threads_daemon_threads") { threads.daemonThreadCount }
  Metrics.register("jvm_threads_peak_threads") { threads.peakThreadCount }
  Metrics.register("jvm_classes_loaded_classes") { classes.loadedClassCount }
}

class OpenMetricsRenderer(
  override val contentType: String = "application/openmetrics-text",
  private val keyPrefix: String = "",
): BodyRenderer {
  override fun render(e: HttpExchange, code: StatusCode, value: Any?) {
    val contentType = if (e.header("Accept")?.contains(contentType) == true) "$contentType; version=1.0.0; charset=utf-8" else MimeTypes.text
    e.startResponse(code, contentType = contentType).use { render(it, value) }
  }

  override fun render(output: OutputStream, value: Any?) {
    val data = value as? Map<*, *> ?: return
    val families = linkedMapOf<String, MutableList<String>>()
    collect(families, keyPrefix, data, emptyMap())
    families.values.forEach { it.forEach(output::writeln) }
    output.writeln("# EOF")
  }

  private fun collect(families: MutableMap<String, MutableList<String>>, prefix: String, data: Map<*, *>, labels: Map<String, Any?>) {
    data.forEach { (k, v) ->
      val snake = SnakeCase.to(k.toString())
      collect(families, if (prefix.isEmpty()) snake else "${prefix}_$snake", v, labels)
    }
  }

  private fun collect(families: MutableMap<String, MutableList<String>>, key: String, value: Any?, labels: Map<String, Any?>) {
    when (value) {
      null -> {}
      is Labeled -> collect(families, key, value.value, labels + value.labels)
      is Iterable<*> -> value.forEach { collect(families, key, it, labels) }
      is Map<*, *> -> collect(families, key, value, labels)
      is Number -> families.getOrPut(key) { mutableListOf() } += "$key${render(labels)} $value"
      else -> families.getOrPut(key) { mutableListOf() } += "$key${render(labels + ("value" to value))} 1"
    }
  }

  private fun render(labels: Map<String, Any?>) =
    labels.filterValues { it != null }.let { if (it.isEmpty()) "" else it.entries.joinToString(",", "{", "}") { (k, v) -> "$k=\"${escape(v.toString())}\"" } }

  private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
