package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.d
import org.junit.jupiter.api.Test

class JdbcExtensionsTest {
  val db = ConfigDataSource("jdbc:postgres")
  val otherDb = ConfigDataSource("jdbc:h2:mem:test")
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
    expect(otherDb.insertExpr("table", values)).toEqual("insert into \"table\" (hello, nullable, array, emptyArray, date, json, decimal)" +
      " values (?, ?, ?, '{}', current_date, ?::jsonb, ?)")
  }

  @Test fun setExpr() {
    expect(db.setExpr(values)).toEqual("hello=?, nullable=?, array=?, emptyArray='{}', date=current_date, json=?::jsonb, decimal=?::decimal")
    expect(db.setValues(values).toList()).toContainExactly("world", null, listOf(1, 2, 3), "{}", 1000.d)
    expect(otherDb.setExpr(values)).toEqual("hello=?, nullable=?, array=?, emptyArray='{}', date=current_date, json=?::jsonb, decimal=?")
    expect(otherDb.setValues(values).toList()).toContainExactly("world", null, listOf(1, 2, 3), "{}", 1000.d)
  }

  @Test fun whereExpr() {
    val where = whereConvert(values.map { it.key to it.value }) + sql("exists (subselect)") + or("a" to "b", "array" any 123, "something" like "x%", "num" gte 1)
    expect(db.whereExpr(where)).toEqual(" where hello=? and nullable is null and array = any(?) and emptyArray='{}' and date=current_date and json=?::jsonb and decimal=?::decimal and exists (subselect) and (a=? or ?=any(array) or something like ? or num >= ?)")
    expect(db.whereValues(where).toList()).toEqual(listOf<Any?>("world", listOf(1, 2, 3), "{}", 1000.d, "b", 123, "x%", 1))
    expect(otherDb.whereExpr(where)).toEqual(" where hello=? and nullable is null and array in (?, ?, ?) and emptyArray='{}' and date=current_date and json=?::jsonb and decimal=? and exists (subselect) and (a=? or ?=any(array) or something like ? or num >= ?)")
    expect(otherDb.whereValues(where).toList()).toEqual(listOf<Any?>("world", 1, 2, 3, "{}", 1000.d, "b", 123, "x%", 1))
  }

  @Test fun directCollectionWherePair() {
    val where = whereConvert(listOf("array" to listOf(1, 2, 3), "excluded" to NotIn("a", "b")))
    expect(db.whereExpr(where)).toEqual(" where array = any(?) and excluded <> all(?)")
    expect(db.whereValues(where).toList()).toEqual(listOf<Any?>(listOf(1, 2, 3), listOf("a", "b")))
    expect(otherDb.whereExpr(where)).toEqual(" where array in (?, ?, ?) and excluded not in (?, ?)")
    expect(otherDb.whereValues(where).toList()).toEqual(listOf<Any?>(1, 2, 3, "a", "b"))
  }
}
