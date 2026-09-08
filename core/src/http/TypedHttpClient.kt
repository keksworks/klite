package klite.http

import klite.*
import klite.StatusCode.Companion.TooManyRequests
import klite.sse.parseSSE
import java.io.IOException
import java.io.InputStream
import java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE
import java.lang.reflect.Modifier.ABSTRACT
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers.ofString
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configure a default java.net.HttpClient in your registry with proper default timeout
 */
open class TypedHttpClient(
  val baseUrl: String = "",
  val reqModifier: RequestModifier = { this },
  errorHandler: ((HttpResponse<*>, String) -> Nothing)? = null,
  val retryCount: Int = 0,
  val retryAfter: Duration = 1.seconds,
  private val maxLoggedLen: Int = 1000,
  val http: HttpClient,
  val contentType: String,
  loggerName: String = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).walk { stack -> stack
    .filter { !TypedHttpClient::class.java.isAssignableFrom(it.declaringClass) && it.declaringClass.modifiers and ABSTRACT == 0 }
    .findFirst().get().className
  }
) {
  val errorHandler = errorHandler ?: { res, body -> throw HttpException(StatusCode(res.statusCode()), body) }

  protected var trimToLog: String.() -> String = { if (length <= maxLoggedLen) this else substring(0, maxLoggedLen) + "…" }
  var logger = logger(loggerName).apply {
    if (baseUrl.isNotEmpty()) info("Using $baseUrl")
  }

  private fun buildReq(urlSuffix: String) = HttpRequest.newBuilder().uri(URI("$baseUrl$urlSuffix"))
    .contentType("application/json; charset=UTF-8").accept("application/json")
    .timeout(10.seconds).reqModifier()

  private fun <T> requestJson(urlSuffix: String, type: KType, payload: String? = null, builder: RequestModifier): T =
    parse(request(urlSuffix, payload, BodyHandlers.ofString(), builder).trim(), type)

  private fun requestStream(urlSuffix: String, payload: String? = null, builder: RequestModifier) =
    request(urlSuffix, payload, BodyHandlers.ofInputStream(), builder)

  private fun <T> request(urlSuffix: String, payload: String? = null, bodyHandler: HttpResponse.BodyHandler<T>, builder: RequestModifier): T {
    val req = buildReq(urlSuffix).builder().build()
    val start = System.nanoTime()
    val res = http.send(req, bodyHandler)
    val ms = (System.nanoTime() - start) / 1000_000
    val body = res.body()
    if (res.statusCode() < 300) {
      logger.info("${req.method()} $urlSuffix ${payload?.trimToLog() ?: ""} in $ms ms: " + (if (body is InputStream) "streaming" else body.toString().trimToLog()))
      return res.body()
    } else {
      val errorBody = if (body is InputStream) body.readAllBytes().decodeToString() else body.toString()
      logger.error("Failed ${req.method()} $urlSuffix ${payload?.trimToLog() ?: ""} in $ms ms: ${res.statusCode()}: ${errorBody.trimToLog()}")
      errorHandler(res, errorBody)
    }
  }

  fun <T> retryRequest(urlSuffix: String, type: KType, payload: String? = null, builder: RequestModifier): T {
    for (i in 0..retryCount) {
      try {
        return requestJson(urlSuffix, type, payload, builder)
      } catch (e: IOException) {
        if (i < retryCount && (e as? HttpException)?.statusCode != TooManyRequests) {
          logger.error("Failed $urlSuffix, retry ${i + 1} after $retryAfter", e)
          sleep(retryAfter)
        } else {
          logger.error("Failed $urlSuffix: ${payload?.trimToLog()}", e)
          throw e
        }
      }
    }
    error("Unreachable")
  }

  inline fun <reified T> request(urlSuffix: String, payload: String? = null, noinline builder: RequestModifier): T =
    retryRequest(urlSuffix, typeOf<T>(), payload, builder)

  fun <T> get(urlSuffix: String, type: KType, modifier: RequestModifier? = null): T =
    retryRequest(urlSuffix, type) { GET().apply(modifier) }
  inline fun <reified T> get(urlSuffix: String, noinline modifier: RequestModifier? = null): T = get(urlSuffix, typeOf<T>(), modifier)

  fun <T> post(urlSuffix: String, o: Any?, type: KType, modifier: RequestModifier? = null): T = render(o).let {
    retryRequest(urlSuffix, type, it) { POST(ofString(it)).apply(modifier) } }
  inline fun <reified T> post(urlSuffix: String, o: Any?, noinline modifier: RequestModifier? = null): T = post(urlSuffix, o, typeOf<T>(), modifier)

  fun <T> postSSE(urlSuffix: String, o: Any?, type: KType, eventName: String? = null, modifier: RequestModifier? = null): Sequence<T> =
    render(o).let { requestStream(urlSuffix, it) { POST(ofString(it)).accept("text/event-stream").apply(modifier) } }.parseSSE().mapNotNull {
      if ((eventName == null || it.name == eventName) && it.data is String) parse(it.data, type) else null
    }

  inline fun <reified T> postSSE(urlSuffix: String, o: Any?, eventName: String? = null, noinline modifier: RequestModifier? = null): Sequence<T> =
    postSSE(urlSuffix, o, typeOf<T>(), eventName, modifier)

  fun <T> put(urlSuffix: String, o: Any?, type: KType, modifier: RequestModifier? = null): T = render(o).let {
    retryRequest(urlSuffix, type, it) { PUT(ofString(it)).apply(modifier) } }
  inline fun <reified T> put(urlSuffix: String, o: Any?, noinline modifier: RequestModifier? = null): T = put(urlSuffix, o, typeOf<T>(), modifier)

  fun <T> delete(urlSuffix: String, type: KType, modifier: RequestModifier? = null): T =
    retryRequest(urlSuffix, type) { DELETE().apply(modifier) }
  inline fun <reified T> delete(urlSuffix: String, noinline modifier: RequestModifier? = null): T = delete(urlSuffix, typeOf<T>(), modifier)

  fun <T> patch(urlSuffix: String, o: Any?, type: KType, modifier: RequestModifier? = null): T = render(o).let {
    retryRequest(urlSuffix, type, it) { method("PATCH", ofString(it)).apply(modifier) } }
  inline fun <reified T> patch(urlSuffix: String, o: Any?, noinline modifier: RequestModifier? = null): T = patch(urlSuffix, o, typeOf<T>(), modifier)

  private fun HttpRequest.Builder.apply(modifier: RequestModifier?) = modifier?.let { it() } ?: this

  protected open fun render(o: Any?): String = o.toString()

  @Suppress("UNCHECKED_CAST")
  protected open fun <T> parse(body: String, type: KType): T = when (type.classifier) {
    Unit::class -> Unit as T
    else -> body as T
  }
}

class HttpException(val statusCode: StatusCode, val body: String): IOException("Failed with $statusCode: $body")
