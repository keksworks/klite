package klite

import java.util.concurrent.atomic.AtomicLong

open class RequestIdGenerator(
  val prefix: String = (0xFFFF * Math.random()).toInt().toString(16)
) {
  protected val counter = AtomicLong()
  init {
    Metrics.register("instanceId") { prefix }
    Metrics.register("requestsTotal") { counter.get() }
  }

  open operator fun invoke(headers: Headers) = "$prefix-${counter.incrementAndGet()}"
}

open class XRequestIdGenerator: RequestIdGenerator() {
  override fun invoke(headers: Headers) = super.invoke(headers) + (headers.getFirst("X-Request-Id")?.let { "/$it" } ?: "")
}
