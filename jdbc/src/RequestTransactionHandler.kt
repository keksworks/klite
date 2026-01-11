package klite.jdbc

import klite.*
import kotlinx.coroutines.withContext
import kotlin.reflect.full.hasAnnotation

/**
 * Will start and close DB transaction for each request.
 * Normal finish or StatusCodeException will commit, any other Exception will rollback.
 */
class RequestTransactionHandler(val exclude: Set<RequestMethod> = emptySet()): Extension {
  override fun install(config: RouterConfig) = config.run {
    decorator { exchange, handler -> decorate(exchange, handler) }
  }

  suspend fun decorate(e: HttpExchange, handler: Handler): Any? {
    if (e.method in exclude || e.route.hasAnnotation<NoTransaction>()) return handler(e)

    val tx = Transaction()
    return withContext(TransactionContext(tx)) {
      try {
        handler(e).also {
          tx.close(commit = true)
        }
      } catch (e: Throwable) {
        tx.close(commit = e is StatusCodeException)
        throw e
      }
    }
  }
}
