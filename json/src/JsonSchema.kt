package klite.json

import klite.Converter
import klite.mapOfNotNull
import klite.publicProperties
import java.net.URI
import java.net.URL
import java.time.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

fun KType.toJsonSchema(response: Boolean = false): Map<String, Any?>? {
  val cls = classifier as? KClass<*> ?: return null
  if (cls.qualifiedName?.startsWith("kotlin.Function") == true) return null // workaround for https://youtrack.jetbrains.com/issue/KT-83608
  return when {
    cls == Nothing::class -> mapOf("type" to "null")
    cls == Boolean::class -> mapOf("type" to "boolean")
    cls == Int::class -> mapOf("type" to "integer", "format" to "int32")
    cls == Long::class -> mapOf("type" to "integer", "format" to "int64")
    cls == Float::class -> mapOf("type" to "number", "format" to "float")
    cls == Double::class -> mapOf("type" to "number", "format" to "double")
    cls.isSubclassOf(Number::class) -> mapOf("type" to "number")
    cls.isSubclassOf(Enum::class) -> mapOf("type" to "string", "enum" to cls.java.enumConstants.toList())
    cls.isSubclassOf(Array::class) || cls.isSubclassOf(Iterable::class) -> mapOf("type" to "array", "items" to arguments.firstOrNull()?.type?.toJsonSchema(response))
    cls.isSubclassOf(CharSequence::class) || Converter.supports(cls) && cls != Any::class -> mapOfNotNull("type" to "string", "format" to when (cls) {
      LocalDate::class, Date::class -> "date"
      LocalTime::class -> "time"
      Instant::class, LocalDateTime::class -> "date-time"
      Period::class, Duration::class -> "duration"
      URI::class, URL::class -> "uri"
      UUID::class -> "uuid"
      else -> null
    })
    else -> mapOfNotNull("type" to "object",
      "properties" to cls.publicProperties.mapValues { it.value.returnType.toJsonSchema(response) }.takeIf { it.isNotEmpty() },
      "required" to cls.publicProperties.values.filter { p ->
        !p.returnType.isMarkedNullable && (response || cls.primaryConstructor?.parameters?.find { it.name == p.name }?.isOptional != true)
      }.map { it.name }.toSet().takeIf { it.isNotEmpty() })
  }
}
