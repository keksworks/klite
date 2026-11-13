package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class TimerTest {
  @Test fun `max is reset after snapshot`() {
    val timer = Timer()
    listOf(500_000_000L, 2_000_000_000L).forEach(timer::record)
    expect(timer.snapshot()).toEqual(mapOf("count" to 2L, "sum" to 2.5, "max" to 2.0))
    expect(timer.snapshot()["max"]).toEqual(0.0)
  }
}
