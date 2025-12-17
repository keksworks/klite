package klite.jdbc

import klite.*
import kotlinx.coroutines.withContext
import javax.sql.DataSource
import kotlin.reflect.full.hasAnnotation

/**
 * Will start and close DB transaction for each request.
 * Normal finish or StatusCodeException will commit, any other Exception will rollback.
 */
class RequestTransactionHandler(val exclude: Set<RequestMethod> = emptySet()): Extension {
  override fun install(config: RouterConfig) = config.run {
    val db = registry.require<DataSource>()
    decorator { exchange, handler -> decorate(db, exchange, handler) }
  }

  suspend fun decorate(db: DataSource, e: HttpExchange, handler: Handler): Any? {
    if (e.method in exclude || e.route.hasAnnotation<NoTransaction>()) return handler(e)

    val tx = Transaction(db)
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
