package klite

import ch.tutteli.atrium.api.fluent.en_GB.message
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

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

  data class SomeData(val hello: String, val world: Int, val nullable: String? = "default", val list: List<Int> = listOf(1, 2))
  data class AnotherData(val hello: String?)
}
