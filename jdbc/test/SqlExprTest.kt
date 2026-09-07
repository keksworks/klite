package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SqlExprTest {
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

  @Nested inner class `postgres` {
    val db = ConfigDataSource("jdbc:postgres")

    @Test fun orExpr() {
      val or = orExpr("column" to null, "column" to listOf(1, 2, 3), null)
      expect(or.expr(db, "")).toEqual("(\"column\" is null or \"column\" = any(?))")
      expect(or.values(db).toList()).toEqual(listOf<Any?>(listOf(1, 2, 3)))
    }

    @Test fun inOperators() {
      expect(In(1, 2, 3).expr(db, "column")).toEqual("\"column\" = any(?)")
      expect(NotIn(1, 2, 3).expr(db, "column")).toEqual("\"column\" <> all(?)")
    }

    @Test fun expressionValuesBindingStrategy() {
      val and = andExpr("column" to Between(1, 2), "array" to listOf(3, 4))
      expect(and.expr(db, "")).toEqual("(\"column\" between ? and ? and array = any(?))")
      expect(and.values(db).toList()).toEqual(listOf<Any?>(1, 2, listOf(3, 4)))
    }

    @Test fun SqlComputed() {
      expect(SqlComputed("current_date").expr(db, "date")).toEqual("date=current_date")
    }

    @Test fun SqlOp() {
      expect(SqlOp("<", 1).expr(db, "n")).toEqual("n < ?")
      expect(SqlOp("<=", SqlComputed("current_date")).expr(db, "date")).toEqual("date <= current_date")
      expect(("date" lte SqlComputed("123")).second.expr(db, "x")).toEqual("x <= 123")
    }
  }

  @Nested inner class `non-postgres` {
    val db = ConfigDataSource("jdbc:h2:mem:test")

    @Test fun orExpr() {
      val or = orExpr("column" to null, "column" to listOf(1, 2, 3), null)
      expect(or.expr(db, "")).toEqual("(\"column\" is null or \"column\" in (?, ?, ?))")
      expect(or.values(db).toList()).toEqual(listOf<Any?>(1, 2, 3))
    }

    @Test fun inOperators() {
      expect(In(1, 2, 3).expr(db, "column")).toEqual("\"column\" in (?, ?, ?)")
      expect(NotIn(1, 2, 3).expr(db, "column")).toEqual("\"column\" not in (?, ?, ?)")
    }

    @Test fun expressionValuesBindingStrategy() {
      val and = andExpr("column" to Between(1, 2), "array" to listOf(3, 4))
      expect(and.expr(db, "")).toEqual("(\"column\" between ? and ? and array in (?, ?))")
      expect(and.values(db).toList()).toEqual(listOf<Any?>(1, 2, 3, 4))
    }
  }
}
