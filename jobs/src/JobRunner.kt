package klite.jobs

import klite.*
import klite.jdbc.NoTransaction
import klite.jdbc.Transaction
import klite.jdbc.tryLock
import klite.jdbc.unlock
import kotlinx.coroutines.Runnable
import java.lang.Thread.currentThread
import java.time.Duration.between
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.reflect.full.hasAnnotation
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

interface Job: Runnable {
  override fun run()
  val name get() = this::class.simpleName!!
  val allowParallelRun get() = false
  val noTransaction get() = this::class.hasAnnotation<NoTransaction>() || this::run.hasAnnotation<NoTransaction>()
}

class NamedJob(override val name: String, override val allowParallelRun: Boolean, private val job: () -> Unit): Job {
  override fun run() = job()
}

open class JobRunner(
  private val db: DataSource,
  private val requestIdGenerator: RequestIdGenerator,
  val workerPool: ScheduledExecutorService = Executors.newScheduledThreadPool(Config.optional("JOB_WORKERS", "3").toInt())
): Extension {
  private val log = logger()
  private val seq = AtomicLong()

  init {
    (workerPool as? ThreadPoolExecutor)?.let {
      Metrics.register("jobs") {
        mapOf("active" to it.activeCount, "size" to it.poolSize, "max" to it.corePoolSize, "runs" to seq.get())
      }
    }
  }

  override fun install(server: Server) {
    server.onStop(::gracefulStop)
  }

  internal fun runInTransaction(job: Job) {
    val thread = currentThread()
    val prevName = thread.name
    thread.name = "${requestIdGenerator.prefix}/${job.name}#${seq.incrementAndGet()}"
    val tx = if (job.noTransaction) null else Transaction()
    var commit = true
    try {
      if (!job.allowParallelRun && !db.tryLock(job.name)) return log.info("${job.name} locked, skipping")
      try {
        log.info("${job.name} started")
        tx?.attachToThread()
        run(job)
      } finally {
        if (!job.allowParallelRun) db.unlock(job.name)
      }
    } catch (e: Exception) {
      commit = false
      log.error("${job.name} failed", e)
    } finally {
      tx?.close(commit)
      tx?.detachFromThread()
      thread.name = prevName
    }
  }

  protected open fun run(job: Job) = job.run()

  open fun runOnce(job: Job) = workerPool.submit { runInTransaction(job) }

  @Deprecated("Use version with Duration parameters instead", ReplaceWith("schedule(job, period.seconds, delay.seconds)"))
  open fun schedule(job: Job, delay: Long, period: Long, unit: TimeUnit) = schedule(job, unit.toMillis(period).milliseconds, unit.toMillis(delay).milliseconds)

  open fun schedule(job: Job, period: Duration, delay: Duration = period) {
    val startAt = LocalDateTime.now().plus(delay.toJavaDuration()).truncatedTo(ChronoUnit.SECONDS)
    log.info("${job.name} will start at ${startAt.toString().replace("T", " ")} and run every $period")
    workerPool.scheduleAtFixedRate({ runInTransaction(job) }, delay.inWholeMilliseconds, period.inWholeMilliseconds, MILLISECONDS)
  }

  fun scheduleDaily(job: Job, delay: Duration = (Math.random() * 10).toLong().minutes) =
    schedule(job, 24.hours, delay)

  fun scheduleDaily(job: Job, vararg at: LocalTime) {
    val now = LocalDateTime.now()
    for (time in at) {
      val todayAt = time.atDate(now.toLocalDate())
      val runAt = if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
      scheduleDaily(job, between(now, runAt).toKotlinDuration())
    }
  }

  fun scheduleMonthly(job: Job, dayOfMonth: Int, vararg at: LocalTime) = scheduleDaily(NamedJob(job.name, job.allowParallelRun) {
    if (LocalDate.now().dayOfMonth == dayOfMonth) job.run()
  }, at = at)

  open fun gracefulStop() {
    workerPool.shutdownNow()
    workerPool.awaitTermination(10, SECONDS)
  }
}
