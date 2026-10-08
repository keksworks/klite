package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.TSID
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf

class JsonTest {
  val tsid = TSID<Any>(11_111_101_234_567_890)
  val value = tsid.value.toString()

  data class Holder(val id: TSID<Any>, val name: String) {
    val computed get() = name.uppercase()
  }

  @Test fun `TSID is rendered as a number`() {
    expect(dbJsonMapper.render(tsid)).toEqual(value)
    expect(dbJsonMapper.render(Holder(tsid, "x"))).toEqual("""{"id":$value,"name":"x"}""")
  }

  @Test fun `computed properties are not stored`() {
    expect(dbJsonMapper.render(Holder(tsid, "x"))).toEqual("""{"id":$value,"name":"x"}""")
  }

  @Test fun `TSID is parsed from a number`() {
    expect(dbJsonMapper.parse<TSID<Any>>(value, typeOf<TSID<Any>>())).toEqual(tsid)
    expect(dbJsonMapper.parse<Holder>("""{"id":$value,"name":"x"}""", typeOf<Holder>())).toEqual(Holder(tsid, "x"))
  }

  @Test fun `TSID is parsed from a decimal string`() {
    expect(dbJsonMapper.parse<TSID<Any>>("\"$value\"", typeOf<TSID<Any>>())).toEqual(tsid)
    expect(dbJsonMapper.parse<Holder>("""{"id":"$value","name":"x"}""", typeOf<Holder>())).toEqual(Holder(tsid, "x"))
  }

  @Test fun `TSID is parsed from its toString form`() {
    expect(dbJsonMapper.parse<TSID<Any>>("\"$tsid\"", typeOf<TSID<Any>>())).toEqual(tsid)
    expect(dbJsonMapper.parse<Holder>("""{"id":"$tsid","name":"x"}""", typeOf<Holder>())).toEqual(Holder(tsid, "x"))
  }

  @Test fun `TSID rendering can be parsed back`() {
    expect(dbJsonMapper.parse<TSID<Any>>(dbJsonMapper.render(tsid), typeOf<TSID<Any>>())).toEqual(tsid)
    expect(dbJsonMapper.parse<Holder>(dbJsonMapper.render(Holder(tsid, "x")), typeOf<Holder>())).toEqual(Holder(tsid, "x"))
  }
}
