package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.sql.ResultSet

class ValuesTest {
  @Test fun create() {
    val rs = mockk<ResultSet> {
      every { getObject("hello") } returns "Hello"
      every { getInt("world") } returns 42
      every { wasNull() } returns false
      every { getObject("list") } returns mockk<java.sql.Array> { every { array } returns arrayOf(4, 5)}
      every { getObject("colName") } returns "colValue"
    }
    expect(rs.create<SomeData>()).toEqual(SomeData("Hello", 42, list = listOf(4, 5), propName = "colValue"))
  }

  @Test fun `create with some values provided`() {
    val rs = mockk<ResultSet>()
    expect(rs.create(SomeData::hello to "Hello", SomeData::world to 42, SomeData::nullable to null,
      SomeData::list to listOf(9), SomeData::propName to "propName"))
      .toEqual(SomeData("Hello", 42, list = [9], propName = "propName"))
  }

  data class SomeData(
    val hello: String,
    val world: Int,
    val nullable: String? = null,
    val list: List<Int> = [1, 2],
    @Column("colName") val propName: String
  )
}
