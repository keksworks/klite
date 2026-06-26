package klite.slf4j

import kotlinx.coroutines.ThreadContextElement
import org.slf4j.MDC
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class MDCContext(private val map: Map<String, String?>): ThreadContextElement<Map<String, String?>?>, AbstractCoroutineContextElement(Key) {
  companion object Key: CoroutineContext.Key<MDCContext>

  override fun updateThreadContext(context: CoroutineContext): Map<String, String?>? {
    val oldState = MDC.getCopyOfContextMap()
    MDC.setContextMap(map)
    return oldState
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String?>?) {
    if (oldState == null) MDC.clear() else MDC.setContextMap(oldState)
  }
}
