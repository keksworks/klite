package klite.slf4j

/**
 * Use this logger if you want stack traces to be logged as a single log line in json format:
 * `Config["LOGGER_CLASS"] = StackTraceOptimizingJsonLogger::class.qualifiedName!!`
 */
open class StackTraceOptimizingJsonLogger(name: String): StackTraceOptimizingLogger(name) {
  override fun print(formatted: String, t: Throwable?) {
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
    sb.append(t.toString().replace("\"", "\\\"").replace("\n", "\\n"))
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
      sb.append("\"").append(line).append("\"")
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
