package klite

open class NoStackTraceException(message: String? = null, cause: Throwable? = null): Exception(message, cause) {
  override fun fillInStackTrace(): Throwable = if (Config.isDev) super.fillInStackTrace() else this
}

open class StatusCodeException(val statusCode: StatusCode, content: String? = null, cause: Throwable? = null): NoStackTraceException(content, cause)

class NotFoundException(content: String? = null, cause: Throwable? = null): StatusCodeException(StatusCode.NotFound, content, cause)
class NotModifiedException: StatusCodeException(StatusCode.NotModified)
class BadRequestException(content: String? = null, cause: Throwable? = null): StatusCodeException(StatusCode.BadRequest, content, cause)
class ForbiddenException(content: String? = null, cause: Throwable? = null): StatusCodeException(StatusCode.Forbidden, content, cause)
class UnauthorizedException(content: String? = null, cause: Throwable? = null): StatusCodeException(StatusCode.Unauthorized, content, cause)
class UnsupportedMediaTypeException(contentType: String?): StatusCodeException(StatusCode.UnsupportedMediaType, "Unsupported Content-Type: $contentType")
class NotAcceptableException(contentType: String?): StatusCodeException(StatusCode.NotAcceptable, "Unsupported Accept: $contentType")
class RedirectException(val location: String, statusCode: StatusCode = StatusCode.Found): StatusCodeException(statusCode)

class BodyNotAllowedException: NoStackTraceException()
