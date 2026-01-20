package klite

import klite.StatusCode.Companion.BadRequest
import klite.StatusCode.Companion.InternalServerError
import klite.StatusCode.Companion.NotFound
import klite.StatusCode.Companion.UnprocessableEntity
import java.io.FileNotFoundException
import java.nio.file.NoSuchFileException
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses

open class BusinessException(messageKey: String, cause: Throwable? = null): Exception(messageKey, cause)

typealias ThrowableHandler<T> = HttpExchange.(e: T) -> ErrorResponse?

open class ErrorHandler {
  private val log = logger()
  private val handlers = mutableMapOf<KClass<out Throwable>, ThrowableHandler<Throwable>>()
  private val statusCodes = mutableMapOf<KClass<out Throwable>, StatusCode>(
    NoSuchFileException::class to NotFound,
    FileNotFoundException::class to NotFound,
    IllegalArgumentException::class to BadRequest,
    IllegalStateException::class to BadRequest,
    BusinessException::class to UnprocessableEntity
  )

  init {
    on<NoSuchElementException> { e -> log.error(e); ErrorResponse(NotFound, e.message?.takeIf { "is empty" !in it }) }
    on<NullPointerException> { e ->
      if (e.message?.startsWith("Parameter specified as non-null is null") == true)
        ErrorResponse(BadRequest, e.message!!.substring(e.message!!.indexOf(", parameter ") + 12) + " is required")
      else null
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <T: Throwable> on(e: KClass<out T>, handler: ThrowableHandler<T>) { handlers[e] = handler as ThrowableHandler<Throwable> }
  inline fun <reified T: Throwable> on(noinline handler: ThrowableHandler<T>) = on(T::class, handler)

  fun on(e: KClass<out Throwable>, statusCode: StatusCode) { statusCodes[e] = statusCode }
  inline fun <reified T: Throwable> on(statusCode: StatusCode) { on(T::class, statusCode) }

  private val ioErrorsToSkip = setOf("Broken pipe", "Stream is closed", "Connection reset", "Connection reset by peer", "Operation timed out")

  fun handle(exchange: HttpExchange, e: Throwable) {
    exchange.failure = e
    if (!exchange.isResponseStarted) toResponse(exchange, e).let {
      if (it.statusCode.bodyAllowed) exchange.render(it.statusCode, it) else exchange.send(it.statusCode)
    } else if (e.message == null || e.message !in ioErrorsToSkip)
      log.error("Error after headers sent", e)
  }

  open fun toResponse(exchange: HttpExchange, e: Throwable): ErrorResponse {
    if (e is RedirectException) exchange.header("Location", e.location)
    if (e is StatusCodeException) return ErrorResponse(e.statusCode, e.message)

    // TODO: look for subclasses
    handlers.find(e::class)?.let { handler ->
      exchange.handler(e)?.let { return it }
    }
    statusCodes.find(e::class)?.let {
      log.error(e)
      return ErrorResponse(it, e.message)
    }
    return unhandled(e)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> Map<KClass<out Throwable>, T>.find(cls: KClass<out Throwable>): T? =
    get(cls) ?: cls.superclasses.firstNotNullOfOrNull { find(it as KClass<out Throwable>) }

  open fun unhandled(e: Throwable): ErrorResponse {
    log.error("Unhandled exception", e)
    return ErrorResponse(InternalServerError, e.message.takeUnless { Config.isProd })
  }
}

data class ErrorResponse(val statusCode: StatusCode, val message: String?) {
  val reason: String = statusCode.reason ?: ""
  override fun toString() = "${statusCode.value} $reason\n${message ?: ""}"
}
