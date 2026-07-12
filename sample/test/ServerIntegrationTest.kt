package klite.sample

import MyData
import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import klite.json.JsonHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import sampleServer
import java.io.IOException
import java.net.http.HttpRequest.BodyPublishers.noBody

class ServerIntegrationTest {
  @Test fun requests() {
    val server = sampleServer(0).apply { start(gracefulStopDelaySec = -1) }

    val http = JsonHttpClient("http://localhost:${server.address.port}", registry = server.registry)
    runBlocking {
      expect(http.get<String>("/hello")).toEqual("\"Hello World\"")
      expect(http.get<String>("/hello/param/123456?query=123")).toEqual("\"Path: 123456, Query: {query=123}\"")
      expect(http.get<String>("/hello/decorated")).toEqual("\"<!!!>\"")

      expect(http.get<MyData>("/api/hello")).toEqual(MyData("Hello"))
      expect(http.request<Unit>("/api/hello") { method("HEAD", noBody()) }).toEqual(Unit)
      expect(http.get<Unit>("/api/hello/suspend204")).toEqual(Unit)
      expect(http.post<Unit>("/api/hello/201", null)).toEqual(Unit)
      expect(http.get<String>("/api/hello/broken-render")).toEqual("{")
      expect(http.get<String>("/api/hello/null")).toEqual("null")
      expect(http.get<String>("/api/hello/params?required=123")).toEqual("\"false,123,null\"")
      expect(http.get<String>("/api/hello/params?required=xx&nullable=1&optional")).toEqual("\"true,xx,1\"")
      expect(http.post<String>("/api/hello/post", MyData("World"))).toEqual("\"Received MyData(hello=World, world=3.141592653589793) as json, optional = true\"")
      expect(http.patch<String>("/api/hello/patch", MyData("World"))).toEqual("\"Patched MyData(hello=World, world=3.141592653589793)\"")
    }

    expect { runBlocking { http.get<String>("/api/hello/params") } }.toThrow<IOException>()
      .messageToContain("""{"detail":"required is required","message":"required is required","status":400,"title":"Bad Request"}""")

    expect { runBlocking { http.get<String>("/api/notfound") } }.toThrow<IOException>()
      .messageToContain("""{"detail":"/api/notfound","message":"/api/notfound","status":404,"title":"Not Found"}""")

    server.stop()
  }
}
