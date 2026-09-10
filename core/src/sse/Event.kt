package klite.sse

import klite.http.*
import klite.max
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import kotlin.time.Duration.Companion.days

/** Server-Sent Event */
data class Event(val data: Any? = "", val name: String? = null, val id: Any? = null)

fun HttpClient.getSSE(url: URI, modifier: RequestModifier = { this }): Sequence<Event> =
  getStreaming(url) { timeout(1.days).modifier().header("Accept", "text/event-stream") }.bodyOrThrow().parseSSE()

fun HttpClient.postSSE(url: URI, data: Any?, modifier: RequestModifier = { this }): Sequence<Event> =
  postStreaming(url, data) { timeout(1.days).modifier().header("Accept", "text/event-stream") }.bodyOrThrow().parseSSE()

fun InputStream.parseSSE(): Sequence<Event> = sequence {
  reader().useLines { lines ->
    var id: String? = null
    var name: String? = null
    var data: String? = null
    for (line in lines) {
      val colonPos = line.indexOf(':').max(0)
      val tag = line.substring(0, colonPos)
      val content = if (colonPos == 0) line else line.substring(colonPos + 1).trim()
      when (tag) {
        "id" -> id = content
        "event" -> name = content
        "data" -> {
          if (data == null) data = content
          else data += "\n$content"
        }
        "" -> {
          if (name != null || data != null) yield(Event(data, name, id))
          id = null
          name = null
          data = null
        }
      }
    }
  }
}
