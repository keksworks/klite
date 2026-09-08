package klite.sse

import klite.*
import java.io.OutputStream

/** Use in a GET handler to implement SSE (Server-Sent Events), follow by [send] calls */
fun HttpExchange.startEventStream() = startResponse(StatusCode.OK, null, MimeTypes.eventStream)

fun HttpExchange.send(event: Event, dataRenderer: BodyRenderer? = config.renderers.first()) {
  if (!isResponseStarted) startEventStream()
  event.sendTo(original.responseBody, dataRenderer)
}

internal fun Event.sendTo(out: OutputStream, dataRenderer: BodyRenderer? = null) {
  id?.let { out.write("id: $it\n") }
  name?.let { out.write("event: $it\n") }
  out.write("data: ")
  dataRenderer?.render(out, data) ?: out.write(data.toString().replace("\n", "\ndata: "))
  out.write("\n\n")
  out.flush()
}
