package klite

import java.lang.System.Logger.Level.ERROR
import java.lang.System.Logger.Level.INFO

typealias RequestLogFormatter = HttpExchange.(ms: Long) -> String?
val defaultRequestLogFormatter: RequestLogFormatter = { ms ->
  "$remoteAddress $method $path$query: $statusCode in $ms ms - $browser" +
    (if (failure.isError) " - $failure" else if (failure is RedirectException) " - ${failure?.message}" else "")
}

private val Throwable?.isError get() =
  this != null && (this !is StatusCodeException || this.statusCode.isError && this !is NotFoundException)

open class RequestLogger(
  val formatter: RequestLogFormatter = defaultRequestLogFormatter
): Decorator {
  private val log = logger()

  override suspend fun invoke(exchange: HttpExchange, handler: Handler): Any? {
    val start = System.nanoTime()
    exchange.onComplete {
      val ms = (System.nanoTime() - start) / 1000_000
      formatter(exchange, ms)?.let { log.log(if (exchange.failure.isError) ERROR else INFO, it) }
    }
    return handler(exchange)
  }
}
