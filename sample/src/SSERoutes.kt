import klite.HttpExchange
import klite.annotations.GET
import klite.annotations.POST
import klite.info
import klite.logger
import klite.sse.Event
import klite.sse.send
import klite.sse.startEventStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.*
import kotlin.time.Duration.Companion.seconds

class SSERoutes {
  private val channel = Channel<Message>(100)
  private val log = logger("sse")

  data class Message(val hello: String)

  // curl --data '{"hello":"World"}' -H 'Content-Type: application/json' http://localhost:8080/api/sse
  @POST suspend fun post(message: Message) = channel.send(message)

  // use EventSource in browser to receive
  @GET suspend fun listen(e: HttpExchange) = try {
    e.startEventStream()
    while (true) e.send(Event(channel.receive()))
  } catch (e: IOException) {
    log.info(e.toString()) // client disconnect
  }

  @GET("/demo") suspend fun demo(e: HttpExchange) {
    e.send(Event(name = "start"))
    try {
      for (i in 1..100) {
        val data = mapOf("message" to "Hello $i")
        e.send(Event(data = data, id = UUID.randomUUID()))
        log.info("Sent $data")
        delay(2.seconds)
      }
      e.send(Event(name = "end"))
    } catch (e: IOException) {
      log.info(e.toString()) // client disconnect
    }
  }
}
