package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import klite.Cookie.SameSite.Strict
import klite.RequestMethod.POST
import klite.StatusCode.Companion.Created
import klite.StatusCode.Companion.OK
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.time.LocalDate
import kotlin.reflect.typeOf

class HttpExchangeTest {
  abstract class MockableHttpExchange: OriginalHttpExchange()
  val requestStream = "Input".byteInputStream()
  val original = mockk<MockableHttpExchange>(relaxed = true) {
    every { requestMethod } returns "POST"
    every { requestURI } returns URI("/hello?hello=world")
    every { responseCode } returns -1
    every { requestBody } returns requestStream
  }
  val bodyRenderer = mockk<BodyRenderer>()
  val customParser = mockk<BodyParser> {
    every { contentType } returns "application/specific"
  }
  val routerConfig = mockk<RouterConfig> {
    every { parsers } returns mutableListOf(TextBodyParser(), customParser)
    every { renderers } returns mutableListOf(bodyRenderer)
  }
  val exchange = HttpExchange(original, routerConfig, null, "req-id")

  @Test fun path() {
    expect(exchange.method).toEqual(POST)
    expect(exchange.path).toEqual("/hello")
  }

  @Test fun queryParams() {
    expect(exchange.query).toEqual("?hello=world")
    expect(exchange.queryParams).toEqual(mapOf("hello" to "world"))
  }

  @Test fun rawBody() {
    expect(exchange.rawBody).toEqual("Input")
    expect(exchange.rawBody).toEqual(exchange.rawBody)
  }

  @Test fun fullUrl() {
    every { exchange.host } returns "localhost:8080"
    expect(exchange.fullUrl).toEqual(URI("http://localhost:8080/hello?hello=world"))

    every { exchange.host } returns "host.domain"
    expect(exchange.fullUrl("/some/page")).toEqual(URI("http://host.domain/some/page"))
  }

  @Test fun `request cookies`() {
    every { exchange.header("Cookie") } returns "Hello=World; Second=123%20456"
    expect(exchange.cookies).toEqual(mapOf("Hello" to "World", "Second" to "123 456"))
    expect(exchange.cookie("Hello")).toEqual("World")
  }

  @Test fun `response cookies`() {
    exchange.cookie("Hello", "World")
    verify { original.responseHeaders.add("Set-Cookie", "Hello=World; Path=/") }

    exchange += Cookie("LANG", "et", sameSite = Strict, domain = "angryip.org", path = null)
    verify { original.responseHeaders.add("Set-Cookie", "LANG=et; Domain=angryip.org; SameSite=Strict") }
  }

  @Test fun `response content-type`() {
    exchange.responseType = MimeTypes.csv
    verify { original.responseHeaders.set("Content-type", "application/csv; charset=UTF-8") }

    exchange.responseType = "text/hello; charset=ISO-8859-1"
    verify { original.responseHeaders.set("Content-type", "text/hello; charset=ISO-8859-1") }

    exchange.responseType = MimeTypes.pdf
    verify { original.responseHeaders.set("Content-type", "application/pdf") }
  }

  @Test fun `response content-disposition`() {
    exchange.fileName("hellö.xml")
    verify {
      original.responseHeaders.set("Content-disposition", "attachment; filename*=UTF-8''hell%C3%B6.xml")
      original.responseHeaders.set("Content-type", "text/xml; charset=UTF-8")
    }

    exchange.fileName("mega.pdf", attachment = false)
    verify {
      original.responseHeaders.set("Content-disposition", "filename*=UTF-8''mega.pdf")
      original.responseHeaders.set("Content-type", "application/pdf")
    }
  }

  @Test fun `body as text`() {
    every { exchange.requestType } returns null
    every { original.requestBody } answers { "123".byteInputStream() }
    expect(exchange.body<String>()).toEqual("123")
    expect(exchange.body<Int>()).toEqual(123)
  }

  @Test fun `body with specific content-type`() {
    every { exchange.requestType } returns customParser.contentType
    every { customParser.parse<Double>(exchange, typeOf<Double>()) } returns Math.PI
    expect(exchange.body<Double>()).toEqual(Math.PI)
  }

  @Test fun `body as String`() {
    every { original.requestBody } returns "Raw".byteInputStream()
    expect(exchange.body<String>()).toEqual("Raw")
  }

  @Test fun `body as bytes`() {
    every { original.requestBody } returns "Raw".byteInputStream()
    expect(exchange.body<ByteArray>().decodeToString()).toEqual("Raw")
  }

  @Test fun `body with unsupported content-type`() {
    every { exchange.requestType } returns "unsupported"
    assertThrows<UnsupportedMediaTypeException> { exchange.body<LocalDate>() }
  }

  @Test fun `send text`() {
    exchange.send(OK, "Hello", "text/custom")
    verify {
      original.responseHeaders["Content-type"] = "text/custom; charset=UTF-8"
      original.sendResponseHeaders(200, 5)
      original.responseBody.write("Hello")
    }
  }

  @Test fun `send binary`() {
    exchange.send(Created, "XXX".toByteArray(), "image/custom")
    verify {
      original.responseHeaders["Content-type"] = "image/custom"
      original.sendResponseHeaders(201, 3)
      original.responseBody.write("XXX")
    }
  }

  @Test fun onComplete() {
    val handler1 = mockk<Runnable>(relaxed = true)
    val handler2 = mockk<Runnable>(relaxed = true)
    exchange.onComplete(handler1)
    exchange.onComplete(handler2)

    exchange.close()

    verify {
      original.close()
      handler1.run()
      handler2.run()
    }
  }
}
