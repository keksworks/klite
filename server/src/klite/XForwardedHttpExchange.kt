package klite

/** Use this class if you are 100% your application will be only accessible behind a trusted reverse proxy */
open class XForwardedHttpExchange(original: OriginalHttpExchange, config: RouterConfig, sessionStore: SessionStore?, requestId: String):
  HttpExchange(original, config, sessionStore, requestId) {
  companion object {
    private val forwardedIPIndexFromEnd = Config.optional("XFORWARDED_IP_FROM_END", "1").toInt()
  }
  override val remoteAddress get() = header("X-Forwarded-For")?.split(", ")?.let { it.getOrNull(it.size - forwardedIPIndexFromEnd) } ?: super.remoteAddress
  override val host get() = header("X-Forwarded-Host") ?: super.host
  override val protocol get() = header("X-Forwarded-Proto") ?: "http"
  override val isSecure get() = protocol == "https"
}
