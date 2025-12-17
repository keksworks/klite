package klite.http

import klite.error
import klite.info
import klite.logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import java.io.IOException
import java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE
import java.lang.reflect.Modifier.ABSTRACT
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configure a default java.net.HttpClient in your registry with proper default timeout
 */
open class TypedHttpClient(
  protected val urlPrefix: String = "",
  val reqModifier: RequestModifier = { this },
  val errorHandler: (HttpResponse<*>, String) -> Nothing = { res, body -> throw IOException("Failed with ${res.statusCode()}: $body") },
  val retryCount: Int = 0,
  val retryAfter: Duration = 1.seconds,
  private val maxLoggedLen: Int = 1000,
  val http: HttpClient,
  val contentType: String,
  loggerName: String = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).walk { stack -> stack.skip(2).filter {
    it.className != TypedHttpClient::class.java.name && it.declaringClass.modifiers and ABSTRACT == 0
  }.findFirst().get().className }
) {
  protected var trimToLog: String.() -> String = { if (length <= maxLoggedLen) this else substring(0, maxLoggedLen) + "…" }
  var logger = logger(loggerName).apply {
    if (urlPrefix.isNotEmpty()) info("Using $urlPrefix")
  }

  private fun buildReq(urlSuffix: String) = HttpRequest.newBuilder().uri(URI("$urlPrefix$urlSuffix"))
    .contentType("application/json; charset=UTF-8").accept("application/json")
    .timeout(10.seconds).reqModifier()

  private suspend fun <T> request(urlSuffix: String, type: KType, payload: String? = null, builder: RequestModifier): T {
    val req = buildReq(urlSuffix).builder().build()
    val start = System.nanoTime()
    val res = http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).await()
    val ms = (System.nanoTime() - start) / 1000_000
    val body = res.body().trim() // TODO: NPE -> return nullable type
    if (res.statusCode() < 300) {
      logger.info("${req.method()} $urlSuffix ${payload?.trimToLog() ?: ""} in $ms ms: ${body.trimToLog()}")
      return parse(body, type)
    } else {
      logger.error("Failed ${req.method()} $urlSuffix ${payload?.trimToLog() ?: ""} in $ms ms: ${res.statusCode()}: $body")
      errorHandler(res, body)
    }
  }

  suspend fun <T> retryRequest(urlSuffix: String, type: KType, payload: String? = null, builder: RequestModifier): T {
    for (i in 0..retryCount) {
      try {
        return request(urlSuffix, type, payload, builder)
      } catch (e: IOException) {
        if (i < retryCount) {
          logger.error("Failed $urlSuffix, retry ${i + 1} after $retryAfter", e)
          delay(retryAfter.inWholeMilliseconds)
        } else {
          logger.error("Failed $urlSuffix: ${payload?.trimToLog()}", e)
          throw e
        }
      }
    }
    error("Unreachable")
  }

  suspend inline fun <reified T> request(urlSuffix: String, payload: String? = null, noinline builder: RequestModifier): T =
    retryRequest(urlSuffix, typeOf<T>(), payload, builder)

  suspend fun <T> get(urlSuffix: String, type: KType, modifier: RequestModifier? = null): T =
    retryRequest(urlSuffix, type) { GET().apply(modifier) }
  suspend inline fun <reified T> get(urlSuffix: String, noinline modifier: RequestModifier? = null): T = get(urlSuffix, typeOf<T>(), modifier)

  suspend fun <T> post(urlSuffix: String, o: Any?, type: KType, modifier: RequestModifier? = null): T = render(o).let {
    retryRequest(urlSuffix, type, it) { POST(BodyPublishers.ofString(it)).apply(modifier) } }
  suspend inline fun <reified T> post(urlSuffix: String, o: Any?, noinline modifier: RequestModifier? = null): T = post(urlSuffix, o, typeOf<T>(), modifier)

  suspend fun <T> put(urlSuffix: String, o: Any?, type: KType, modifier: RequestModifier? = null): T = render(o).let {
    retryRequest(urlSuffix, type, it) { PUT(BodyPublishers.ofString(it)).apply(modifier) } }
  suspend inline fun <reified T> put(urlSuffix: String, o: Any?, noinline modifier: RequestModifier? = null): T = put(urlSuffix, o, typeOf<T>(), modifier)

  suspend fun <T> delete(urlSuffix: String, type: KType, modifier: RequestModifier? = null): T =
    retryRequest(urlSuffix, type) { DELETE().apply(modifier) }
  suspend inline fun <reified T> delete(urlSuffix: String, noinline modifier: RequestModifier? = null): T = delete(urlSuffix, typeOf<T>(), modifier)

  suspend fun <T> patch(urlSuffix: String, o: Any?, type: KType, modifier: RequestModifier? = null): T = render(o).let {
    retryRequest(urlSuffix, type, it) { method("PATCH", BodyPublishers.ofString(it)).apply(modifier) } }
  suspend inline fun <reified T> patch(urlSuffix: String, o: Any?, noinline modifier: RequestModifier? = null): T = patch(urlSuffix, o, typeOf<T>(), modifier)

  private fun HttpRequest.Builder.apply(modifier: RequestModifier?) = modifier?.let { it() } ?: this

  protected open fun render(o: Any?): String = o.toString()

  @Suppress("UNCHECKED_CAST")
  protected open fun <T> parse(body: String, type: KType): T = when (type.classifier) {
    Unit::class -> Unit as T
    else -> body as T
  }
}
