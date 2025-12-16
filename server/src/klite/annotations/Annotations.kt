package klite.annotations

import klite.*
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import kotlin.annotation.AnnotationTarget.*
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

@Target(CLASS) annotation class Path(val value: String)
@Target(FUNCTION) annotation class GET(val value: String = "")
@Target(FUNCTION) annotation class POST(val value: String = "")
@Target(FUNCTION) annotation class PUT(val value: String = "")
@Target(FUNCTION) annotation class PATCH(val value: String = "")
@Target(FUNCTION) annotation class DELETE(val value: String = "")
@Target(FUNCTION) annotation class OPTIONS(val value: String = "")

@Target(VALUE_PARAMETER) annotation class PathParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class QueryParam(val value: String = "")
/** Inside of body param, the body itself is unannotated */
@Target(VALUE_PARAMETER) annotation class BodyParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class HeaderParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class CookieParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class SessionParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class AttrParam(val value: String = "")

/**
 * Adds all annotated methods as routes, sorted by path (matching more specific paths first).
 * Routes can also implement Before/After interfaces.
 *
 * Use the @XXXParam annotations to bind specific types of params.
 * Non-annotated binding of well known classes is possible, like [HttpExchange] and [Session].
 * Non-annotated custom class is interpreted as the whole POST/PUT body, e.g. a data class deserialized from json.
 */
@Suppress("NAME_SHADOWING")
fun Router.annotated(path: String = "", routes: Any, annotations: List<Annotation> = emptyList()) {
  val cls = routes::class
  val path = path + (cls.annotation<Path>()?.value ?: "")
  val classDecorators = mutableListOf<Decorator>()
  if (routes is Before) classDecorators += routes.toDecorator()
  if (routes is After) classDecorators += routes.toDecorator()
  cls.functions.asSequence().mapNotNull { f ->
    val a = f.kliteAnnotation ?: return@mapNotNull null
    val method = RequestMethod.valueOf(a.annotationClass.simpleName!!)
    val subPath = a.annotationClass.members.first().call(a) as String
    subPath to Route(method, pathParamRegexer.from(path + subPath), annotations + f.annotations + cls.annotations, FunHandler(routes, f)).decorateWith(classDecorators)
  }.sortedBy { it.first.replace(':', '~') }.forEach { add(it.second) }
}

fun Router.annotated(routes: Any) = annotated("", routes)
inline fun <reified T: Any> Router.annotated(path: String = "", annotations: List<Annotation> = emptyList()) = annotated(path, require<T>(), annotations)

private val packageName = GET::class.java.packageName
private val KAnnotatedElement.kliteAnnotation get() = annotations.filter { it.annotationClass.java.packageName == packageName }
  .let { if (it.size > 1) error("$this cannot have multiple klite annotations: $it") else it.firstOrNull() }

class FunHandler(val instance: Any, val f: KFunction<*>): Handler {
  val params = f.parameters.map(::Param)
  override suspend fun invoke(e: HttpExchange): Any? = try {
    val args = params.associate { p -> p.p to p.valueFrom(e, instance) }.filter { !it.key.isOptional || it.value != null }
    f.callSuspendBy(args)
  } catch (e: InvocationTargetException) {
    throw e.targetException
  }
}

class Param(val p: KParameter) {
  val cls = p.type.classifier as KClass<*>
  val source: Annotation? = p.kliteAnnotation
  val name: String = source?.value ?: p.name ?: ""

  fun valueFrom(e: HttpExchange, instance: Any) = try {
    if (p.kind == INSTANCE) instance
    else if (cls == HttpExchange::class) e
    else if (cls == Session::class) e.session
    else if (cls == InputStream::class) e.requestStream
    else {
      when (source) {
        is PathParam -> e.path(name)?.toType()
        is QueryParam ->
          if (p.type.jvmErasure.isSubclassOf(Iterable::class)) e.queryList(name)
            .map { Converter.from<Any>(it, p.type.arguments[0].type!!) }.takeIf { it.isNotEmpty() || !p.type.isMarkedNullable }
          else (e.query(name)).let { if (e.isValueLessQueryParam(it)) true else it?.toType()
        }
        is HeaderParam -> e.header(name)?.toType()
        is CookieParam -> e.cookie(name)?.toType()
        is SessionParam -> e.session[name]?.toType()
        is AttrParam -> e.attr(name)
        is BodyParam -> e.body<Any?>(name)?.let { if (it is String) it.trimToNull()?.toType() else it }
        else -> e.body(p.type)
      }
    }
  } catch (e: Exception) {
    if (e is IllegalArgumentException || e.message?.contains(name) == true) throw e
    throw IllegalArgumentException("Cannot get $name: ${e.message}", e)
  }

  private fun HttpExchange.isValueLessQueryParam(v: String?) =
    v == null && p.type.classifier == Boolean::class && name in queryParams

  private val Annotation.value: String? get() = (javaClass.getMethod("value").invoke(this) as String).takeIf { it.isNotEmpty() }
  private fun String.toType() = Converter.from<Any>(this, p.type)
}

inline fun <reified T: Annotation> KClass<*>.annotation(): T? = java.getAnnotation(T::class.java)
inline fun <reified T: Annotation> KFunction<*>.annotation(): T? = javaMethod!!.getAnnotation(T::class.java)
