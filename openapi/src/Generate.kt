package klite.openapi

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.media.Schema.*
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.tags.Tag
import klite.*
import klite.StatusCode.Companion.NoContent
import klite.StatusCode.Companion.OK
import klite.annotations.*
import klite.json.toJsonSchema
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.hasAnnotation

context(e: HttpExchange)
internal fun Router.generateOpenAPI() = mapOf(
  "openapi" to "3.1.0",
  "info" to e.route.findAnnotation<Info>()?.toNonEmptyValues(),
  "servers" to listOf(mapOf("url" to e.fullUrl(prefix))),
  "tags" to toTags(routes),
  "components" to mapOfNotNull(
    "securitySchemes" to e.route.findAnnotations<SecurityScheme>().associate { s ->
      s.name to s.toNonEmptyValues { it.name != "paramName" }.let { it + ("name" to s.paramName) }
    }.takeIf { it.isNotEmpty() }
  ).takeIf { it.isNotEmpty() },
  "paths" to routes.filter { !it.hasAnnotation<Hidden>() }.groupBy { pathParamRegexer.toOpenApi(it.path) }.mapValues { (_, routes) ->
    routes.associate(::toOperation)
  },
) + (e.route.findAnnotation<OpenAPIDefinition>()?.let {
  it.toNonEmptyValues() + ("security" to it.security.toList().toSecurity())
} ?: emptyMap())

internal fun toTags(routes: List<Route>) = routes.asSequence()
  .map { it.handler }
  .filterIsInstance<FunHandler>()
  .flatMap { it.instance::class.findAnnotations<Tag>().map { it.toNonEmptyValues() }.ifEmpty { [mapOf("name" to it.instance::class.simpleName)] } }
  .toSet()

internal fun toOperation(route: Route): Pair<String, Any> {
  val op = route.findAnnotation<Operation>()
  val funHandler = route.handler as? FunHandler
  return (op?.method?.trimToNull() ?: route.method.name).lowercase() to mapOf(
    "operationId" to funHandler?.let { it.instance::class.simpleName + "." + it.f.name },
    "tags" to listOfNotNull(funHandler?.let { it.instance::class.annotation<Tag>()?.name ?: it.instance::class.simpleName }),
    "parameters" to funHandler?.let {
      it.params.map { p -> toParameter(p, op) }.filter { it["in"] != null }
    },
    "requestBody" to toRequestBody(route, route.findAnnotation<RequestBody>() ?: op?.requestBody),
    "responses" to toResponsesByCode(route, op, funHandler?.f?.returnType),
    "security" to (op?.security?.toList() ?: route.findAnnotations<SecurityRequirement>()).toSecurity()
  ) + (op?.let { it.toNonEmptyValues { it.name !in setOf("method", "requestBody", "responses") } } ?: emptyMap())
}

fun toParameter(p: Param, op: Operation? = null) = mapOf(
  "name" to p.name,
  "required" to (!p.p.isOptional && !p.p.type.isMarkedNullable),
  "in" to toParameterIn(p.source),
  "schema" to p.p.type.toJsonSchema(),
) + ((p.p.findAnnotation<Parameter>() ?: op?.parameters?.find { it.name == p.name })?.toNonEmptyValues() ?: emptyMap())

private fun toParameterIn(paramAnnotation: Annotation?) = when(paramAnnotation) {
  is HeaderParam -> ParameterIn.HEADER
  is QueryParam -> ParameterIn.QUERY
  is PathParam -> ParameterIn.PATH
  is CookieParam -> ParameterIn.COOKIE
  else -> null
}

private fun toRequestBody(route: Route, annotation: RequestBody?): Map<String, Any?>? {
  val bodyParam = (route.handler as? FunHandler)?.params?.find { it.p.kind != INSTANCE && it.source == null && it.cls.java.packageName != "klite" }?.p
  val requestBody = annotation?.toNonEmptyValues() ?: HashMap()
  if (annotation != null && annotation.content.isNotEmpty())
    requestBody["content"] = annotation.content.associate {
      val content = it.toNonEmptyValues { it.name != "mediaType" }
      if (it.schema.implementation != Void::class) content["schema"] = it.schema.implementation.createType().toJsonSchema()
      else if (it.array.schema.implementation != Void::class) content["schema"] = Array::class.createType(arguments = listOf(KTypeProjection(null, it.array.schema.implementation.createType()))).toJsonSchema()
      it.mediaType to content
    }
  if (bodyParam != null) requestBody.putIfAbsent("content", bodyParam.type.toJsonContent())
  if (requestBody.isEmpty()) return null
  requestBody.putIfAbsent("required", bodyParam == null || !bodyParam.isOptional)
  return requestBody
}

private fun toResponsesByCode(route: Route, op: Operation?, returnType: KType?): Map<StatusCode, Any?> {
  val responses = LinkedHashMap<StatusCode, Any?>()
  if (returnType?.classifier == Unit::class) responses[NoContent] = mapOf("description" to "No content")
  else if (op?.responses?.isEmpty() != false) responses[OK] = mapOfNotNull("description" to "OK", "content" to returnType?.toJsonContent(response = true))
  (route.findAnnotations<ApiResponse>() + (op?.responses ?: emptyArray())).forEach {
    responses[StatusCode(it.responseCode.toInt())] = it.toNonEmptyValues { it.name != "responseCode" }
  }
  return responses
}

private fun KType.toJsonContent(response: Boolean = false) = mapOf(MimeTypes.json to mapOf("schema" to toJsonSchema(response)))

private fun List<SecurityRequirement>.toSecurity() = map { mapOf(it.name to it.scopes.toList()) }.takeIf { it.isNotEmpty() }

internal fun Annotation.toNonEmptyValues(filter: (KProperty1<Any, *>) -> Boolean = { true }): MutableMap<String, Any?> {
  val result = HashMap<String, Any?>()
  val props = javaClass.interfaces.first().kotlin.publicProperties.values as Iterable<KProperty1<Any, *>>
  props.filter(filter).forEach { p ->
    when(val v = p.valueOf(this)) {
      "", "default", false, 0, Int.MAX_VALUE, Int.MIN_VALUE, 0.0, Void::class.java, AccessMode.AUTO, RequiredMode.AUTO, AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION -> null
      is Enum<*> -> v.takeIf { v.name != "DEFAULT" && v.name != "AUTO" }
      is Annotation -> v.toNonEmptyValues().takeIf { it.isNotEmpty() }
      is Array<*> -> v.map { (it as? Annotation)?.toNonEmptyValues() ?: it }.takeIf { it.isNotEmpty() }
      else -> v
    }?.let { result[p.name] = it }
  }
  return result
}
