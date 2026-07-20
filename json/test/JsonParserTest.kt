package klite.json

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import klite.*
import klite.json.JsonSubTypes.Type
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KProperty1
import kotlin.reflect.KType

class JsonParserTest {
  val mapper = JsonMapper()

  @Test fun parse() {
    expect(mapper.parse<JsonNode>("""  {  "hello" : "world","blah": 123, "xxx": true, "zzz" : null, "nested":{"a":[],"c":{}}, "array": [1,-2,3.14, 1e20, 1e-10]}""")).toEqual(
      mapOf("hello" to "world", "blah" to 123, "xxx" to true, "zzz" to null, "nested" to mapOf("a" to emptyList<Any>(), "c" to emptyMap<String, Any>()), "array" to listOf(1, -2, 3.14, 1e20, 1e-10)))
  }

  @Test fun array() {
    expect(mapper.parse<List<Int>>(" [1,\n2,3]\n")).toEqual(listOf(1, 2, 3))
    expect(mapper.parse<Set<Int>>(" [1,\n2,3]\n")).toEqual(setOf(1, 2, 3))
  }

  @Test fun `consume array`() {
    val list = mutableListOf<JsonNode>()
    JsonParser("""[{"a":1},{"b":2}]""".reader(), mapper).readArray<JsonNode>(list::add)
    expect(list).toContainExactly(mapOf("a" to 1), mapOf("b" to 2))
  }

  @Test fun escaping() {
    expect(mapper.copy(trimToNull = false).parse<Any>("""{"x\\y": "\"\n\r\u00A0"}""")).toEqual(mapOf("x\\y" to "\"\n\r\u00A0"))
  }

