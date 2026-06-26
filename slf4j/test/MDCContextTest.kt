package klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class MDCContextTest {
  @AfterEach fun clearMdc() { MDC.clear() }

  @Test fun `restores previous MDC state`() {
    expect { MDC.put("outer", "value") }.toThrow<IllegalStateException>()
    runBlocking(MDCContext(mapOf("outer" to "yes"))) {
      withContext(MDCContext(mapOf("inner" to "yes"))) {
        expect(MDC.get("outer")).toEqual(null)
        expect(MDC.get("inner")).toEqual("yes")
      }
      expect(MDC.get("outer")).toEqual("yes")
      expect(MDC.get("inner")).toEqual(null)
    }
  }
}
