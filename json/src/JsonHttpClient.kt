package klite.json

import klite.MimeTypes
import klite.Registry
import klite.http.RequestModifier
import klite.http.TypedHttpClient
import klite.require
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpResponse
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configure a default java.net.HttpClient in your registry, e.g.
 * registry.register(httpClient { connectTimeout(5.seconds) })
 * or provide both http and json parameters to the constructor instead
 */
open class JsonHttpClient(
  urlPrefix: String = "",
  reqModifier: RequestModifier = { this },
  errorHandler: (HttpResponse<*>, String) -> Nothing = { res, body -> throw IOException("Failed with ${res.statusCode()}: $body") },
  retryCount: Int = 0,
  retryAfter: Duration = 1.seconds,
  maxLoggedLen: Int = 1000,
  registry: Registry? = null,
  http: HttpClient = registry!!.require(),
  val json: JsonMapper = registry!!.require()
): TypedHttpClient(urlPrefix, reqModifier, errorHandler, retryCount, retryAfter, maxLoggedLen, http, MimeTypes.json) {
  override fun render(o: Any?) = o as? String ?: json.render(o)

  @Suppress("UNCHECKED_CAST")
  override fun <T> parse(body: String, type: KType): T = when (type.classifier) {
    Unit::class -> Unit as T
    String::class -> body as T
    else -> json.parse(body, type)
  }
}
