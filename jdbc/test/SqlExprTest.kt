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
    expect(or.expr).toEqual("(\"column\" is null or \"column\"" +
      if (isPostgres) " = any(?))" else " in (?, ?, ?))")
    expect(or.values).toContainExactly(if (isPostgres) listOf(listOf(1, 2, 3)) else listOf(1, 2, 3))
  }

  @Test fun inOperators() {
    expect(In(1, 2, 3).expr("column")).toEqual("\"column\"" +
      if (isPostgres) " = any(?)" else " in (?, ?, ?)")
    expect(NotIn(1, 2, 3).expr("column")).toEqual("\"column\"" +
      if (isPostgres) " <> all(?)" else " not in (?, ?, ?)")
  }

  @Test fun expressionValuesAreFlattened() {
    val and = andExpr("column" to Between(1, 2), "array" to listOf(3, 4))
    expect(and.expr).toEqual("(\"column\" between ? and ? and \"array\"" +
      if (isPostgres) " = any(?))" else " in (?, ?))")
    expect(and.values).toContainExactly(if (isPostgres) listOf(1, 2, listOf(3, 4)) else listOf(1, 2, 3, 4))
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
