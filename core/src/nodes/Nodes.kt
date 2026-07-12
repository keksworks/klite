package klite.nodes

import klite.Converter

typealias Node = Map<String, Any?>

fun <T: Any> Node.childOrNull(key: String) = get(key) as T?
fun <T: Any> Node.child(key: String) = (childOrNull<T>(key) ?: throw NullPointerException("$key is absent"))
fun <T> Node.children(key: String): List<T> = childOrNull<Any>(key).let {
  if (it == null) emptyList() else it as? List<T> ?: listOf(it as T)
}
fun Node.at(key: String) = child<Node>(key)
fun Node.nodes(key: String): List<Node> = children(key)
fun Node.text(key: String) = child<Any>(key).toString()
fun Node.textOrNull(key: String) = childOrNull<Any>(key)?.toString()
inline fun <reified T: Any> Node.value(key: String) = Converter.from<T>(text(key))
inline fun <reified T: Any> Node.valueOrNull(key: String) = textOrNull(key)?.let { Converter.from<T>(it) }
