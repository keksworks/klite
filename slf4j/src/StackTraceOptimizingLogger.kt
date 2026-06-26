package klite.slf4j

import org.slf4j.event.Level

/**
 * Use this logger if you want shorter stack traces:
 * `Config["LOGGER_CLASS"] = StackTraceOptimizingLogger::class.qualifiedName!!`
 */
open class StackTraceOptimizingLogger(name: String): KliteLogger(name) {
  public override fun print(level: Level, msg: String?, t: Throwable?): Unit = synchronized(out) {
    val formatted = formatMessage(level, msg)
    if (formatted.isNotEmpty()) {
      out.print(formatted)
      if (t == null) out.println()
      else if (!formatted.endsWith(" ")) out.print(": ")
    }
    if (t != null) print(t)
  }

  private fun print(t: Throwable) {
    out.println(t)
    val stackTrace = t.stackTrace
    for (i in 0..findUsefulStackTraceEnd(stackTrace)) {
      out.print("  at ")
      out.println(stackTrace[i])
    }
    t.cause?.let { out.print("Caused by: "); print(it) }
  }

  protected fun findUsefulStackTraceEnd(trace: Array<out StackTraceElement>): Int {
    var until = trace.lastIndex
    val predicate: StackTraceElement.() -> Boolean = { className.run { startsWith("klite") || contains(".coroutines.") } }
    while (until > 0 && !trace[until].predicate()) until--
    while (until > 0 && trace[until].predicate()) until--
    return if (until < trace.lastIndex) until + 1 else until
  }
}
