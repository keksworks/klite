package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class NamedParamsTest {
  @Test fun `replaces named parameters in order`() {
    val (sql, values) = resolveNamedParams("select * from t where a = :a and b = :b or a = :a", mapOf("a" to 1, "b" to "x"))
    expect(sql).toEqual("select * from t where a = ? and b = ? or a = ?")
    expect(values).toEqual(listOf<Any?>(1, "x", 1))
  }

  @Test fun `leaves colons in strings, quoted identifiers, comments and casts`() {
    val (sql, values) = resolveNamedParams(
      """select ':a', "x:a", a::text, $$ :a $$, ${'$'}tag${'$'} :a ${'$'}tag${'$'} from t -- :a
        |where b = :b /* :a */ and c::int = :b""".trimMargin(),
      mapOf("a" to 1, "b" to 2)
    )
    expect(sql).toEqual(
      """select ':a', "x:a", a::text, $$ :a $$, ${'$'}tag${'$'} :a ${'$'}tag${'$'} from t -- :a
        |where b = ? /* :a */ and c::int = ?""".trimMargin()
    )
    expect(values).toEqual(listOf<Any?>(2, 2))
  }

  @Test fun `interleaves positional and named parameters`() {
    val (sql, values) = resolveNamedParams("select * from t where a = ? and b = :b and c = ?", mapOf("b" to 2), listOf(1, 3))
    expect(sql).toEqual("select * from t where a = ? and b = ? and c = ?")
    expect(values).toEqual(listOf<Any?>(1, 2, 3))
  }

  @Test fun `binds null named parameter`() {
    val (sql, values) = resolveNamedParams("select * from t where a = :a", mapOf("a" to null))
    expect(sql).toEqual("select * from t where a = ?")
    expect(values).toEqual(listOf<Any?>(null))
  }

  @Test fun `fails on missing named parameter`() {
    expect { resolveNamedParams("select * from t where a = :missing", mapOf("a" to 1)) }
      .toThrow<IllegalArgumentException>().messageToContain(":missing")
  }

  @Test fun `splits named parameters from conditions`() {
    val (named, conditions) = listOf<ColValue>("a" to 1, ":b" to 2, ":c" to null).splitNamedParams()
    expect(named).toEqual(mapOf("b" to 2, "c" to null))
    expect(conditions).toEqual(listOf<ColValue>("a" to 1))
  }
}
