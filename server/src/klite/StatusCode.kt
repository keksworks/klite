package klite

@JvmInline value class StatusCode(val value: Int) {
  companion object {
    val OK = StatusCode(200)
    val Created = StatusCode(201)
    val Accepted = StatusCode(202)
    val NoContent = StatusCode(204)
    val Moved = StatusCode(301)
    val Found = StatusCode(302)
    val SeeOther = StatusCode(303)
    val NotModified = StatusCode(304)
    val TemporaryRedirect = StatusCode(307)
    val PermanentRedirect = StatusCode(308)
    val BadRequest = StatusCode(400)
    val Unauthorized = StatusCode(401)
    val Forbidden = StatusCode(403)
    val NotFound = StatusCode(404)
    val MethodNotAllowed = StatusCode(405)
    val NotAcceptable = StatusCode(406)
    val Timeout = StatusCode(408)
    val Conflict = StatusCode(409)
    val Gone = StatusCode(410)
    val PreconditionFailed = StatusCode(412)
    val PayloadTooLarge = StatusCode(413)
    val UnsupportedMediaType = StatusCode(415)
    val UnprocessableEntity = StatusCode(422)
    val Locked = StatusCode(423)
    val TooEarly = StatusCode(425)
    val TooManyRequests = StatusCode(429)
    val InternalServerError = StatusCode(500)
    val NotImplemented = StatusCode(501)
    val BadGateway = StatusCode(502)
    val ServiceUnavailable = StatusCode(503)
    val GatewayTimeout = StatusCode(504)
    val InsufficientStorage = StatusCode(507)

    private val reasons: Map<StatusCode, String> by lazy {
      publicProperties.associate {
        it.get(this) as StatusCode to it.name.replace("[A-Z]".toRegex(), " $0").trim()
      } + (OK to "OK")
    }
  }

  val reason get() = reasons[this]
  val bodyAllowed get() = this != NoContent && this != NotModified
  val isError get() = value >= BadRequest.value
  override fun toString() = value.toString()
}
