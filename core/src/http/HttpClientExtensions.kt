package klite.http

import klite.error
import klite.info
import klite.logger
import kotlinx.coroutines.future.await
import java.lang.System.currentTimeMillis
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers.ofString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

inline fun httpClient(builder: HttpClient.Builder.() -> Unit = {}): HttpClient =
  HttpClient.newBuilder().connectTimeout(5.seconds).apply(builder).build()

fun HttpClient.Builder.connectTimeout(duration: Duration): HttpClient.Builder = connectTimeout(duration.toJavaDuration())

fun HttpRequest.Builder.timeout(duration: Duration): HttpRequest.Builder = timeout(duration.toJavaDuration())
fun HttpRequest.Builder.authBearer(token: String) = setHeader("Authorization", "Bearer $token")
fun HttpRequest.Builder.contentType(mimeType: String) = setHeader("Content-Type", mimeType)
fun HttpRequest.Builder.accept(mimeType: String) = setHeader("Accept", mimeType)

typealias RequestModifier = HttpRequest.Builder.() -> HttpRequest.Builder

private val log = logger<HttpClient>()

suspend fun <R> HttpClient.request(url: URI, bodyHandler: HttpResponse.BodyHandler<R>, modifier: RequestModifier = { this }): HttpResponse<R> {
  val start = currentTimeMillis()
  val req = HttpRequest.newBuilder().uri(url).timeout(10.seconds).modifier().build()
  try {
    val res = sendAsync(req, bodyHandler).await()
    log.info("${req.method()} $url in ${currentTimeMillis() - start}ms - ${res.statusCode()}")
    return res
  } catch (e: Exception) {
    log.error("${req.method()} $url in ${currentTimeMillis() - start}ms - failed: ${e.message}")
    throw e
  }
}

suspend fun HttpClient.get(url: URI, modifier: RequestModifier) = request(url, ofString()) { GET().modifier() }
suspend fun HttpClient.post(url: URI, data: Any?, modifier: RequestModifier) = request(url, ofString()) { POST(toBodyPublisher(data)).modifier() }
suspend fun HttpClient.put(url: URI, data: Any?, modifier: RequestModifier) = request(url, ofString()) { PUT(toBodyPublisher(data)).modifier() }
suspend fun HttpClient.patch(url: URI, data: Any?, modifier: RequestModifier) = request(url, ofString()) { method("PATCH", toBodyPublisher(data)).modifier() }
suspend fun HttpClient.delete(url: URI, modifier: RequestModifier) = request(url, ofString()) { DELETE().modifier() }

fun toBodyPublisher(data: Any?): BodyPublisher = when (data) {
  null, Unit -> BodyPublishers.noBody()
  is BodyPublisher -> data
  is ByteArray -> BodyPublishers.ofByteArray(data)
  else -> BodyPublishers.ofString(data.toString())
}
