package klite.json

import klite.Converter
import klite.Decimal
import klite.TSID
import klite.json.JsonSubTypes.Type
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KProperty1

data class Hello(@JsonProperty("hellou") val hello: String, val id: UUID, val date: LocalDate, val instant: Instant, val nested: Nested,
                 val array: List<Nested> = emptyList(), val map: Map<LocalDate, Nested> = emptyMap(), val nullable: String? = null,
                 @JsonIgnore val ignore: Boolean = true, @JsonProperty(readOnly = true) val readOnly: Boolean = true, val isBoolean: Boolean = true) {
  val computed get() = 1
}

data class Nullable(val x: String? = null)

data class Nested(val x: BigDecimal = ZERO, val y: Int = 123)

data class TypedData<T>(val list: List<T>, val map: Map<String, T> = emptyMap())

data class FieldRule<T: Comparable<T>>(val field: KProperty1<out Hello, T>, val limits: Ranges<T> = emptyMap())

typealias Ranges<T> = Map<T, Decimal>

data class DataResponse<T>(val data: T)

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

@JvmInline value class NumCode<T: Any>(val value: Long) {
  companion object {
    init { Converter.use { NumCode<Any>(it.toLong()) } }
  }
  override fun toString() = value.toString()
}

@JvmInline value class InlineInt(val n: Int)
@JvmInline value class InlineString(val s: String)

@JvmInline value class CountryCode(val value: String) {
  val isCountry get() = value.length == 2
}

@JvmInline value class MyId<out T>(val uuid: UUID = UUID.randomUUID())

enum class SomeEnum { HELLO, WORLD }
enum class EnumWithFields(val bool: Boolean, val num: Int, val other: SomeEnum) {
  HELLO(true, 1, SomeEnum.HELLO), WORLD(false, 2, SomeEnum.WORLD)
}

interface Person { val name: String; val hello get() = SomeEnum.HELLO; }

data class SomeData(override val name: String, val age: Int, val birthDate: LocalDate?, val id: MyId<SomeData>, val other: SomeData?,
                    val list: List<SomeData>, val map: Map<SomeEnum, Array<SomeData>>, val any: Any, val status: Status = Status.ACTIVE,
                    val field: KProperty1<Person, *>, val bytes: ByteArray, val tsid: TSID<SomeData>): Person {
  enum class Status { ACTIVE }
}

interface NoProps { fun onlyMethods() }

data class CustomTypes(val id: TSID<CustomTypes>, val date: LocalDate)
