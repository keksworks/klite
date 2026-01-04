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

  // TODO vision
  // db.update("table").where("id" to 1) /* PreparedExecutor */.run(v.toValues())
  // db.delete("table").where("id" to 1) /* PreparedExecutor */.run()
  // db.insert("table").columns(...) /* PreparedExecutor */.run(v.toValues())
  // db.insert("table").columns(...) /* PreparedExecutor */.batch(sequenceOf(v.toValues()))
  // TableOperations("table", cols).insert(v.toValues())
  // TableOperations("table", cols).update.where("id" to 1).run(v.toValues())

  @Test fun map() {
    val rs = mockk<ResultSet>(relaxed = true)
    every { db.connection.prepareStatement(any(), any<Int>()) } returns mockk(relaxed = true) {
      every { executeQuery() } returns rs
    }
    every { rs.next() } returnsMany listOf(true, true, false)
    every { rs.getInt("id") } returnsMany listOf(1, 2)

    val result = db.select("table").join("table2", "on xxx").where("id" to 1).map { getInt("id") }.toList()
    // val result = db.select("table", "id" to 1) { getInt("id") }
    verify(exactly = 0) { rs.close() }

    expect(result.toList()).toContainExactly(1, 2)

    verify { rs.close() }
    verify { db.connection.close() }
  }
}
