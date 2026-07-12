package klite.jobs

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import klite.RequestIdGenerator
import klite.Server
import klite.jdbc.Transaction
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JobRunnerTest {
  val db = mockk<DataSource>()
  val executor = mockk<ScheduledExecutorService>(relaxed = true)
  val job = MyJob()
  val runner = JobRunner(db, RequestIdGenerator(), executor)

  class MyJob: Job {
    override val allowParallelRun = true
    override fun run() {
      expect(Transaction.current()).notToEqualNull()
    }
  }

  @Test fun `adds shutdown handler`() {
    val server = mockk<Server>(relaxed = true)
    runner.install(server)
    verify { server.onStop(any()) }
  }

  @Test fun runInTransaction() {
    runner.runInTransaction(job)
  }

  @Test fun schedule() {
    runner.schedule(job, 20.seconds, 10.seconds)
    verify { executor.scheduleAtFixedRate(any(), 10000, 20000, MILLISECONDS) }
  }

  @Test fun `scheduleDaily with delay`() {
    runner.scheduleDaily(job, delay = 10.minutes)
    verify { executor.scheduleAtFixedRate(any(), 10 * 60 * 1000, 86400000, MILLISECONDS) }
  }

  @Test fun `scheduleDaily at time`() {
    runner.scheduleDaily(job, LocalTime.of(6, 30), LocalTime.of(6, 45))
    verify(exactly = 2) { executor.scheduleAtFixedRate(any(), match { it > 0 }, 86400000, MILLISECONDS) }
  }

  @Test fun `scheduleMonthly today`() {
    val job = spyk(job)
    runner.scheduleMonthly(job, LocalDate.now().dayOfMonth, LocalTime.of(6, 30))
    val dailyRunner = slot<Runnable>()
    verify { executor.scheduleAtFixedRate(capture(dailyRunner), match { it > 0 }, 86400000, MILLISECONDS) }
    dailyRunner.captured.run()
    verify { job.run() }
  }

  @Test fun `scheduleMonthly not today`() {
    val job = spyk(job)
    runner.scheduleMonthly(job, LocalDate.now().dayOfMonth + 1, LocalTime.of(6, 30))
    val dailyRunner = slot<Runnable>()
    verify { executor.scheduleAtFixedRate(capture(dailyRunner), match { it > 0 }, 86400000, MILLISECONDS) }
    dailyRunner.captured.run()
    verify(exactly = 0) { job.run() }
  }
}
