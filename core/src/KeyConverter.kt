package klite

import kotlin.reflect.KType

typealias KeyConverter = ValueConverter<String>
open class ValueConverter<T> {
  open fun to(o: T) = o
  open fun from(o: T) = o
  open fun from(o: T, type: KType?) = from(o)
}

object SnakeCase: KeyConverter() {
  private val humps = "(?<=.)(?=\\p{Upper})".toRegex()
  override fun to(o: String) = o.replace(humps, "_").lowercase()
  override fun from(o: String) = o.split('_').joinToString("") { it.replaceFirstChar { it.uppercaseChar() } }.replaceFirstChar { it.lowercaseChar() }
}

object Capitalize: KeyConverter() {
  override fun to(o: String) = o.replaceFirstChar { it.uppercaseChar() }
  override fun from(o: String) = o.replaceFirstChar { it.lowercaseChar() }
}
