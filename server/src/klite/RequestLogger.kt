package klite

import java.lang.System.Logger.Level
import java.lang.System.Logger.Level.*

typealias RequestLogFormatter = HttpExchange.(ms: Long) -> String?
val defaultRequestLogFormatter: RequestLogFormatter = { ms ->
  "$remoteAddress $method $path$query: $statusCode in $ms ms - $browser" +
    (failure?.let { e -> if (e is StatusCodeException) e.message?.let { m -> " - $m" } else " - $e" } ?: "")
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

  open fun logLevel(e: Throwable?): Level = if (e == null) INFO
    else if (e is NotFoundException || e is UnauthorizedException) WARNING
    else if (e !is StatusCodeException || e.statusCode.isError) ERROR
    else INFO
}
