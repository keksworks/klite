package klite

import klite.RequestMethod.*
import kotlinx.coroutines.Runnable
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

abstract class RouterConfig(
  val registry: MutableRegistry,
  val pathParamRegexer: PathParamRegexer,
  decorators: List<Decorator>,
  bodyRenderers: List<BodyRenderer>,
  bodyParsers: List<BodyParser>
): MutableRegistry by registry {
  val decorators = decorators.toMutableList()
  val renderers = bodyRenderers.toMutableList()
  val parsers = bodyParsers.toMutableList()

  fun decorator(decorator: Decorator) { decorators += decorator }
  inline fun <reified T: Decorator> decorator() = decorator(require<T>())

  fun before(before: Before) = decorator(before.toDecorator())
  inline fun <reified T: Before> before() = before(require<T>())

  fun after(after: After) = decorator(after.toDecorator())
  inline fun <reified T: After> after() = after(require<T>())

  // add both Extension and Runnable overloads when this is resolved: https://youtrack.jetbrains.com/issue/KT-56930
  inline fun <reified E: Any> use() = require<E>().also { use(it) }
  fun use(extension: Any) = extension.also {
    register(it)
    var used = false
    if (it is Extension) it.install(this).also { used = true }
    if (it is Runnable) it.run().also { used = true }
    if (it is BodyParser) parsers += it.also { used = true }
    if (it is BodyRenderer) renderers += it.also { used = true }
    if (!used) error("Cannot use $it, not an Extension, Runnable, BodyParser or BodyRenderer")
  }

  inline fun <reified T> useOnly() where T: BodyParser, T: BodyRenderer {
    useOnly(renderers, BodyRenderer::class, T::class)
    useOnly(parsers, BodyParser::class, T::class)
  }

  fun <T: Any> useOnly(list: MutableList<T>, type: KClass<out T>, impl: KClass<out T>) {
    if (impl.isSubclassOf(type)) {
      list.retainAll { it::class.isSubclassOf(impl) }
      if (list.isEmpty()) list += registry.require(impl)
    }
  }
}

class Router(
  val prefix: String,
  registry: MutableRegistry,
  pathParamRegexer: PathParamRegexer,
  decorators: List<Decorator>,
  renderers: List<BodyRenderer>,
  parsers: List<BodyParser>
): RouterConfig(registry, pathParamRegexer, decorators, renderers, parsers) {
  private val log = logger()
  val routes = mutableListOf<Route>() // TODO: use ExplicitBackingFields feature when it is more stable for immutable getter

  internal fun route(exchange: HttpExchange): Pair<Route, PathParams>? {
    val suffix = exchange.path.removePrefix(prefix)
    return match(exchange.method, suffix)?.let {
      it.first to PathParams(it.second.groups)
    }
  }

  private fun match(method: RequestMethod, path: String): Pair<Route, MatchResult>? {
    for (route in routes) {
      if (method == route.method || method == HEAD && route.method == GET)
        route.path.matchEntire(path)?.let { return route to it }
    }
    return null
  }

  fun add(route: Route) = route.apply {
    routes += decorateWith(decorators)
    log.info("$method $prefix$path")
  }

  fun get(path: Regex, handler: Handler) = add(Route(GET, path, handler = handler))
  fun get(path: String = "", handler: Handler) = get(pathParamRegexer.from(path), handler)

  fun post(path: Regex, handler: Handler) = add(Route(POST, path, handler = handler))
  fun post(path: String = "", handler: Handler) = post(pathParamRegexer.from(path), handler)

  fun put(path: Regex, handler: Handler) = add(Route(PUT, path, handler = handler))
  fun put(path: String = "", handler: Handler) = put(pathParamRegexer.from(path), handler)

  fun patch(path: Regex, handler: Handler) = add(Route(PATCH, path, handler = handler))
  fun patch(path: String = "", handler: Handler) = patch(pathParamRegexer.from(path), handler)

  fun delete(path: Regex, handler: Handler) = add(Route(DELETE, path, handler = handler))
  fun delete(path: String = "", handler: Handler) = delete(pathParamRegexer.from(path), handler)

  fun options(path: Regex, handler: Handler) = add(Route(OPTIONS, path, handler = handler))
  fun options(path: String = "", handler: Handler) = options(pathParamRegexer.from(path), handler)
}

enum class RequestMethod(val hasBody: Boolean = true) {
  GET(false), POST, PUT, PATCH, DELETE, OPTIONS, HEAD(false)
}

open class Route(val method: RequestMethod, val path: Regex, annotations: List<Annotation> = emptyList(), val handler: Handler): KAnnotatedElement {
  internal var decoratedHandler: Handler = handler
  override val annotations = anonymousHandlerAnnotations(handler) + annotations

  internal fun decorateWith(decorators: List<Decorator>) = this.also {
    decoratedHandler = decorators.wrap(decoratedHandler)
  }

  private fun anonymousHandlerAnnotations(handler: Handler) =
    handler.javaClass.methods.find { it.name.startsWith("invoke") && it.annotations.isNotEmpty() }?.annotations?.toList() ?: emptyList()
}

class NotFoundRoute(path: String, notFoundHandler: Handler): Route(OPTIONS, path.toRegex(), handler = notFoundHandler)

/** Converts parameterized paths like "/hello/:world/" to Regex with named parameters */
open class PathParamRegexer(private val paramConverter: Regex = "(^|/):([^/]+)".toRegex()) {
  open fun from(path: String) = paramConverter.replace(path, "$1(?<$2>[^/]+)").toRegex()
  open fun toOpenApi(path: String) = paramConverter.replace(path, "$1{$2}")
  open fun toOpenApi(path: Regex) = path.pattern.replace("\\(\\?<(.+?)>.*?\\)".toRegex(), "{$1}")
}

class PathParams(private val groups: MatchGroupCollection?): Params {
  override val entries get() = throw NotImplementedError()
  override val keys get() = throw NotImplementedError()
  override val values get() = groups?.map { it?.value } ?: emptyList()
  override val size get() = groups?.size ?: 0
  override fun isEmpty() = groups?.isEmpty() ?: true
  override fun get(key: String) = runCatching { groups?.get(key) }.getOrNull()?.value
  override fun containsKey(key: String) = get(key) != null
  override fun containsValue(value: String?) = groups?.find { it != null && it.value == value } != null

  companion object {
    val EMPTY = PathParams(null)
  }
}
