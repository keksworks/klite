package klite.jdbc

internal const val namedParamPrefix = ':'

/**
 * Matches a whole token which can contain colons/question marks without them being parameters:
 * strings, quoted identifiers, comments, and dollar-quoted strings, plus `::` casts.
 * Groups: 1 - dollar-quote tag, 2 - named parameter name.
 */
private val sqlToken = Regex("""'(?:[^']|'')*'|"(?:[^"]|"")*"|--[^\n]*|/\*[\s\S]*?\*/|(\$(?:[A-Za-z_]\w*)?\$)[\s\S]*?\1|::|:([A-Za-z_]\w*)|\?""")

/** Where keys starting with `:` are named parameters, the rest are regular conditions */
internal fun Where.splitNamedParams(): Pair<Map<String, Any?>, Where> {
  if (none { isNamedParam(it.first) }) return emptyMap<String, Any?>() to this
  val (named, conditions) = partition { isNamedParam(it.first) }
  return named.associate { (key, value) -> (key as String).substring(1) to value } to conditions
}

private fun isNamedParam(key: ColName) = key is String && key.startsWith(namedParamPrefix)

/**
 * Replaces every `:name` in [sql] with a `?` placeholder and returns the values in placeholder order.
 * Colons inside strings, quoted identifiers, comments, and `::` casts are left untouched.
 * Positional values (from regular conditions) are consumed by `?` placeholders to preserve their order.
 */
internal fun resolveNamedParams(sql: String, named: Map<String, Any?>, positional: List<Any?> = emptyList()): Pair<String, List<Any?>> {
  if (named.isEmpty()) return sql to positional
  val positionals = positional.iterator()
  val values = mutableListOf<Any?>()
  val resolved = sqlToken.replace(sql) { token ->
    val name = token.groupValues[2]
    when {
      token.value == "?" -> {
        if (positionals.hasNext()) values.add(positionals.next())
        token.value
      }
      name.isNotEmpty() -> {
        require(named.containsKey(name)) { "Missing value for named parameter :$name in SQL: $sql" }
        values.add(named[name])
        "?"
      }
      else -> token.value
    }
  }
  while (positionals.hasNext()) values.add(positionals.next())
  return resolved to values
}
