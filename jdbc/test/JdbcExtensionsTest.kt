package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.d
import org.junit.jupiter.api.Test

class JdbcExtensionsTest {
  val db = ConfigDataSource("jdbc:postgres")
  val values = mapOf(
    "hello" to "world",
    "nullable" to null,
    "array" to listOf(1, 2, 3),
    "emptyArray" to emptyArray<Any>(),
    "date" to SqlComputed("current_date"),
    "json" to jsonb("{}"),
    "decimal" to 1000.d
  )

  @Test fun insertExpr() {
    expect(db.insertExpr("table", values)).toEqual("insert into \"table\" (hello, nullable, array, emptyArray, date, json, decimal)" +
      " values (?, ?, ?, '{}', current_date, ?::jsonb, ?::decimal)")
  }

  @Test fun setExpr() {
    expect(db.setExpr(values)).toEqual("hello=?, nullable=?, array=?, emptyArray='{}', date=current_date, json=?::jsonb, decimal=?::decimal")
    expect(db.setValues(values).toList()).toContainExactly("world", null, listOf(1, 2, 3), "{}", 1000.d)
  }

  @Test fun whereExpr() {
    val where = whereConvert(values.map { it.key to it.value }) + sql("exists (subselect)") + or("a" to "b", "array" any 123, "something" like "x%", "num" gte 1)
    expect(db.whereExpr(where)).toEqual(" where hello=? and nullable is null and array" +
      (if (db.isPostgres) " = any(?) and emptyArray='{}'" else " in (?, ?, ?) and emptyArray='{}'") +
      " and date=current_date and json=?::jsonb and decimal=?::decimal and exists (subselect) and (a=? or ?=any(array) or something like ? or num >= ?)")
    val expectedValues: List<Any?> = if (db.isPostgres)
      listOf("world", listOf(1, 2, 3), "{}", 1000.d, "b", 123, "x%", 1)
    else listOf("world", 1, 2, 3, "{}", 1000.d, "b", 123, "x%", 1)
    expect(db.whereValues(where).toList()).toEqual(expectedValues)
  }

  @Test fun directCollectionWherePair() {
    val where = whereConvert(listOf("array" to listOf(1, 2, 3), "excluded" to NotIn("a", "b")))
    expect(db.whereExpr(where)).toEqual(" where array" +
      if (db.isPostgres) " = any(?) and excluded <> all(?)" else " in (?, ?, ?) and excluded not in (?, ?)")
    val expectedValues: List<Any?> = if (db.isPostgres) listOf(listOf(1, 2, 3), listOf("a", "b")) else listOf(1, 2, 3, "a", "b")
    expect(db.whereValues(where).toList()).toEqual(expectedValues)
  }
}
