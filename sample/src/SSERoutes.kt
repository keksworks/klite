import klite.HttpExchange
import klite.annotations.GET
import klite.annotations.POST
import klite.info
import klite.logger
import klite.sse.Event
import klite.sse.send
import klite.sse.startEventStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

class SSERoutes {
  private val queue = ArrayBlockingQueue<Message>(100)
  private val log = logger("sse")

  data class Message(val hello: String)

  // curl --data '{"hello":"World"}' -H 'Content-Type: application/json' http://localhost:8080/api/sse
  @POST fun post(message: Message) = queue.put(message)

  // use EventSource in browser to receive
  @GET fun listen(e: HttpExchange) = try {
    e.startEventStream()
    while (true) e.send(Event(queue.poll()))
  } catch (e: IOException) {
    log.info(e.toString()) // client disconnect
  }

  @GET("/demo") fun demo(e: HttpExchange) {
    e.send(Event(name = "start"))
    try {
      for (i in 1..100) {
        val data = mapOf("message" to "Hello $i")
        e.send(Event(data = data, id = UUID.randomUUID()))
        log.info("Sent $data")
        Thread.sleep(2000)
      }
      e.send(Event(name = "end"))
    } catch (e: IOException) {
      log.info(e.toString()) // client disconnect
    }
  }
}
