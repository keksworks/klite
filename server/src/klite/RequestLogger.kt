package klite

import java.lang.System.Logger.Level.ERROR
import java.lang.System.Logger.Level.INFO

typealias RequestLogFormatter = HttpExchange.(ms: Long) -> String?
val defaultRequestLogFormatter: RequestLogFormatter = { ms ->
  "$remoteAddress $method $path$query: $statusCode in $ms ms - $browser" +
    (failure?.let { if (it is StatusCodeException && it.message != null) " - ${it.message}" else "- $it" } ?: "")
}

open class RequestLogger(
  val formatter: RequestLogFormatter = defaultRequestLogFormatter
): Decorator {
  private val log = logger()

  override suspend fun invoke(exchange: HttpExchange, handler: Handler): Any? {
    val start = System.nanoTime()
    exchange.onComplete {
      val ms = (System.nanoTime() - start) / 1000_000
      formatter(exchange, ms)?.let { log.log(logLevel(exchange.failure), it) }
    }
    return handler(exchange)
  }

  open fun logLevel(e: Throwable?) = if (e != null && (e !is StatusCodeException || e.statusCode.isError)) ERROR else INFO
}
