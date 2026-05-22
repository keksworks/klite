package klite.jdbc

import java.sql.Statement
import kotlin.reflect.KClass

class GeneratedKey<T: Any>(val convertTo: KClass<T>? = null) {
  lateinit var value: T
}

@Suppress("UNCHECKED_CAST")
internal fun Statement.processGeneratedKeys(values: Iterable<ValueMap>) {
  val i = values.iterator()
  generatedKeys.process {
    i.next().forEach { (k, v) ->
      val n = name(k)
      (v as? GeneratedKey<Any>)?.let {
        val value = if (it.convertTo != null) getString(n) else getObject(n)
        it.value = JdbcConverter.from(value, it.convertTo) as Any
      }
    }
  }
}
