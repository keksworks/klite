package klite

import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

class CacheTest {
  val data = LocalDate.now()

  @Test fun `set & get`() { Cache<String, LocalDate>(10.milliseconds).use { cache ->
    expect(cache.isEmpty()).toEqual(true)

    cache["key"] = data
    expect(cache.isEmpty()).toEqual(false)

    expect(cache["key"]).toBeTheInstance(data)
    sleep(12.milliseconds)
    expect(cache["key"]).toEqual(null)
    sleep(9.milliseconds)
    expect(cache.isEmpty()).toEqual(true)

    expect(cache.getOrSet("key") { data }).toBeTheInstance(data)
    expect(cache.getOrSet("key") { sleep(20.milliseconds); data }).toBeTheInstance(data)
    expect(cache["key"]).toBeTheInstance(data)
  }}

  @Test fun `getOrSet recomputes expired`() { Cache<String, LocalDate>(10.milliseconds, autoRemoveExpired = false).use { cache ->
    cache["key"] = data
    expect(cache.getOrSet("key") { LocalDate.MAX }).toBeTheInstance(data)
    sleep(12.milliseconds)
    expect(cache.getOrSet("key") { LocalDate.MAX }).toBeTheInstance(LocalDate.MAX)
  }}

  @Test fun prolongOnAccess() { Cache<String, LocalDate>(10.milliseconds, prolongOnAccess = true, keepAlive = mockk(relaxed = true)).use { cache ->
    cache["key"] = data
    Thread.sleep(7)
    expect(cache["key"]).toBeTheInstance(data)
    Thread.sleep(4)
    expect(cache["key"]).toBeTheInstance(data)
    verify { cache.keepAlive(match { it.key == "key" && it.value.value == data }) }
    Thread.sleep(11)
    expect(cache["key"]).toEqual(null)
  }}
}
