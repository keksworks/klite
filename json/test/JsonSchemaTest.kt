package klite.json

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.nodes.at
import klite.nodes.text
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType

class JsonSchemaTest {
  @Test fun `primitive types`() {
    expect(Boolean::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "boolean"))
    expect(Int::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "integer", "format" to "int32"))
    expect(Long::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "integer", "format" to "int64"))
    expect(Float::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "number", "format" to "float"))
    expect(Double::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "number", "format" to "double"))
    expect(BigDecimal::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "number"))
  }

  @Test fun `string formats`() {
    expect(String::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "string"))
    expect(java.time.LocalDate::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "string", "format" to "date"))
    expect(java.time.Instant::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "string", "format" to "date-time"))
    expect(java.util.UUID::class.starProjectedType.toJsonSchema()).toEqual(mapOf("type" to "string", "format" to "uuid"))
  }

  @Test fun `nested object`() {
    val schema = Nested::class.createType().toJsonSchema()
    expect(schema).toEqual(mapOf(
      "type" to "object",
      "properties" to mapOf(
        "x" to mapOf("type" to "number"),
        "y" to mapOf("type" to "integer", "format" to "int32")
      )
    ))
  }

  @Test fun `object with required and optional fields`() {
    val schema = Hello::class.createType().toJsonSchema()!!
    val props = schema["properties"] as Map<*, *>
    expect(props.keys).toEqual(setOf("array", "computed", "date", "hello", "id", "ignore", "instant", "isBoolean", "map", "nested", "nullable", "readOnly"))
    val required = schema["required"] as Set<*>
    expect(required).toEqual(setOf("computed", "hello", "id", "date", "instant", "nested"))
  }

  @Test fun `object with required fields in response mode`() {
    val schema = Hello::class.createType().toJsonSchema(response = true)!!
    val required = schema["required"] as Set<*>
    expect(required).toEqual(setOf("array", "computed", "date", "hello", "id", "ignore", "instant", "isBoolean", "map", "nested", "readOnly"))
  }

  @Test fun `list type`() {
    val schema = List::class.createType(listOf(KTypeProjection.invariant(Nested::class.createType()))).toJsonSchema()
    expect(schema).toEqual(mapOf(
      "type" to "array",
      "items" to mapOf(
        "type" to "object",
        "properties" to mapOf("x" to mapOf("type" to "number"), "y" to mapOf("type" to "integer", "format" to "int32"))
      )
    ))
  }

  @Test fun `nullable field`() {
    val schema = Nullable::class.createType().toJsonSchema()!!
    expect(schema.at("properties").at("x").text("type")).toEqual("string")
  }
}
