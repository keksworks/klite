package klite

import klite.RequestMethod.GET
import klite.StatusCode.Companion.OK
import klite.StatusCode.Companion.PayloadTooLarge
import klite.StatusCode.Companion.TooManyRequests
import java.io.InputStream
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
  e.basicAuthCredentials()?.let { (name, password) -> userProvider(name, password) }?.let {
    e.attr("user", it)
    e.attrPut(it)
    return@before
  }
  e.header("WWW-Authenticate", "Basic realm=\"${realm.quoted()}\"")
  throw UnauthorizedException()
}

fun RouterConfig.basicAuth(users: Map<String, Password>, realm: String = "Auth") = basicAuth(realm) { name, password ->
  if (users[name] == password) name else null
}

/** Parses `Authorization: Basic` credentials, null for anything malformed, so it can't fail the request */
private fun HttpExchange.basicAuthCredentials(): Pair<String, Password>? {
  val auth = header("Authorization") ?: return null
  if (!auth.startsWith("Basic ")) return null
  val credentials = auth.removePrefix("Basic ").base64DecodeOrNull() ?: return null
  val decoded = String(credentials)
  val separator = decoded.indexOf(':')
  return if (separator < 0) null else decoded.substring(0, separator) to Password(decoded.substring(separator + 1))
}

/** Escapes a quoted-string per RFC 7230, so a realm can't break out of the `WWW-Authenticate` header value */
private fun String.quoted() = replace("\\", "\\\\").replace("\"", "\\\"")

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

fun RouterConfig.rateLimit(limit: Int, window: Duration) {
  val limits = Cache<String, RateLimit>(expiration = window * 3, prolongOnAccess = true)
  val rate = limit.toDouble() / window.inWholeNanoseconds
  val maxTokens = limit.toDouble()
  val log = logger("rateLimit")
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
        log.warn("exceeded for ${e.remoteAddress}, retry after $retryAfter sec")
        throw StatusCodeException(TooManyRequests)
      }
    }
    handler(e)
  }
}

private data class RateLimit(var tokens: Double, var lastRefill: Long)

fun RouterConfig.securityBan(bannedFor: Duration = 1.hours,
  blacklistedPaths: List<String> = listOf("/..", "/.env", "/.git", "compose.yml", ".php")) {
  val banned = Cache<String, Boolean>(expiration = bannedFor)
  val log = logger("securityBan")
  val requestsBanned = AtomicLong()
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

fun RouterConfig.bodySizeLimit(bodyLimit: Long = Config.optional("BODY_LIMIT_MB", "10").toLong() * 1024 * 1024) {
  before { e ->
    if (e.method.hasBody) {
      val cl = e.header("Content-Length")?.toLongOrNull()
      if (cl != null && cl > bodyLimit)
        throw StatusCodeException(PayloadTooLarge, "Maximum request body size is $bodyLimit bytes, received $cl bytes")
      e.requestStream = SizeLimitedInputStream(e.requestStream, bodyLimit)
    }
  }
}

internal class SizeLimitedInputStream(private val src: InputStream, private val limit: Long): InputStream() {
  private var read = 0L

  private fun check(n: Long): Long {
    if (n == -1L) return -1
    read += n
    if (read > limit) throw StatusCodeException(PayloadTooLarge, "Request body exceeds limit of $limit bytes")
    return n
  }

  override fun read() = src.read().also { if (it >= 0) check(1) }
  override fun read(b: ByteArray, off: Int, len: Int) = check(src.read(b, off, len).toLong()).toInt()
  override fun skip(n: Long) = check(src.skip(n))
  override fun available() = (limit - read).coerceIn(0, src.available().toLong()).toInt()
  override fun close() = src.close()
}
