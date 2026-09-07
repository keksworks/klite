package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class SqlExprTest {
  val pgDb = ConfigDataSource("jdbc:postgres")

  @Test fun equals() {
    expect(SqlExpr("expr", 1, 2, 3)).toEqual(SqlExpr("expr", 1, 2, 3))
    expect(SqlOp(">", 1)).toEqual(SqlOp(">", 1))
    expect(SqlExpr("expr", 1)).notToEqual(SqlExpr("expr", 2))
    expect(SqlExpr("expr1", 1)).notToEqual(SqlExpr("expr2", 1))
    expect(In(123, 234)).toEqual(In(123, 234))
    expect(NotIn(123, 234)).toEqual(NotIn(123, 234))
    expect(NotIn(123, 234) as SqlExpr).notToEqual(In(123, 234))

    expect(SqlExpr("expr", 1, 2, 3).hashCode()).toEqual(3158614)
  }

  @Test fun orExpr() {
    val or = orExpr("column" to null, "column" to listOf(1, 2, 3), null)
    expect(or.expr(pgDb, "")).toEqual("(\"column\" is null or \"column\"" +
      if (pgDb.isPostgres) " = any(?))" else " in (?, ?, ?))")
    val expectedValues: List<Any?> = if (pgDb.isPostgres) listOf(listOf(1, 2, 3)) else listOf(1, 2, 3)
    expect(or.values.toList()).toEqual(expectedValues)
  }

  @Test fun inOperators() {
    expect(In(1, 2, 3).expr(pgDb, "column")).toEqual("\"column\"" +
      if (pgDb.isPostgres) " = any(?)" else " in (?, ?, ?)")
    expect(NotIn(1, 2, 3).expr(pgDb, "column")).toEqual("\"column\"" +
      if (pgDb.isPostgres) " <> all(?)" else " not in (?, ?, ?)")
  }

  @Test fun expressionValuesBindingStrategy() {
    val and = andExpr("column" to Between(1, 2), "array" to listOf(3, 4))
    expect(and.expr(pgDb, "")).toEqual("(\"column\" between ? and ? and array" +
      if (pgDb.isPostgres) " = any(?))" else " in (?, ?))")
    val expectedValues: List<Any?> = if (pgDb.isPostgres) listOf(1, 2, listOf(3, 4)) else listOf(1, 2, 3, 4)
    expect(and.values.toList()).toEqual(expectedValues)
  }

  @Test fun SqlComputed() {
    expect(SqlComputed("current_date").expr(pgDb, "date")).toEqual("date=current_date")
  }

  @Test fun SqlOp() {
    expect(SqlOp("<", 1).expr(pgDb, "n")).toEqual("n < ?")
    expect(SqlOp("<=", SqlComputed("current_date")).expr(pgDb, "date")).toEqual("date <= current_date")
    expect(("date" lte SqlComputed("123")).second.expr(pgDb, "x")).toEqual("x <= 123")
  }
}
