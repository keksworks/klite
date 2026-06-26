package klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class MDCContextTest {
  @AfterEach fun clearMdc() { MDC.clear() }

  @Test fun `sets MDC context for coroutine`() = runBlocking(MDCContext(mapOf("request.id" to "123", "user" to "admin"))) {
    expect(MDC.get("request.id")).toEqual("123")
    expect(MDC.get("user")).toEqual("admin")
  }

  @Test fun `restores previous MDC state`() {
    MDC.put("outer", "value")
    runBlocking(MDCContext(mapOf("inner" to "yes"))) {
      expect(MDC.get("outer")).toEqual(null)
      expect(MDC.get("inner")).toEqual("yes")
    }
    expect(MDC.get("outer")).toEqual("value")
    expect(MDC.get("inner")).toEqual(null)
  }
}
