package klite

import com.sun.net.httpserver.HttpsExchange
import klite.RequestMethod.HEAD
import klite.RequestMethod.OPTIONS
import klite.StatusCode.Companion.Found
import klite.StatusCode.Companion.OK
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.time.Instant
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.reflect.KType
import kotlin.reflect.typeOf

typealias OriginalHttpExchange = com.sun.net.httpserver.HttpExchange

typealias Headers = com.sun.net.httpserver.Headers
operator fun Headers.plusAssign(headers: Map<String, String>) = headers.forEach { (k, v) -> set(k, v) }

@Suppress("UNCHECKED_CAST")
open class HttpExchange(
  internal val original: OriginalHttpExchange,
  internal val config: RouterConfig,
  private val sessionStore: SessionStore?,
  val requestId: String
): AutoCloseable {
  lateinit var route: Route
  val method = RequestMethod.valueOf(original.requestMethod)
  open val remoteAddress: String get() = original.remoteAddress.address.hostAddress
  open val host: String get() = header("Host")!!
  open val isSecure: Boolean get() = original is HttpsExchange
  open val protocol: String get() = if (isSecure) "https" else "http"

  val contextPath: String get() = original.httpContext.path
  val path: String get() = original.requestURI.path
  lateinit var pathParams: Params internal set
  fun path(param: String): String? = pathParams[param]
  inline fun <reified T: Any> path(param: String): T? = path(param)?.let { Converter.from<T>(it) }

  val query: String get() = original.requestURI.query?.let { "?$it" } ?: ""
  val queryParams: Params by lazy { original.requestURI.queryParams }
  fun query(param: String): String? = queryParams[param]
  fun queryList(param: String) = (queryParams[param] as Any?).asList<String>()
  inline fun <reified T: Any> query(param: String): T? = query(param)?.let { Converter.from<T>(it) }

  val fullUrl get() = fullUrl(original.requestURI.toString())
  fun fullUrl(suffix: String): URI = URI("$protocol://$host$suffix")

  inline fun <reified T: Any> body(): T = body(typeOf<T>())

  fun <T: Any> body(type: KType): T {
    if (type.classifier == String::class) return requestStream.reader().use { it.readText() } as T
    if (type.classifier == ByteArray::class) return requestStream.readBytes() as T
    val contentType = requestType ?: MimeTypes.text
    val bodyParser = config.parsers.find { contentType.startsWith(it.contentType) } ?: throw UnsupportedMediaTypeException(requestType)
    return bodyParser.parse(this, type)
  }

  val rawBody by lazy { body<String>() }

  val bodyParams: Map<String, Any?> by lazy { body() }
  /** e.g. form param, passed in body */
  fun <T> body(param: String): T = bodyParams[param] as T

  val attrs: MutableMap<Any, Any?> = mutableMapOf()
  fun <T> attr(key: Any): T = attrs[key] as T
  fun attr(key: Any, value: Any?) = attrs.put(key, value)
  inline fun <reified T: Any> attr(): T = attr(T::class)
  inline fun <reified T: Any> attrPut(value: T) = attr(T::class, value)

  val headers: Headers get() = original.requestHeaders
  fun header(key: String): String? = headers.getFirst(key)

  val responseHeaders: Headers get() = original.responseHeaders
  fun header(key: String, value: String) { responseHeaders[key] = value }

  val cookies: Params by lazy(NONE) { decodeCookies(header("Cookie")) }
  fun cookie(key: String) = cookies[key]

  fun cookie(key: String, value: String, expires: Instant? = null) = cookie(Cookie(key, value, expires, secure = isSecure))
  fun cookie(cookie: Cookie) = responseHeaders.add("Set-Cookie", cookie.toString())
  operator fun plusAssign(cookie: Cookie) = cookie(cookie)

  val session: Session by lazy(NONE) { sessionStore?.load(this) ?: error("No sessionStore defined") }
  inline fun <reified T: Any> session(key: String): T? = session[key]?.let { Converter.from<T>(it) }

  val statusCode: StatusCode? get() = StatusCode(original.responseCode).takeIf { isResponseStarted }
  val isResponseStarted get() = original.responseCode >= 0
  var failure: Throwable? = null

  val requestType: String? get() = header("Content-Type")
  val requestStream: InputStream get() = if (method.hasBody) original.requestBody else error("$method should not have body")

  var responseType: String?
    get() = responseHeaders["Content-type"]?.firstOrNull()
    internal set(value) { value?.let { header("Content-type", MimeTypes.withCharset(it)) } }

  fun fileName(fileName: String, attachment: Boolean = true) {
    header("Content-disposition", (if (attachment) "attachment; " else "") + "filename=\"$fileName\"")
    responseType = MimeTypes.typeFor(fileName)
  }

  val accept get() = Accept(header("Accept"))

  fun render(code: StatusCode, body: Any?) = findRenderer(takeFirst = code != OK).render(this, code, body)

  private fun findRenderer(takeFirst: Boolean = false): BodyRenderer {
    val accept = accept
    return config.renderers.find { accept(it) }
      ?: if (accept.isRelaxed || takeFirst) config.renderers.first() else throw NotAcceptableException(accept.contentTypes)
  }

  /**
   * Sends response headers and provides the stream
   * @param length 0 -> no response, null -> use chunked encoding
   */
  fun startResponse(code: StatusCode, length: Long? = null, contentType: String? = null, fileName: String? = null): OutputStream {
    sessionStore?.save(this, session)
    fileName?.let { fileName(it) }
    contentType?.let { responseType = it }
    val bodyNotAllowed = method == HEAD || method == OPTIONS
    original.sendResponseHeaders(code.value, if (length == 0L || bodyNotAllowed) -1 else length ?: 0)
    if (bodyNotAllowed) throw BodyNotAllowedException()
    return original.responseBody
  }

  fun send(code: StatusCode, body: ByteArray? = null, contentType: String? = null) {
    val out = startResponse(code, body?.size?.toLong() ?: 0, contentType)
    body?.let { out.write(it) }
  }

  fun send(code: StatusCode, body: String?, contentType: String? = null) =
    send(code, body?.toByteArray(), contentType)

  fun redirect(location: String, statusCode: StatusCode = Found): Nothing = throw RedirectException(location, statusCode)
  fun redirect(url: URI, statusCode: StatusCode = Found): Nothing = redirect(url.toString(), statusCode)

  private val onCompleteHandlers = mutableListOf<Runnable>()
  fun onComplete(handler: Runnable) { onCompleteHandlers += handler }

  override fun close() {
    original.close()
    onCompleteHandlers.forEach { it.run() }
  }

  override fun toString() = "$method ${original.requestURI}"
}
