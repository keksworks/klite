package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import klite.StatusCode.Companion.BadRequest
import klite.StatusCode.Companion.Forbidden
import klite.StatusCode.Companion.InternalServerError
import klite.StatusCode.Companion.NotFound
import org.junit.jupiter.api.Test
import java.io.IOException

class ErrorHandlerTest {
  val exchange = mockk<HttpExchange>(relaxUnitFun = true) {
    every { path } returns "/path"
  }
  val errorHandler = ErrorHandler()

  @Test fun `status code exception`() {
    expect(errorHandler.toResponse(exchange, ForbiddenException("Some message")))
      .toEqual(ErrorResponse(Forbidden, "Some message", instance = "/path"))
  }

  @Test fun `required parameter exception`() {
    expect(errorHandler.toResponse(exchange, NullPointerException("Parameter specified as non-null is null, parameter hello")))
      .toEqual(ErrorResponse(BadRequest, "hello is required"))
  }

  @Test fun `subclassed exception`() {
    expect(errorHandler.toResponse(exchange, SubIllegalArgumentException("Something happened")))
      .toEqual(ErrorResponse(BadRequest, "Something happened", instance = "/path"))
  }

  @Test fun `no such element`() {
    expect(errorHandler.toResponse(exchange, NoSuchElementException("List is empty."))).toEqual(ErrorResponse(NotFound, null))
    expect(errorHandler.toResponse(exchange, NoSuchElementException("Custom message"))).toEqual(ErrorResponse(NotFound, "Custom message"))
  }

  @Test fun unhandled() {
    expect(errorHandler.toResponse(exchange, Exception("Kaboom"))).toEqual(ErrorResponse(InternalServerError, "Kaboom"))
  }

  @Test fun `handler returning null proceeds further`() {
    errorHandler.on<NullPointerException> { null }
    expect(errorHandler.toResponse(exchange, NullPointerException())).toEqual(ErrorResponse(InternalServerError, null))
  }

  @Test fun `handle broken pipe - client closed connection`() {
    every { exchange.isResponseStarted } returns true
    val e = IOException("Broken pipe")
    errorHandler.handle(exchange, e)
    verify { exchange.failure = e }
    verify(exactly = 0) { exchange.render(any(), any()) }
  }

  @Test fun `render error`() {
    every { exchange.isResponseStarted } returns false
    val e = IOException("Any exception")
    errorHandler.handle(exchange, e)
    verify {
      exchange.failure = e
      exchange.render(InternalServerError, ErrorResponse(InternalServerError, "Any exception"))
    }
  }

  class SubIllegalArgumentException(message: String): IllegalArgumentException(message)
}
