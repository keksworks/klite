package klite.slf4j

import klite.Config
import org.slf4j.event.Level
import org.slf4j.event.Level.*
import org.slf4j.helpers.MarkerIgnoringBase
import org.slf4j.helpers.MessageFormatter.arrayFormat
import org.slf4j.helpers.MessageFormatter.format
import java.lang.System.currentTimeMillis
import java.lang.Thread.currentThread

open class KliteLogger(name: String): MarkerIgnoringBase() {
  companion object {
    var out = System.out
    private val defaultLevel = Config.optional("LOGGER", Config.optional("LOGGER_LEVEL", INFO.name))
    private val start = currentTimeMillis()
  }

  init { this.name = name }
  open val shortName = name.substringAfterLast(".")
  open val level = Level.valueOf(Config.inherited("LOGGER.$name", defaultLevel))

  open fun isEnabled(level: Level) = this.level >= level
  override fun isTraceEnabled() = isEnabled(TRACE)
  override fun isDebugEnabled() = isEnabled(DEBUG)
  override fun isInfoEnabled() = isEnabled(INFO)
  override fun isWarnEnabled() = isEnabled(WARN)
  override fun isErrorEnabled() = isEnabled(ERROR)

  override fun trace(msg: String?) = log(TRACE, msg)
  override fun trace(msg: String?, t: Throwable?) = log(TRACE, msg, t)
  override fun trace(format: String, arg: Any?) = log(TRACE, format, arg)
  override fun trace(format: String, arg1: Any?, arg2: Any?) = log(TRACE, format, arg1, arg2)
  override fun trace(format: String, vararg args: Any?) = log(TRACE, format, *args)

  override fun debug(msg: String?) = log(DEBUG, msg)
  override fun debug(msg: String?, t: Throwable?) = log(DEBUG, msg, t)
  override fun debug(format: String, arg: Any?) = log(DEBUG, format, arg)
  override fun debug(format: String, arg1: Any?, arg2: Any?) = log(DEBUG, format, arg1, arg2)
  override fun debug(format: String, vararg args: Any?) = log(DEBUG, format, *args)

  override fun info(msg: String?) = log(INFO, msg)
  override fun info(msg: String?, t: Throwable?) = log(INFO, msg, t)
  override fun info(format: String, arg: Any?) = log(INFO, format, arg)
  override fun info(format: String, arg1: Any?, arg2: Any?) = log(INFO, format, arg1, arg2)
  override fun info(format: String, vararg args: Any?) = log(INFO, format, *args)

  override fun warn(msg: String?) = log(WARN, msg)
  override fun warn(msg: String?, t: Throwable?) = log(WARN, msg, t)
  override fun warn(format: String, arg: Any?) = log(WARN, format, arg)
  override fun warn(format: String, arg1: Any?, arg2: Any?) = log(WARN, format, arg1, arg2)
  override fun warn(format: String, vararg args: Any?) = log(WARN, format, *args)

  override fun error(msg: String?) = log(ERROR, msg)
  override fun error(msg: String?, t: Throwable?) = log(ERROR, msg, t)
  override fun error(format: String, arg: Any?) = log(ERROR, format, arg)
  override fun error(format: String, arg1: Any?, arg2: Any?) = log(ERROR, format, arg1, arg2)
  override fun error(format: String, vararg args: Any?) = log(ERROR, format, *args)

  open fun formatMessage(level: Level, msg: String?) =
    "${currentTimeMillis() - start} [${currentThread().name}] $level $shortName - ${msg ?: ""}"

  protected open fun print(level: Level, msg: String?, t: Throwable?): Unit = synchronized(out) {
    out.println(formatMessage(level, msg))
    t?.printStackTrace(out)
  }

  private fun log(level: Level, format: String, vararg args: Any?) {
    if (!isEnabled(level)) return
    arrayFormat(format, args).apply { log(level, msg = message, throwable) }
  }

  private fun log(level: Level, format: String, arg: Any?) {
    if (!isEnabled(level)) return
    format(format, arg).apply { log(level, msg = message, throwable) }
  }

  private fun log(level: Level, msg: String?, t: Throwable? = null) {
    if (isEnabled(level)) print(level, msg, t)
  }
}
