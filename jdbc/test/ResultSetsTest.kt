package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.sql.ResultSet

class ResultSetsTest {
  val rs = mockk<ResultSet>()

  @Test fun getObjectUnwrapped() {
    every { rs.getObject("x") } returns PGobject().apply { value = "citext" }
    expect(rs.getObjectUnwrapped("x")).toEqual("citext")
  }
}
