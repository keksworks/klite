package klite

import ch.tutteli.atrium.api.fluent.en_GB.message
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf

class ValuesTest {
  @Test fun toValues() {
    val data = SomeData("Hello", 123, nullable = null)
    expect(data.toValues()).toEqual(mapOf(
      SomeData::hello to "Hello", SomeData::world to 123, SomeData::nullable to null, SomeData::list to listOf(1, 2)))
    expect(data.toValues(SomeData::world to 124)).toEqual(mapOf(
      SomeData::hello to "Hello", SomeData::world to 124, SomeData::nullable to null, SomeData::list to listOf(1, 2)))
    expect(data.toValues().create<SomeData>()).toEqual(data)
  }

  @Test fun toValuesSkipping() {
    val data = SomeData("Hello", 123)
    expect(data.toValuesSkipping(SomeData::nullable, SomeData::list)).toEqual(mapOf(SomeData::hello to "Hello", SomeData::world to 123))
    expect(data.toValuesSkipping(SomeData::hello, SomeData::world, SomeData::nullable, SomeData::list)).toBeEmpty()
  }

  @Test fun createFrom() {
    val values = mapOf("hello" to "Hello", "world" to 34)
    expect(SomeData::class.createFrom(values)).toEqual(SomeData("Hello", 34))
    expect(values.create<SomeData>()).toEqual(SomeData("Hello", 34))
  }

  @Test fun `create with explicit nullable`() {
    val values = mapOf("hello" to "", "world" to 0, "nullable" to null, "list" to null)
    expect(SomeData::class.createFrom(values)).toEqual(SomeData("", 0, nullable = null))

    expect(AnotherData::class.createFrom(emptyMap())).toEqual(AnotherData(null))
  }

  @Test fun `descriptive error message`() {
    val values = mapOf("world" to 34)
    expect { SomeData::class.createFrom(values) }.toThrow<IllegalArgumentException>()
      .message.toEqual("Cannot create SomeData from {world=34}: missing hello")
  }

  @Test fun `convert nested values`() {
    val values = mapOf(
      "ids" to listOf("A", "B"),
      "items" to listOf(mapOf("name" to "x", "status" to "DONE", "tags" to listOf("t"))),
      "counts" to mapOf("x" to "5"))
    expect(typeOf<Order>().convertFrom(values)).toEqual(Order(setOf("A", "B"), listOf(Item("x", Status.DONE, listOf("t"))), mapOf("x" to 5)))
  }

  data class SomeData(val hello: String, val world: Int, val nullable: String? = "default", val list: List<Int> = listOf(1, 2))
  data class AnotherData(val hello: String?)
  enum class Status { NEW, DONE }
  data class Item(val name: String, val status: Status, val tags: List<String> = emptyList())
  data class Order(val ids: Set<String>, val items: List<Item>, val counts: Map<String, Int> = emptyMap())
}
