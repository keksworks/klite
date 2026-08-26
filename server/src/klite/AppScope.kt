package klite

import java.lang.Thread.currentThread
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

object AppScope {
  private val executor = Executors.newVirtualThreadPerTaskExecutor()
  private val log = logger()

  fun <T> async(task: Callable<T>): Future<T> {
    val threadName = currentThread().name
    return executor.submit(Callable {
      currentThread().name = "$threadName+async"
      try {
        task.call()
      } catch (e: Exception) {
        log.error("Async task failed", e)
        throw e
      }
    })
  }
}
