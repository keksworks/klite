@file:Suppress("NOTHING_TO_INLINE")
package klite

import java.lang.System.Logger.Level.*
import kotlin.reflect.KClass

fun logger(name: String): System.Logger = System.getLogger(name)
fun logger(cls: KClass<*>): System.Logger = logger(cls.java.nonAnonymousName())
inline fun <reified T> logger(): System.Logger = logger(T::class)
fun Any.logger(): System.Logger = logger(this::class)

internal fun Class<*>.nonAnonymousName(): String {
  var cls = this
  while (cls.isAnonymousClass) cls = cls.superclass
  return cls.name
}

inline fun System.Logger.debug(msg: String) = log(DEBUG, msg)
inline fun System.Logger.info(msg: String) = log(INFO, msg)
inline fun System.Logger.warn(msg: String) = log(WARNING, msg)
inline fun System.Logger.error(msg: String, e: Throwable? = null) = log(ERROR, msg, e)
inline fun System.Logger.error(e: Throwable) = log(ERROR, e.message ?: e.javaClass.name, e)
