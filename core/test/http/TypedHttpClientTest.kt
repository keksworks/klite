package klite.http

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import com.sun.net.httpserver.HttpServer
import klite.StatusCode
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class TypedHttpClientTest {
  @Test fun `logger name should get container class name`() {
    expect(object: TypedHttpClient(http = HttpClient.newHttpClient(), contentType = "") {}.logger.name)
      .toEqual(javaClass.name)
  }

  @Test fun `request should not be retried on a client error`() {
    RespondingServer(StatusCode.BadRequest).use { server ->
      expect { server.client.get<String>("/") }.toThrow<HttpException>().messageToContain("Failed with 400")
      expect(server.requests.get()).toEqual(1)
    }
  }

  @Test fun `request should be retried on a server error`() {
    RespondingServer(StatusCode.ServiceUnavailable).use { server ->
      expect { server.client.get<String>("/") }.toThrow<HttpException>().messageToContain("Failed with 503")
      expect(server.requests.get()).toEqual(4)
    }
  }

  @Test fun `request body is logged separately from the response when logStart is enabled`() {
    RespondingServer(StatusCode.OK, logStart = true).use { server ->
      val logger = CapturingLogger()
      server.client.logger = logger

      server.client.post<String>("/", "hello")

      expect(logger.messages.size).toEqual(2)
      expect(logger.messages[0]).toContain("POST / hello")
      expect(logger.messages[1]).toContain("POST / in")
      expect(logger.messages[1]).toContain("200")
      expect(logger.messages[1]).notToContain("hello")
    }
  }
}

private class RespondingServer(status: StatusCode, retryCount: Int = 3, logStart: Boolean = false): AutoCloseable {
  private val http = HttpServer.create(InetSocketAddress(0), 0)
  val requests = AtomicInteger()
  val client = object: TypedHttpClient("http://localhost:${http.address.port}", retryCount = retryCount, retryAfter = 0.seconds,
    logStart = logStart, http = HttpClient.newHttpClient(), contentType = "text/plain") {}

  init {
    http.createContext("/") { exchange ->
      requests.incrementAndGet()
      val body = "error".toByteArray()
      exchange.sendResponseHeaders(status.value, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    http.start()
  }

  override fun close() = http.stop(0)
}

private class CapturingLogger: System.Logger {
  val messages = mutableListOf<String>()

  override fun getName() = "test"
  override fun isLoggable(level: System.Logger.Level) = true
  override fun log(level: System.Logger.Level, msg: String) { messages += msg }
  override fun log(level: System.Logger.Level, msg: String, thrown: Throwable?) { messages += msg }

  override fun log(level: System.Logger.Level, bundle: ResourceBundle?, msg: String?, thrown: Throwable?) {
    messages += msg.orEmpty()
  }

  override fun log(level: System.Logger.Level, bundle: ResourceBundle?, format: String?, vararg params: Any?) {
    messages += format?.format(*params).orEmpty()
  }
}
