package klite.json

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.Converter
import klite.TSID
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

class JsonRendererTest {
  val mapper = JsonMapper()
  val uuid = UUID.fromString("b8ca58ec-ab15-11ed-93cc-8fdb43988a14")
  val tsId = TSID<Any>("123456789")
  val numCode = NumCode<Any>(123456789)

  @Test fun literals() {
    expect(mapper.render(null)).toEqual("null")
    expect(mapper.render(true)).toEqual("true")
    expect(mapper.render(false)).toEqual("false")
    expect(mapper.render(123.45)).toEqual("123.45")
  }

  @Test fun string() {
    expect(mapper.render("Hello")).toEqual("\"Hello\"")
    expect(mapper.render("Hello\n\t\"World\"\u001F\\")).toEqual("\"Hello\\n\\t\\\"World\\\"\\u001f\\\\\"")
  }

  @Test fun converter() {
    expect(mapper.render(uuid)).toEqual("\"$uuid\"")
    expect(mapper.render(tsId)).toEqual("\"$tsId\"")

    expect(mapper.render(numCode)).toEqual("$numCode")
    expect(Converter.from<NumCode<Any>>("123456789")).toEqual(numCode)

    val date = Converter.from<LocalDate>("2022-10-21")
    expect(mapper.render(date)).toEqual("\"2022-10-21\"")
  }

  @Test fun array() {
    expect(mapper.render(emptyList<Any>())).toEqual("[]")
    expect(mapper.render(listOf("a", 1, 3))).toEqual("[\"a\",1,3]")
    expect(mapper.render(arrayOf(1, null))).toEqual("[1,null]")
  }

  @Test fun objects() {
    expect(mapper.render(emptyMap<Any, Any>())).toEqual("{}")
    expect(mapper.render(mapOf("x" to 123, "y" to "abc"))).toEqual("""{"x":123,"y":"abc"}""")
    expect(mapper.render(mapOf(1 to mapOf(2 to arrayOf(1, 2, 3))))).toEqual("""{"1":{"2":[1,2,3]}}""")
  }

  @Test fun `json in json`() {
    val value = mapper.render(mapOf("a" to "\"foo\""))
    expect(mapper.render(mapOf("x" to value))).toEqual("""{"x":"{\"a\":\"\\\"foo\\\"\"}"}""")
  }

  @Test fun renderNulls() {
    expect(mapper.render(mapOf("x" to null))).toEqual("{}")
    expect(mapper.render(mapOf("x" to null, "y" to null, 1 to 2))).toEqual("""{"1":2}""")
    expect(mapper.copy(renderNulls = true).render(mapOf("x" to null))).toEqual("""{"x":null}""")
  }

  @Test fun inline() {
    expect(mapper.render(Inline(123))).toEqual("123")
  }

  @JvmInline value class Inline(val value: Int)

  @Test fun classes() {
    val o = Hello("", uuid, LocalDate.parse("2022-10-21"), Instant.parse("2022-10-21T10:55:00Z"), Nested(567.toBigDecimal()), listOf(Nested(), Nested()))
    expect(mapper.render(o)).toEqual(/*language=JSON*/ """{"array":[{"x":0,"y":123},{"x":0,"y":123}],"computed":1,"date":"2022-10-21","hellou":"","id":"b8ca58ec-ab15-11ed-93cc-8fdb43988a14","instant":"2022-10-21T10:55:00Z","isBoolean":true,"map":{},"nested":{"x":567,"y":123},"readOnly":true}""")
    expect(mapper.render(o.toJsonValues())).toEqual(mapper.render(o))
  }

  @Test fun `snake case`() {
    val mapper = JsonMapper(keys = SnakeCase)
    expect(mapper.render(mapOf("snakeCase" to 123))).toEqual("""{"snake_case":123}""")
  }

  @Test fun `upper camel case`() {
    val mapper = JsonMapper(keys = Capitalize)
    expect(mapper.render(mapOf("camelCase" to true))).toEqual("""{"CamelCase":true}""")
  }

  @Test fun valueConverter() {
    val mapper = JsonMapper(values = object: ValueConverter<Any?>() {
      override fun to(o: Any?) = when (o) {
        is LocalDate -> o.year
        is Nested -> o.x + o.y.toBigDecimal()
        else -> o
      }
    })
    expect(mapper.render(mapOf("date" to LocalDate.of(2022, 10, 21), "custom" to Nested()))).toEqual("""{"date":2022,"custom":123}""")
  }

  @JvmInline value class NumCode<T: Any>(val value: Long) {
    companion object {
      init { Converter.use { NumCode<Any>(it.toLong()) } }
    }
    override fun toString() = value.toString()
  }
}
