package klite.jackson

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.ZERO

class JsonHttpClientTest {
  val httpClient = mockk<HttpClient>()
  val http = JsonHttpClient("http://some.host/v1", reqModifier = { setHeader("X-Custom-API", "123") },
    retryCount = 2, retryAfter = ZERO, http = httpClient, json = kliteJsonMapper())

  @Test fun `logger name from stack trace`() {
    expect(http.logger.name).toEqual(javaClass.name)
  }

  @Test fun get() {
    val response = mockResponse(200, """{"hello": "World"}""")
    every { httpClient.send<String>(any(), any()) } returns response

    runBlocking {
      val data = http.get<SomeData>("/some/data")
      expect(data).toEqual(SomeData("World"))
    }

    coVerify { httpClient.send<String>(match { it.uri().toString() == "http://some.host/v1/some/data" }, any()) }
  }

  @Test fun `http error`() {
    val response = mockResponse(500, """{"error": "Error"}""")
    every { httpClient.send<String>(any(), any()) } returns response

    assertThrows<IOException> { runBlocking { http.get<SomeData>("/error") } }
  }

  @Test fun exception() {
    val exception = IOException()
    every { httpClient.send<String>(any(), any()) }.throws(exception)
    assertThrows<IOException> { runBlocking { http.post<String>("/some/data", "Hello") } }
    coVerify(exactly = 3) { httpClient.send<String>(any(), any()) }
  }

  @Test fun retry() {
    val response = mockResponse(200, """{"hello": "World"}""")
    every { httpClient.send<String>(any(), any()) } throws IOException() andThen response
    runBlocking {
      val body = http.post<String>("/some/data", "Hello")
      expect(body).toEqual(response.body())
    }
    coVerify(exactly = 2) { httpClient.send<String>(any(), any()) }
  }

  private fun mockResponse(status: Int, body: String) = mockk<HttpResponse<String>> {
    every { statusCode() } returns status
    every { body() } returns body
  }
}
