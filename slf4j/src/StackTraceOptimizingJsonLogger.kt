package klite.slf4j

import org.slf4j.event.Level

/**
 * Use this logger if you want stack traces to be logged as a single log line in json format:
 * `Config["LOGGER_CLASS"] = StackTraceOptimizingJsonLogger::class.qualifiedName!!`
 */
open class StackTraceOptimizingJsonLogger(name: String): StackTraceOptimizingLogger(name) {
  override fun print(level: Level, msg: String?, t: Throwable?) {
    val formatted = formatMessage(level, msg)
    val sb = StringBuilder()
    if (formatted.isNotEmpty()) {
      sb.append(formatted)
      sb.append(" ")
    }
    if (t != null) appendJson(sb, t)
    out.println(sb)
  }

  protected fun appendJson(sb: StringBuilder, t: Throwable) {
    sb.append("""{"error":"""")
    sb.appendJsonEscaped(t.toString())
    sb.append("""","stack":[""")
    val stack = t.stackTrace
    val until = findUsefulStackTraceEnd(stack)
    for (i in 0..until) {
      var line = stack[i].toString()
      if (i < until) {
        val prev = stack[i + 1]
        line = line.substringAfter(prev.className)
        val prevPackage = prev.className.substringBeforeLast(".")
        line = line.substringAfter(prevPackage)
      }
      sb.append("\"").appendJsonEscaped(line).append("\"")
      if (i != until) sb.append(",")
    }
    sb.append("]")
    t.cause?.let {
      sb.append(""","cause":""")
      appendJson(sb, it)
    }
    sb.append("}")
  }
}

internal fun StringBuilder.appendJsonEscaped(s: String) = apply {
  for (c in s) when (c) {
    '"' -> append("\\\"")
    '\\' -> append("\\\\")
    '\n' -> append("\\n")
    '\r' -> append("\\r")
    '\t' -> append("\\t")
    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
  }
}
