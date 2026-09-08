package klite.sse

import klite.http.RequestModifier
import klite.http.bodyOrThrow
import klite.http.postStreaming
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient

/** Server-Sent Event */
data class Event(val data: Any? = "", val name: String? = null, val id: Any? = null)

fun HttpClient.postSSE(url: URI, data: Any?, modifier: RequestModifier = { this }): Sequence<Event> =
  postStreaming(url, data) { modifier().header("Accept", "text/event-stream") }.bodyOrThrow().parseSSE()

fun InputStream.parseSSE(): Sequence<Event> = sequence {
  reader().useLines { lines ->
    var id: String? = null
    var name: String? = null
    for (line in lines) {
      val colonPos = line.indexOf(':')
      if (colonPos < 0) continue
      val tag = line.substring(0, colonPos)
      val content = line.substring(colonPos + 1).trim()
      when (tag) {
        "id" -> id = content
        "event" -> name = content
        "data" -> {
          if (content.isNotEmpty()) yield(Event(content, name, id))
          id = null
          name = null
        }
      }
    }
  }
}
