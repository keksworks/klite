package klite

import klite.RequestMethod.GET
import klite.StatusCode.Companion.OK
import klite.StatusCode.Companion.TooManyRequests
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

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
  e.handler()?.also { if (e.method == GET && e.statusCode == OK && it != Unit) e.checkETagHashCode(it) }
}

fun HttpExchange.checkETagHashCode(o: Any) {
  if (eTagHashCode(o, responseType) == header("If-None-Match")) throw NotModifiedException()
}

fun HttpExchange.eTagHashCode(o: Any, contentType: String? = null) =
  "W/\"${(o.hashCode() xor contentType.hashCode()).toUInt().toString(36)}\"".also { header("ETag", it) }

fun HttpExchange.checkLastModified(at: Instant) {
  if (lastModified(at) == header("If-Modified-Since")) throw NotModifiedException()
}

fun HttpExchange.lastModified(at: Instant): String = DateTimeFormatter.RFC_1123_DATE_TIME.format(at.atOffset(ZoneOffset.UTC)).also {
  header("Last-Modified", it)
}

fun RouterConfig.rateLimit(limit: Int, window: Duration, bannedFor: Duration = window * 3) {
  val limits = Cache<String, RateLimit>(expiration = bannedFor, prolongOnAccess = true)
  val rate = limit.toDouble() / window.inWholeNanoseconds
  val maxTokens = limit.toDouble()
  val requestsLimited = AtomicLong()
  Metrics.register("requestsLimited") { requestsLimited.get() }

  decorator { e, handler ->
    val limiter = limits.getOrSet(e.remoteAddress) { RateLimit(maxTokens, System.nanoTime()) }
    synchronized(limiter) {
      val now = System.nanoTime()
      val refill = (now - limiter.lastRefill) * rate
      limiter.tokens = (limiter.tokens + refill).coerceAtMost(maxTokens)
      limiter.lastRefill = now
      if (limiter.tokens >= 1) limiter.tokens -= 1
      else {
        requestsLimited.incrementAndGet()
        val retryAfter = ceil((1 - limiter.tokens) / rate / 1e9).toInt()
        e.header("Retry-After", retryAfter.toString())
        throw StatusCodeException(TooManyRequests)
      }
    }
    handler(e)
  }
}

fun RouterConfig.securityBan(bannedFor: Duration = 1.hours,
  blacklistedPaths: List<String> = listOf("/..", "/.env", "/.git", "compose.yml", ".php")) {
  val banned = Cache<String, Boolean>(expiration = bannedFor)
  val requestsBanned = AtomicLong()
  val log = logger("securityBan")
  Metrics.register("requestsBanned") { requestsBanned.get() }

  before { e ->
    if (banned[e.remoteAddress] == true) {
      requestsBanned.incrementAndGet()
      throw StatusCodeException(TooManyRequests)
    }
    if (blacklistedPaths.any { e.path.contains(it) }) {
      banned[e.remoteAddress] = true
      requestsBanned.incrementAndGet()
      log.warn("banned ${e.remoteAddress} for $bannedFor because of requesting ${e.path}")
      throw ForbiddenException()
    }
  }
}

private data class RateLimit(var tokens: Double, var lastRefill: Long)
