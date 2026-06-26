package klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class KliteMDCAdapterTest {
  val adapter = KliteMDCAdapter()
  @AfterEach fun cleanup() { adapter.clear() }

  @Test fun `put fails without init`() {
    expect { adapter.put("key", "value") }.toThrow<IllegalStateException>()
  }

  @Test fun `put and get`() {
    adapter.init(mutableMapOf())
    adapter.put("key", "value")
    expect(adapter.get("key")).toEqual("value")
  }

  @Test fun `save and restore`() {
    expect(adapter.getCopyOfContextMap()).toEqual(null)
    adapter.init(mutableMapOf())
    adapter.put("a", "1")
    adapter.put("b", "2")
    val copy = adapter.getCopyOfContextMap()
    expect(copy).toEqual(mapOf("a" to "1", "b" to "2"))
    adapter.clear()
    adapter.setContextMap(copy)
    expect(adapter.get("a")).toEqual("1")
  }
}
