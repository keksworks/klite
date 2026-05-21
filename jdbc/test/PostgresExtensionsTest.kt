package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class PostgresExtensionsTest {
  @Test fun joinAliases() {
    expect(joinAliases("select * from table1 join table2 on x=y left join table3 t3 on t3.x = y inner join table4 as t4 using(id) where ..."))
      .toContainExactly("table2", "t3", "t4")

    expect(joinAliases("select * from table1\njoin table2\n   on x=y\nleft join\ntable3 t3 on t3.x = y inner join table4 as t4 using(id) where ..."))
      .toContainExactly("table2", "t3", "t4")

    expect(joinAliases("select a, b from table1 t1 join table2 t2 using (field) where ... order by id"))
      .toContainExactly("t2")
  }
}
