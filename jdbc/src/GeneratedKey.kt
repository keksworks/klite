package klite.jdbc

import java.sql.Statement
import kotlin.reflect.KClass

class GeneratedKey<T: Any>(val convertTo: KClass<T>? = null) {
  lateinit var value: T
}

@Suppress("UNCHECKED_CAST")
internal fun Statement.processGeneratedKeys(values: Sequence<ValueMap>) {
  val i = values.iterator()
  val rs = generatedKeys
  while (rs.next()) {
    i.next().forEach { (k, v) ->
      val n = name(k)
      (v as? GeneratedKey<Any>)?.let {
        val value = if (it.convertTo != null) rs.getString(n) else rs.getObject(n)
        it.value = JdbcConverter.from(value, it.convertTo) as Any
      }
    }
  }
}
