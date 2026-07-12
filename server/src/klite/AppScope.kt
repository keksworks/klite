package klite

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// TODO
object AppScope: CoroutineScope {
  private val exceptionHandler = CoroutineExceptionHandler { _, e -> logger().error("Async operation failed", e) }
  override val coroutineContext get() = NonCancellable + exceptionHandler + ThreadNameContext(Thread.currentThread().name + "+async")
}

class ThreadNameContext(private val requestId: String): ThreadContextElement<String?>, AbstractCoroutineContextElement(Key) {
  companion object Key: CoroutineContext.Key<ThreadNameContext>
  override fun updateThreadContext(context: CoroutineContext) = Thread.currentThread().also { it.name = requestId }.name
  override fun restoreThreadContext(context: CoroutineContext, oldState: String?) { Thread.currentThread().name = oldState }
}
