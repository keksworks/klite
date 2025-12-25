package klite.jdbc

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
  val database = Database(db)

  @Test fun list() {
    val rs = mockk<ResultSet>(relaxed = true)
    every { db.connection.prepareStatement(any()) } returns mockk(relaxed = true) {
      every { executeQuery() } returns rs
    }
    every { rs.next() } returnsMany listOf(true, true, false)
    every { rs.getInt("id") } returnsMany listOf(1, 2)

    val results = database.select("users").where("id" to 1).list { getInt("id") }
    verify(exactly = 0) { rs.close() }

    expect(results.toList()).toContainExactly(1, 2)

    verify { rs.close() }
    verify { db.connection.close() }
  }
}
