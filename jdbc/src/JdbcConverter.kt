package klite.jdbc

import klite.Converter
import klite.Decimal
import klite.annotations.annotation
import klite.d
import klite.unboxInline
import java.lang.reflect.Modifier.STATIC
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.sql.Connection
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.*
import java.time.ZoneOffset.UTC
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

typealias ToJdbcConverter<T> = (T, Connection?) -> Any

@ExperimentalUuidApi @ExperimentalTime
object JdbcConverter {
  val nativeTypes: MutableSet<KClass<*>> = mutableSetOf(
    UUID::class, BigDecimal::class, BigInteger::class, LocalDate::class, LocalDateTime::class, LocalTime::class, OffsetDateTime::class
  )
  private val converters: MutableMap<KClass<*>, ToJdbcConverter<*>> = ConcurrentHashMap()

  init {
    use<Instant> { v, _ -> v.atOffset(UTC) }
    use<kotlin.time.Instant> { v, _ -> Timestamp(v.toEpochMilliseconds()) }
    use<Uuid> { v, _ -> v.toJavaUuid() }

    val toString: ToJdbcConverter<Any> = { v, _ -> v.toString() }
    use<Period>(toString)
    use<Duration>(toString)
    use<kotlin.time.Duration>(toString)
    use<Currency>(toString)
    use<Locale>(toString)
    use<URL>(toString)
    use<URI>(toString)
  }

  operator fun <T: Any> set(type: KClass<T>, converter: ToJdbcConverter<T>) { converters[type] = converter }
  inline fun <reified T: Any> use(noinline converter: ToJdbcConverter<T>) = set(T::class, converter)

  @Suppress("UNCHECKED_CAST")
  fun to(v: Any?, conn: Connection? = null): Any? {
    if (v == null) return null
    val cls = v::class
    return when {
      converters.contains(cls) -> (converters[cls] as ToJdbcConverter<Any>).invoke(v, conn)
      cls.javaPrimitiveType != null || nativeTypes.contains(cls) -> v
      cls.isValue && cls.hasAnnotation<JvmInline>() -> v.unboxInline()
      Converter.supports(cls) -> v.toString()
      v is Collection<*> -> conn!!.createArrayOf(arrayType(v.firstOrNull()?.javaClass), v.map { to(it, conn) }.toTypedArray())
      v is Array<*> -> conn!!.createArrayOf(arrayType(v.javaClass.componentType), v.map { to(it, conn) }.toTypedArray())
      else -> v
    }
  }

  private fun arrayType(c: Class<*>?): String = when {
    c == null -> "varchar"
    UUID::class.java.isAssignableFrom(c) || Uuid::class.java.isAssignableFrom(c) -> "uuid"
    Number::class.java.isAssignableFrom(c) || c.isPrimitive -> "numeric"
    LocalDate::class.java.isAssignableFrom(c) -> "date"
    LocalTime::class.java.isAssignableFrom(c) -> "time"
    LocalDateTime::class.java.isAssignableFrom(c) -> "timestamp"
    Instant::class.java.isAssignableFrom(c) || kotlin.time.Instant::class.java.isAssignableFrom(c) -> "timestamptz"
    c.isAnnotationPresent(JvmInline::class.java) -> arrayType(c.declaredFields.find { it.modifiers and STATIC == 0 }?.type)
    else -> "varchar"
  }

  fun from(v: Any?, target: KType): Any? = if (v is java.sql.Array) {
    val targetClass = target.jvmErasure
    if (targetClass.java.isArray) v.array else {
      val list = (v.array as Array<*>).map { from(it, target.arguments[0].type!!) }
      if (targetClass == Set::class) list.toSet()
      else list
    }
  } else from(v, target.jvmErasure)

  fun from(v: Any?, target: KClass<*>?): Any? = when(target) {
    Instant::class -> (v as? Timestamp)?.toInstant()
    kotlin.time.Instant::class -> (v as? Timestamp)?.let { kotlin.time.Instant.fromEpochMilliseconds(v.time) }
    Uuid::class -> (v as? UUID)?.toKotlinUuid()
    LocalDate::class -> (v as? Date)?.toLocalDate()
    LocalTime::class -> (v as? Time)?.toLocalTime()
    LocalDateTime::class -> (v as? Timestamp)?.toLocalDateTime()
    Decimal::class -> v?.toString()?.d
    else -> if (target?.annotation<JvmInline>() != null || target == Decimal::class) target.primaryConstructor!!.call(v)
    else if (v is String && target != null && target != String::class) Converter.from(v, target) else v
  }
}
