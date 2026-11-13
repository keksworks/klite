package klite

import com.sun.management.OperatingSystemMXBean
import com.sun.management.UnixOperatingSystemMXBean
import klite.RequestMethod.GET
import java.io.File
import java.io.OutputStream
import java.lang.management.ManagementFactory
import java.lang.management.MemoryUsage
import java.util.concurrent.ConcurrentHashMap

object Metrics {
  private val resolvers = ConcurrentHashMap<String, () -> Any?>()

  fun register(name: String, value: () -> Any?) {
    // TODO: check if already exists, e.g. dbPool
    resolvers[name] = value
  }

  val data: Map<String, Any?> get() = resolvers.mapValues { it.value() }
}

class Labeled(val label: String, values: Map<String, Any?>): Map<String, Any?> by values

fun Server.metrics(path: String = "/metrics", befores: Router.() -> Unit = {}) {
  context(path) {
    befores()
    metrics("")
  }
}

context(server: Server)
fun Router.metrics(path: String = "/metrics", keyPrefix: String = "", annotations: List<Annotation> = emptyList()) {
  registerJvmMetrics()
  this.renderers.add(0, OpenMetricsRenderer(keyPrefix = keyPrefix))
  add(Route(GET, this.pathParamRegexer.from(path), annotations) {
    Metrics.data
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

  fun byArea(value: (MemoryUsage) -> Long) = Labeled("area", mapOf("heap" to value(memory.heapMemoryUsage), "nonheap" to value(memory.nonHeapMemoryUsage)))
  Metrics.register("jvm_memory_used_bytes") { byArea { it.used } }
  Metrics.register("jvm_memory_committed_bytes") { byArea { it.committed } }
  Metrics.register("jvm_memory_max_bytes") { byArea { it.max } }

  Metrics.register("jvm_gc_collection_seconds_count") { Labeled("gc", gcs.associate { it.name to it.collectionCount }) }
  Metrics.register("jvm_gc_collection_seconds_sum") { Labeled("gc", gcs.associate { it.name to it.collectionTime / 1000.0 }) }

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
    render(output, keyPrefix, data)
    output.writeln("# EOF")
  }

  fun render(out: OutputStream, prefix: String, data: Map<*, *>) {
    data.forEach { (k, v) ->
      val snake = SnakeCase.to(k.toString())
      val key = if (prefix.isEmpty()) snake else "${prefix}_$snake"
      when (v) {
        null -> {}
        is Labeled -> v.forEach { (label, n) -> out.writeln("$key{${v.label}=\"$label\"} $n") }
        is Map<*, *> -> render(out, key, v)
        is Number -> out.writeln("$key $v")
        else -> out.writeln("$key{value=\"$v\"} 1")
      }
    }
  }
}
