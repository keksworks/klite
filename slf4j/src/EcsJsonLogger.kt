package klite.slf4j

import klite.Config
import org.slf4j.event.Level
import java.net.InetAddress
import java.time.Instant

/**
 * Use this logger if you want all logs to be json lines with ECS spec compatibility:
 * `Config["LOGGER_CLASS"] = EcsJsonLogger::class.qualifiedName!!`
 */
open class EcsJsonLogger(name: String): StackTraceOptimizingJsonLogger(name) {
  companion object {
    private val levels = Level.entries.associateWith { it.name.lowercase() }
    private val serviceName = Config.optional("LOGGER_SERVICE_NAME")
    private val serviceVersion = Config.optional("LOGGER_SERVICE_VERSION")
    private val hostname = InetAddress.getLocalHost().hostName
  }

  override fun formatMessage(level: Level, msg: String?): String {
    val sb = StringBuilder(123)
    sb.append('{')
    sb.put("@timestamp", Instant.now())
    sb.put("log.level", levels[level])
    sb.put("message", msg)
    sb.put("service.name", serviceName)
    sb.put("service.version", serviceVersion)
    sb.put("host.hostname", hostname, isLast = true)
    sb.append('}')
    return sb.toString()
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

  override fun print(formatted: String, t: Throwable?) {
    out.println(formatted)
  }
}
