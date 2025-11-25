package klite

import com.sun.net.httpserver.HttpServer
import klite.RequestMethod.GET
import klite.StatusCode.Companion.NoContent
import klite.StatusCode.Companion.NotFound
import klite.StatusCode.Companion.OK
import kotlinx.coroutines.*
import java.lang.Runtime.getRuntime
import java.lang.Thread.currentThread
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

val Config.port: Int get() = optional("PORT", "8080").toInt()
val Config.numWorkers: Int get() = optional("NUM_WORKERS")?.toInt() ?: getRuntime().availableProcessors()

class Server(
  val listen: InetSocketAddress = InetSocketAddress(Config.port),
  val workerPool: ExecutorService = Executors.newWorkStealingPool(Config.numWorkers),
  registry: MutableRegistry = DependencyInjectingRegistry().apply {
    register<RequestLogger>()
    register<TextBody>()
    register<FormUrlEncodedParser>()
    register<FormDataParser>()
  },
  val requestIdGenerator: RequestIdGenerator = registry.require(),
  val errors: ErrorHandler = registry.require(),
  decorators: List<Decorator> = registry.requireAllDecorators(),
  val sessionStore: SessionStore? = registry.optional(),
  val notFoundHandler: Handler = { ErrorResponse(NotFound, path) },
  pathParamRegexer: PathParamRegexer = registry.require(),
  private val httpExchangeCreator: KFunction<HttpExchange> = HttpExchange::class.primaryConstructor!!,
): RouterConfig(registry, pathParamRegexer, decorators, registry.requireAll(), registry.requireAll()) {
  private val requestScope = CoroutineScope(SupervisorJob() + workerPool.asCoroutineDispatcher())
  private val log = logger()

  init {
    currentThread().name += "/" + requestIdGenerator.prefix
    val kliteVersion = javaClass.`package`.implementationVersion ?: "dev"
    log.info("klite ${kliteVersion}, config " + Config.active)
  }

  private val http = HttpServer.create().apply { executor = workerPool }
  private val numActiveRequests = AtomicInteger()

  val address: InetSocketAddress get() = http.address ?: error("Server not started")

  fun start(gracefulStopDelaySec: Int = 3, socketBacklog: Int = 0) {
    http.bind(listen, socketBacklog)
    log.info("Listening on http://${if (address.address.isAnyLocalAddress) "localhost" else address.hostString}:${address.port}")
    http.start()
    if (gracefulStopDelaySec >= 0) getRuntime().addShutdownHook(thread(start = false) { stop(gracefulStopDelaySec) })
  }

  private val onStopHandlers = mutableListOf<Runnable>()
  fun onStop(handler: Runnable) { onStopHandlers += handler }

  fun stop(delaySec: Int = 1) {
    log.info("Stopping gracefully")
    http.stop(if (numActiveRequests.get() == 0) 0 else delaySec)
    onStopHandlers.reversed().forEach { it.run() }
  }

  /** Adds a new router context. When handing a request, the longest matching router context is chosen */
  fun context(prefix: String, block: Router.() -> Unit = {}) =
    Router(prefix, registry, pathParamRegexer, decorators, renderers, parsers).also { router ->
      val notFoundRoute = NotFoundRoute(prefix, notFoundHandler)
      addContext(prefix, router) {
        val r = router.route(this)
        runHandler(this, r?.first ?: notFoundRoute, r?.second ?: PathParams.EMPTY)
      }
      router.block()
      notFoundRoute.decoratedHandler = router.decorators.wrap(notFoundHandler)
    }

  fun assets(prefix: String, handler: AssetsHandler) {
    val route = Route(GET, prefix.toRegex(), handler::class.annotations, handler).apply { decoratedHandler = decorators.wrap(handler) }
    addContext(prefix, this, Dispatchers.IO) { runHandler(this, route, PathParams.EMPTY) }
  }

  private fun addContext(prefix: String, config: RouterConfig, extraCoroutineContext: CoroutineContext = EmptyCoroutineContext, handler: Handler) {
    http.createContext(prefix) { ex ->
      val requestId = requestIdGenerator(ex.requestHeaders)
      requestScope.launch(ThreadNameContext(requestId) + extraCoroutineContext) {
        httpExchangeCreator.call(ex, config, sessionStore, requestId).handler()
      }
    }
  }

  private suspend fun runHandler(exchange: HttpExchange, route: Route, pathParams: PathParams) {
    try {
      numActiveRequests.incrementAndGet()
      exchange.route = route
      exchange.pathParams = pathParams
      val result = route.decoratedHandler.invoke(exchange)
      if (!exchange.isResponseStarted) exchange.handle(result)
      else if (result != null && result != Unit) log.warn("Response already started, cannot render $result")
    } catch (ignore: BodyNotAllowedException) {
    } catch (e: Throwable) {
      handleError(exchange, e)
    } finally {
      exchange.close()
      numActiveRequests.decrementAndGet()
    }
  }

  private fun HttpExchange.handle(result: Any?) = when (result) {
    Unit -> send(NoContent)
    is StatusCode -> send(result)
    is ErrorResponse -> render(result.statusCode, result)
    else -> render(OK, result)
  }

  private fun handleError(exchange: HttpExchange, e: Throwable) = try {
    errors.handle(exchange, e)
  } catch (ignore: BodyNotAllowedException) {
  } catch (e2: Throwable) {
    log.error("While handling $e", e2)
  }
}

interface Extension {
  fun install(server: Server) {}
  fun install(config: RouterConfig) {
    if (config is Server) install(config)
    else error("${this::class} needs to be used at the Server level, move it out of context call")
  }
}
