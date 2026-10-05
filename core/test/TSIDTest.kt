package klite

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.lang.System.currentTimeMillis
import java.util.concurrent.atomic.AtomicLong

typealias Id = TSID<Any>

class TSIDTest {
  val maxValue = Id(Long.MAX_VALUE)

  @Test fun tsid() {
    expect(Id(1234L).toString()).toEqual("ya")
    expect(maxValue.toString()).toEqual("1y2p0ij32e8e7")
    expect(Id("1y2p0ij32e8e7")).toEqual(maxValue)
  }

  @Test fun `convert from string`() {
    expect(Converter.from<Id>("ya")).toEqual(Id(1234L))
    expect(Converter.from<Id>("1y2p0ij32e8e7")).toEqual(maxValue)
  }

  @Test fun comparable() {
    // Comparable is required for a TSID to be storable in a Session, which holds Comparables
    expect(Id(1) < Id(2)).toEqual(true)
    expect(Id(2) > Id(1)).toEqual(true)
    expect(Id(2).compareTo(Id(2))).toEqual(0)
  }

  @Test fun converter() {
    expect(Converter.from<Id>(maxValue.toString())).toEqual(maxValue)
  }

  @Test fun createdAt() {
    expect(Id().createdAt.toEpochMilli()).toBeLessThanOrEqualTo(currentTimeMillis())
  }

  @Test fun `no collisions`() {
    val ids = mutableSetOf<Id>()
    for (i in 1..1000000) ids.add(Id())
    expect(ids.size).toEqual(1000000)
  }

  @Test fun unboxInline() {
    expect(Id(123).unboxInline()).toEqual(123L)
  }

  @Test
  fun deterministic() {
    TSID.deterministic = AtomicLong(123123123)
    expect(TSID<Any>().toString()).toEqual("21ayes")
    expect(TSID<Any>().toString()).toEqual("21ayet")
    expect(TSID<Any>().toString()).toEqual("21ayeu")
    TSID.deterministic = null
    expect(TSID<Any>().toString().length).toBeGreaterThan(8)
  }
}
