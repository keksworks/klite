package klite.jdbc.dsl

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.sql.ResultSet
import javax.sql.DataSource

class DatabaseTest {
  val db = mockk<DataSource>(relaxed = true)

  @Test fun map() {
    val rs = mockk<ResultSet>(relaxed = true)
    every { db.connection.prepareStatement(any(), any<Int>()) } returns mockk(relaxed = true) {
      every { executeQuery() } returns rs
    }
    every { rs.next() } returnsMany listOf(true, true, false)
    every { rs.getInt("id") } returnsMany listOf(1, 2)

    val result = db.select("table").where("id" to 1).map { getInt("id") }.run()
    verify(exactly = 0) { rs.close() }

    expect(result.toList()).toContainExactly(1, 2)

    verify { rs.close() }
    verify { db.connection.close() }
  }
}
