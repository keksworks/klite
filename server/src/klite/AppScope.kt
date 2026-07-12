package klite

import java.lang.Thread.currentThread
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

object AppScope {
  private val executor = Executors.newVirtualThreadPerTaskExecutor()

  fun <T> async(task: Callable<T>): Future<T> {
    val threadName = currentThread().name
    return executor.submit(Callable {
      currentThread().name = "$threadName+async"
      task.call()
    })
  }
}
