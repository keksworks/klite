package klite

import java.io.OutputStream
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType

fun String?.trimToNull() = this?.trim()?.takeIf { it.isNotEmpty() }

fun OutputStream.write(s: String) = write(s.toByteArray())
fun OutputStream.writeln(s: String? = null) { if (s != null) write(s); write(10) }
fun OutputStream.writecrlf(s: String? = null) { if (s != null) write(s); write(13); write(10) }

@Suppress("UNCHECKED_CAST")
fun <K, V> notNullValues(vararg pairs: Pair<K, V?>?) = pairs.filter { it?.second != null } as List<Pair<K, V>>
fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V?>?) = notNullValues(*pairs).toMap()

val KType.java get() = (classifier as KClass<*>).java
fun Any.unboxInline() = javaClass.getMethod("unbox-impl").invoke(this)

val String.uuid: UUID get() = UUID.fromString(this)

fun <T: Comparable<T>> T.min(o: T) = if (this <= o) this else o
fun <T: Comparable<T>> T.max(o: T) = if (this >= o) this else o
