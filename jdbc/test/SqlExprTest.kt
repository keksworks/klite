package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class SqlExprTest {
  val pgDb = ConfigDataSource("jdbc:postgres")
  val otherDb = ConfigDataSource("jdbc:h2:mem:test")

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
    expect(or.expr(pgDb, "")).toEqual("(\"column\" is null or \"column\" = any(?))")
    expect(or.values(pgDb).toList()).toEqual(listOf<Any?>(listOf(1, 2, 3)))
    expect(or.expr(otherDb, "")).toEqual("(\"column\" is null or \"column\" in (?, ?, ?))")
    expect(or.values(otherDb).toList()).toEqual(listOf<Any?>(1, 2, 3))
  }

  @Test fun inOperators() {
    expect(In(1, 2, 3).expr(pgDb, "column")).toEqual("\"column\" = any(?)")
    expect(NotIn(1, 2, 3).expr(pgDb, "column")).toEqual("\"column\" <> all(?)")
    expect(In(1, 2, 3).expr(otherDb, "column")).toEqual("\"column\" in (?, ?, ?)")
    expect(NotIn(1, 2, 3).expr(otherDb, "column")).toEqual("\"column\" not in (?, ?, ?)")
  }

  @Test fun expressionValuesBindingStrategy() {
    val and = andExpr("column" to Between(1, 2), "array" to listOf(3, 4))
    expect(and.expr(pgDb, "")).toEqual("(\"column\" between ? and ? and array = any(?))")
    expect(and.values(pgDb).toList()).toEqual(listOf<Any?>(1, 2, listOf(3, 4)))
    expect(and.expr(otherDb, "")).toEqual("(\"column\" between ? and ? and array in (?, ?))")
    expect(and.values(otherDb).toList()).toEqual(listOf<Any?>(1, 2, 3, 4))
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
