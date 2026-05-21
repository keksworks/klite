package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class SqlExprTest {
  @Test fun equals() {
    expect(SqlExpr("expr", 1, 2, 3)).toEqual(SqlExpr("expr", 1, 2, 3))
    expect(SqlOp(">", 1)).toEqual(SqlOp(">", 1))
    expect(SqlExpr("expr", 1)).notToEqual(SqlExpr("expr", 2))
    expect(SqlExpr("expr1", 1)).notToEqual(SqlExpr("expr2", 1))

    expect(SqlExpr("expr", 1, 2, 3).hashCode()).toEqual(3158614)
  }

  @Test fun orExpr() {
    val or = orExpr("column" to null, "column" to listOf(1, 2, 3), null)
    expect(or.expr).toEqual("(\"column\" is null or \"column\" in (?, ?, ?))")
    expect(or.values).toContainExactly(1, 2, 3)
  }

  @Test fun SqlComputed() {
    expect(SqlComputed("current_date").expr("date")).toEqual("date=current_date")
  }

  @Test fun SqlOp() {
    expect(SqlOp("<", 1).expr("n")).toEqual("n < ?")
    expect(SqlOp("<=", SqlComputed("current_date")).expr("date")).toEqual("date <= current_date")
    expect(("date" lte SqlComputed("123")).second.expr("x")).toEqual("x <= 123")
  }
}
