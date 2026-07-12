package klite.slf4j

import klite.Extension
import klite.Handler
import klite.HttpExchange
import klite.RouterConfig
import kotlinx.coroutines.ThreadContextElement
import org.slf4j.MDC
import org.slf4j.spi.MDCAdapter
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class KliteMDCAdapter: MDCAdapter {
  private val threadLocal = ThreadLocal<MutableMap<String, String?>>()

  fun init(map: MutableMap<String, String?>) = threadLocal.set(map)

  override fun clear() = threadLocal.remove()

  private fun requireMap(): MutableMap<String, String?> =
    threadLocal.get() ?: throw IllegalStateException("MDC not initialized. Use server.use(RequestMDCContext()) to enable MDC in request handlers.")

  override fun put(key: String, value: String?) {
    requireMap()[key] = value
  }

  override fun get(key: String): String? = threadLocal.get()?.get(key)
  override fun remove(key: String) { threadLocal.get()?.remove(key) }

  override fun getCopyOfContextMap(): Map<String, String?>? = threadLocal.get()

  override fun setContextMap(map: Map<String, String?>?) {
    if (map == null) clear() else threadLocal.set(map as MutableMap<String, String?>)
  }

  override fun pushByKey(key: String, value: String?) = throw UnsupportedOperationException()
  override fun popByKey(key: String) = throw UnsupportedOperationException()
  override fun getCopyOfDequeByKey(key: String) = throw UnsupportedOperationException()
  override fun clearDequeByKey(key: String?) = throw UnsupportedOperationException()
}

class RequestMDCContext(val initial: MutableMap<String, String?> = HashMap()): Extension {
  private val adapter = MDC.getMDCAdapter() as KliteMDCAdapter

  override fun install(config: RouterConfig) = config.run {
    decorator { exchange, handler -> decorate(exchange, handler) }
  }

  fun decorate(e: HttpExchange, handler: Handler): Any? {
    adapter.init(initial)
    return try {
      handler(e)
    } finally {
      adapter.clear()
    }
  }
}

class MDCContext(private val map: Map<String, String?>): ThreadContextElement<Map<String, String?>?>, AbstractCoroutineContextElement(Key) {
  constructor(vararg pairs: Pair<String, String?>) : this(mutableMapOf(*pairs))
  companion object Key: CoroutineContext.Key<MDCContext>

  override fun updateThreadContext(context: CoroutineContext): Map<String, String?>? {
    val oldState = MDC.getCopyOfContextMap()
    MDC.setContextMap(map)
    return oldState
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String?>?) {
    if (oldState == null) MDC.clear() else MDC.setContextMap(oldState)
  }
}
