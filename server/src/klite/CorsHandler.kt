package klite

import klite.RequestMethod.*
import klite.StatusCode.Companion.OK
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * An easy way to enable CORS (cross-origin requests).
 * Enable with `before(CorsHandler(...))`
 */
open class CorsHandler(
  val maxAge: Duration = 7.days,
  val allowCredentials: Boolean = true,
  val allowedOrigins: Set<String>? = null,
  val allowedMethods: Set<RequestMethod> = setOf(GET, POST, PUT, PATCH, DELETE),
  val allowedHeaders: Set<String>? = null
): Before {
  override fun before(exchange: HttpExchange) {
    val origin = exchange.header("Origin") ?: return
    if (allowedOrigins == null || allowedOrigins.contains(origin)) {
      exchange.header("Access-Control-Allow-Origin", origin)
      exchange.header("Vary", "Origin")
      if (allowCredentials) exchange.header("Access-Control-Allow-Credentials", "true")
    } else throw ForbiddenException()

    exchange.header("Access-Control-Request-Headers")?.let {
      exchange.header("Access-Control-Allow-Headers", allowedHeaders?.joinToString() ?: it)
    }

    if (exchange.method == OPTIONS) {
      exchange.header("Access-Control-Max-Age", maxAge.inWholeSeconds.toString())

      val requestedMethod = RequestMethod.valueOf(exchange.header("Access-Control-Request-Method")!!)
      if (!allowedMethods.contains(requestedMethod)) throw ForbiddenException()
      else exchange.header("Access-Control-Allow-Methods", allowedMethods.joinToString())

      exchange.send(OK)
    }
  }
}
