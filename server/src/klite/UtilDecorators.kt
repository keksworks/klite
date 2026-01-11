package klite

import klite.StatusCode.Companion.TooManyRequests
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

fun RouterConfig.enforceHttps(maxAge: Duration = 365.days) = before { e ->
  if (!e.isSecure) {
    e.header("Strict-Transport-Security", "max-age=${maxAge.inWholeSeconds}")
    e.redirect(e.fullUrl.toString().replaceFirst("http://", "https://"), StatusCode.PermanentRedirect)
  }
}

fun RouterConfig.enforceCanonicalHost(host: String) = before { e ->
  if (e.host != host) e.redirect(e.protocol + "://" + host + e.path + e.query, StatusCode.PermanentRedirect)
}

fun RouterConfig.basicAuth(realm: String = "Auth", userProvider: (name: String, password: Password) -> Any?) = before { e ->
  val auth = e.header("Authorization")
  if (auth?.startsWith("Basic ") == true) {
    val (name, password) = String(auth.substringAfter(" ").base64Decode()).split(':', limit = 2)
    userProvider(name, Password(password))?.let {
      e.attr("user", it)
      e.attrPut(it)
      return@before
    }
  }
  e.header("WWW-Authenticate", "Basic realm=\"$realm\"")
  throw UnauthorizedException()
}

fun RouterConfig.basicAuth(users: Map<String, Password>, realm: String = "Auth") = basicAuth(realm) { name, password ->
  if (users[name] == password) name else null
}

fun RouterConfig.useHashCodeAsETag() = decorator { e, handler ->
  e.handler()?.also { e.checkETagHashCode(it) }
}

fun HttpExchange.checkETagHashCode(o: Any) {
  if (eTagHashCode(o) == header("If-None-Match")) throw NotModifiedException()
}

fun HttpExchange.eTagHashCode(o: Any) = "W/\"${o.hashCode().toUInt().toString(36)}\"".also { header("ETag", it) }

fun HttpExchange.checkLastModified(at: Instant) {
  if (lastModified(at) == header("If-Modified-Since")) throw NotModifiedException()
}

fun HttpExchange.lastModified(at: Instant): String = DateTimeFormatter.RFC_1123_DATE_TIME.format(at.atOffset(ZoneOffset.UTC)).also {
  header("Last-Modified", it)
}

fun RouterConfig.rateLimit(limit: Int, window: Duration) {
  val limits = ConcurrentHashMap<String, RateLimit>()
  val rate = limit.toDouble() / window.inWholeNanoseconds
  val maxTokens = limit.toDouble()

  decorator { e, handler ->
    val limiter = limits.getOrPut(e.remoteAddress) { RateLimit(maxTokens, System.nanoTime()) }
    synchronized(limiter) {
      val now = System.nanoTime()
      val refill = (now - limiter.lastRefill) * rate
      limiter.tokens = (limiter.tokens + refill).coerceAtMost(maxTokens)
      limiter.lastRefill = now
      if (limiter.tokens >= 1) limiter.tokens -= 1
      else {
        val retryAfter = ceil((1 - limiter.tokens) / rate / 1e9).toInt()
        e.header("Retry-After", retryAfter.toString())
        throw StatusCodeException(TooManyRequests)
      }
    }
    handler(e)
  }
}

private data class RateLimit(var tokens: Double, var lastRefill: Long)
