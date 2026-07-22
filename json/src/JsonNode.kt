@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package klite.json

import klite.d
import klite.nodes.*

typealias JsonNode = Node
typealias JsonList = List<JsonNode>

@kotlin.internal.HidesMembers @Deprecated("Use child()", ReplaceWith("child(key)", "klite.nodes"))
inline fun <T: Any> JsonNode.get(key: String) = child<T>(key)

inline fun <T: Any> JsonNode.getOrNull(key: String) = childOrNull<T>(key)

inline fun JsonNode.getString(key: String) = text(key)
inline fun JsonNode.getInt(key: String) = child<Number>(key).toInt()
inline fun JsonNode.getLong(key: String) = child<Number>(key).toLong()
inline fun JsonNode.getBigDecimal(key: String) = text(key).toBigDecimal()
inline fun JsonNode.getDecimal(key: String) = text(key).d
inline fun JsonNode.getBoolean(key: String) = child<Boolean>(key)

@Deprecated("Use children()", ReplaceWith("children<T>(key)", "klite.nodes"))
inline fun <T> JsonNode.getList(key: String) = children<T>(key)
inline fun <T> JsonNode.getMap(key: String) = child<Map<String, T>>(key)

@Deprecated("Use at()", ReplaceWith("at(key)", "klite.nodes"))
inline fun JsonNode.getNode(key: String): JsonNode = at(key)
