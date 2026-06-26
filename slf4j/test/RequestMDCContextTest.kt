package klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import klite.HttpExchange
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class RequestMDCContextTest {
  @Test fun `decorator initializes MDC`() {
    val decorator = RequestMDCContext()
    val handler = { e: HttpExchange ->
      MDC.put("key", "value")
      expect(MDC.get("key")).toEqual("value")
      "ok"
    }
    runBlocking { decorator.decorate(mockk(), handler) }
  }

  @Test fun `decorator cleans up MDC after handler`() {
    val decorator = RequestMDCContext()
    val handler = { _: HttpExchange -> "ok" }
    runBlocking { decorator.decorate(mockk(), handler) }
    expect(MDC.get("key")).toEqual(null)
  }

  @Test fun `decorator with initial values`() {
    val decorator = RequestMDCContext(initial = mutableMapOf("app" to "test"))
    val handler = { _: HttpExchange ->
      expect(MDC.get("app")).toEqual("test")
      MDC.put("extra", "data")
      expect(MDC.get("extra")).toEqual("data")
      "ok"
    }
    runBlocking { decorator.decorate(mockk(), handler) }
  }

  @Test fun `decorator cleans up on exception`() {
    val decorator = RequestMDCContext()
    val handler = { _: HttpExchange ->
      MDC.put("key", "value")
      throw RuntimeException("fail")
    }
    try {
      runBlocking { decorator.decorate(mockk(), handler) }
    } catch (e: RuntimeException) {
      expect(e.message).toEqual("fail")
    }
    expect(MDC.get("key")).toEqual(null)
  }
}
