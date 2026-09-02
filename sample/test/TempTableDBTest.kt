package klite.sample

import klite.jdbc.exec
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

@TestInstance(PER_CLASS)
abstract class TempTableDBTest: DBTest() {
  val table = "temp"

  @BeforeAll
  fun before() {
    db.exec("create table $table(id uuid primary key, hello varchar, world int, gen serial, json jsonb)")
  }

  @AfterAll
  fun after() {
    db.exec("drop table $table")
  }
}
