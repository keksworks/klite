package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.TSID
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf

class JsonTest {
  val tsid = TSID<Any>(1_234_567_890L)

  data class Holder(val id: TSID<Any>, val name: String)

  @Test fun `TSID is rendered as a number`() {
    expect(dbJsonMapper.render(tsid)).toEqual("1234567890")
    expect(dbJsonMapper.render(Holder(tsid, "x"))).toEqual("""{"id":1234567890,"name":"x"}""")
  }

  @Test fun `TSID is parsed from a number`() {
    expect(dbJsonMapper.parse<TSID<Any>>("1234567890", typeOf<TSID<Any>>())).toEqual(tsid)
    expect(dbJsonMapper.parse<Holder>("""{"id":1234567890,"name":"x"}""", typeOf<Holder>())).toEqual(Holder(tsid, "x"))
  }

  @Test fun `TSID is parsed from a decimal string`() {
    expect(dbJsonMapper.parse<TSID<Any>>("\"1234567890\"", typeOf<TSID<Any>>())).toEqual(tsid)
    expect(dbJsonMapper.parse<Holder>("""{"id":"1234567890","name":"x"}""", typeOf<Holder>())).toEqual(Holder(tsid, "x"))
  }

  @Test fun `TSID is parsed from its toString form`() {
    expect(tsid.toString()).toEqual("kf12oi")
    expect(dbJsonMapper.parse<TSID<Any>>("\"kf12oi\"", typeOf<TSID<Any>>())).toEqual(tsid)
    expect(dbJsonMapper.parse<Holder>("""{"id":"kf12oi","name":"x"}""", typeOf<Holder>())).toEqual(Holder(tsid, "x"))
  }

  @Test fun `TSID rendering can be parsed back`() {
    expect(dbJsonMapper.parse<TSID<Any>>(dbJsonMapper.render(tsid), typeOf<TSID<Any>>())).toEqual(tsid)
    expect(dbJsonMapper.parse<Holder>(dbJsonMapper.render(Holder(tsid, "x")), typeOf<Holder>())).toEqual(Holder(tsid, "x"))
  }
}
