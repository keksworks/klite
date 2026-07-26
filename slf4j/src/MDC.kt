package klite.slf4j

import klite.Extension
import klite.Handler
import klite.HttpExchange
import klite.RouterConfig
import org.slf4j.MDC
import org.slf4j.spi.MDCAdapter
import java.util.concurrent.Callable

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

  fun <T> run(callable: Callable<T>): T {
    adapter.init(initial)
    return try {
      callable.call()
    } finally {
      adapter.clear()
    }
  }

  fun decorate(e: HttpExchange, handler: Handler): Any? = run { handler(e) }
}
