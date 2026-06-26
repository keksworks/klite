package klite.slf4j

import klite.Config
import org.slf4j.MDC
import org.slf4j.event.Level
import java.net.InetAddress
import java.time.Instant

/**
 * Use this logger if you want all logs to be json lines with ECS spec compatibility:
 * `Config["LOGGER_CLASS"] = EcsJsonLogger::class.qualifiedName!!`
 */
open class EcsJsonLogger(name: String): StackTraceOptimizingLogger(name) {
  companion object {
    private val levels = Level.entries.associateWith { it.name.lowercase() }
    private val serviceName = Config.optional("LOGGER_SERVICE_NAME")
    private val serviceVersion = Config.optional("LOGGER_SERVICE_VERSION")
    private val hostname = InetAddress.getLocalHost().hostName
  }

  override fun print(level: Level, msg: String?, t: Throwable?) {
    val sb = StringBuilder(123)
    sb.append('{')
    sb.put("@timestamp", Instant.ofEpochMilli(System.currentTimeMillis()).toString())
    sb.put("trace.id", Thread.currentThread().name) // TODO: split into http.request.id/etc?
    sb.put("log.level", levels[level])
    sb.put("log.logger", name)
    sb.put("message", msg)
    sb.put("service.name", serviceName)
    sb.put("service.version", serviceVersion)
    MDC.getCopyOfContextMap()?.forEach { (key, value) -> sb.put(key, value) }
    if (t != null) {
      sb.put("error.type", t.javaClass.name)
      sb.put("error.message", t.message)
      sb.append("\"error.stack_trace\":\"")
      val stackTrace = t.stackTrace
      for (i in 0..findUsefulStackTraceEnd(stackTrace)) {
        sb.append(stackTrace[i]).append("\\n")
      }
      sb.append("\",")
    }
    sb.put("host.hostname", hostname, isLast = true)
    sb.append('}')
    out.println(sb)
  }

  private fun StringBuilder.put(key: String, value: Any?, isLast: Boolean = false) {
    if (value == null) return
    append('"')
    append(key)
    append("\":\"")
    append(value.toString().replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\""))
    append('"')
    if (!isLast) append(',')
  }
}