  @Test fun `parse invalid`() {
    expect { mapper.parse<Any>("""z""") }.toThrow<JsonParseException>().messageToContain("Unexpected 'z' at 1:1")
    expect { mapper.parse<Any>("""{"hello": x""") }.toThrow<JsonParseException>().messageToContain("Unexpected 'x' at 1:11")
    expect { mapper.parse<Any>("""{"hello": """") }.toThrow<JsonParseException>().messageToContain("Unterminated string at 1:12")
    expect { mapper.parse<Any>("""{"hello": 123""") }.toThrow<JsonParseException>().messageToContain("Expecting ',' but got EOF at 1:14")
    expect { mapper.parse<Any>("""nulls""") }.toThrow<JsonParseException>().messageToContain("Unexpected 'nulls' at 1:6")
    expect { mapper.parse<Any>("""123.12.12""") }.toThrow<NumberFormatException>().messageToContain("multiple points")
    expect { mapper.parse<Any>("{\r\n\r\nx}") }.toThrow<JsonParseException>().messageToContain("Expecting '\"' but got 'x' at 3:1")
  }

  @Test fun `stream arrays`() {
    var n = 0
    JsonParser(StringReader("""[
      {"a":1},{"a":2},{"a":3},{"a":4}
    ]"""), mapper).readArray<JsonNode> {
      expect(it.getInt("a")).toEqual(++n)
    }
    expect(n).toEqual(4)
  }

  @Test fun `stream array error`() {
    var n = 0
    expect {
      JsonParser(StringReader("""[
        {"a":1},
        {"a":2},{"c":"aaa
        {"a":4}
      ]"""), mapper).readArray<JsonNode> {++n}
    }.toThrow<JsonParseException>().messageToContain("Unterminated string at 4:0")
    expect(n).toEqual(2)
  }

  @Test fun `parse into class`() {
    expect(mapper.parse<Hello>("""{
      "hellou": " x ", "id": "b8ca58ec-ab15-11ed-93cc-8fdb43988a14", "date": "2022-10-21", "instant": "2022-10-21T10:55:00Z",
      "nested": {"x": 567}, "array": [{}, {"x": 2}], "map": {"2022-10-21": {"y": 1}},"ignore": false, "readOnly":  false, "isBoolean" : false}
    """)).toEqual(
      Hello("x", "b8ca58ec-ab15-11ed-93cc-8fdb43988a14".uuid, LocalDate.parse("2022-10-21"), Instant.parse("2022-10-21T10:55:00Z"), Nested(567.toBigDecimal()),
        listOf(Nested(), Nested(x = 2.toBigDecimal())), mapOf(LocalDate.parse("2022-10-21") to Nested(y = 1)), isBoolean = false))
  }

  @Test fun `parse type parameter passed to parameter type`() {
    expect(mapper.parse<DataResponse<Nested>>("""{"data":{"x": 3}}""")).toEqual(DataResponse(Nested(3.toBigDecimal())))
    expect(mapper.parse<TypedData<Nested>>("""{"list": [{"x": 1}, {}], "map": {"k": {"x": 2}}}""")).toEqual(TypedData(listOf(Nested(ONE), Nested()), mapOf("k" to Nested(2.toBigDecimal()))))
  }

  @Test fun trimToNull() {
    val json = """{"x": "", "unknown": 123}"""
    expect(mapper.parse<Nullable>(json)).toEqual(Nullable())

    val mapper = mapper.copy(trimToNull = false)
    expect(mapper.parse<Nullable>(json)).toEqual(Nullable(""))
  }

  @Test fun converter() {
    val uuid = UUID.randomUUID()
    expect(mapper.parse<UUID>("\"$uuid\"")).toEqual(uuid)
    val tsid = TSID<Any>()
    expect(mapper.parse<TSID<Any>>("\"$tsid\"")).toEqual(tsid)

    expect(mapper.parse<LocalDate>("\"2022-12-23\"")).toEqual(LocalDate.of(2022, 12, 23))
  }

  @Test fun `custom converter with type params`() {
    Converter.use { s -> Hello::class.publicProperties[s]!! }
    expect(mapper.parse<FieldRule<UUID>>("""{"field": "id"}""")).toEqual(FieldRule(Hello::id))
    expect(mapper.parse<FieldRule<*>>("""{"field": "hello"}""")).toEqual(FieldRule(Hello::hello))
  }

  @Test fun `value converter from String`() {
    val mapper = mapper.copy(values = object: ValueConverter<Any?>() {
      override fun from(o: Any?, type: KType?) =
        if (o is String && type?.classifier == LocalDateTime::class) LocalDateTime.parse(o.replace(" ", "T")) else o
    })
    expect(mapper.parse<LocalDateTime>("\"2022-12-23 10:53:45\"")).toEqual(LocalDateTime.of(2022, 12, 23, 10, 53, 45))
  }

  @Test fun `value converter from number`() {
    val mapper = mapper.copy(values = object: ValueConverter<Any?>() {
      override fun from(o: Any?, type: KType?) =
        if (o is String && type?.classifier == Instant::class) Instant.ofEpochSecond(o.toLong()) else o
    })
    expect(mapper.parse<Instant>("123123123")).toEqual(Instant.ofEpochSecond(123123123))
  }

  @Test fun `snake case`() {
    val mapper = JsonMapper(keys = SnakeCase)
    expect(mapper.parse<Any>("""{"hello_world_is_good": 0}""")).toEqual(mapOf("helloWorldIsGood" to 0))
  }

  @Test fun `upper camel case`() {
    val mapper = JsonMapper(keys = Capitalize)
    expect(mapper.parse<Any>("""{"HelloWorld": true}""")).toEqual(mapOf("helloWorld" to true))
  }

  @Test fun `sealed class with default subtype names`() {
    val json = """{"shape": {"type": "Circle", "radius": 5.0}}"""
    val result = mapper.parse<Container>(json)
    expect(result).toEqual(Container(Shape.Circle(5.0)))
  }

  @Test fun `sealed class array`() {
    val json = """[{"type": "Circle", "radius": 5.0}, {"type": "Rect", "width": 10, "height": 20}]"""
    val result = mapper.parse<List<Shape>>(json)
    expect(result).toEqual(listOf(Shape.Circle(5.0), Shape.Rect(10, 20)))
  }

  @Test fun `sealed class with explicit subtypes`() {
    val json = """{"shape": {"kind": "circle", "radius": 5.0}}"""
    val result = mapper.parse<ContainerWithExplicitSubtypes>(json)
    expect(result).toEqual(ContainerWithExplicitSubtypes(Shape.Circle(5.0)))
  }

  @Test fun `sealed class with unknown discriminator`() {
    val json = """{"type": "Triangle", "sides": 3}"""
    expect { mapper.parse<Container>(json) }.toThrow<IllegalArgumentException>()
  }

  data class Nullable(val x: String? = null)
}

data class Hello(@JsonProperty("hellou") val hello: String, val id: UUID, val date: LocalDate, val instant: Instant, val nested: Nested,
                 val array: List<Nested> = emptyList(), val map: Map<LocalDate, Nested> = emptyMap(), val nullable: String? = null,
                 @JsonIgnore val ignore: Boolean = true, @JsonProperty(readOnly = true) val readOnly: Boolean = true, val isBoolean: Boolean = true) {
  val computed get() = 1
}
data class Nested(val x: BigDecimal = ZERO, val y: Int = 123)
data class TypedData<T>(val list: List<T>, val map: Map<String, T> = emptyMap())
data class FieldRule<T: Comparable<T>>(val field: KProperty1<out Hello, T>, val limits: Ranges<T> = emptyMap())
typealias Ranges<T> = Map<T, Decimal>
data class DataResponse<T>(val data: T)

@JsonSubTypes
sealed class Shape {
  data class Circle(val radius: Double): Shape()
  data class Rect(val width: Int, val height: Int): Shape()
}

data class Container(@JsonSubTypes val shape: Shape)

data class ContainerWithExplicitSubtypes(
  @JsonSubTypes(key = "kind", types = [
    Type("circle", Shape.Circle::class),
    Type("rect", Shape.Rect::class)
  ])
  val shape: Shape
)
