package klite.sample.klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.jdbc.*
import klite.sample.DBTest
import klite.sleep
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

class PostgresExtensionsTest: DBTest() {
  @Test fun `postgres advisory locks`() {
    val ok = "Hello"
    expect(db.unlock(ok)).toEqual(false)
    expect(db.tryLock(ok)).toEqual(true)
    expect(db.tryLock(ok)).toEqual(true)
    expect(db.unlock(ok)).toEqual(true)
    expect(db.unlock(ok)).toEqual(true)
    expect(db.unlock(ok)).toEqual(false)
  }

  @Test fun `postgres notify and listen`() = runTest {
    val channel = Channel<String>(UNLIMITED)
    val reader = thread {
      db.consumeNotifications(setOf("hello"), 500.milliseconds) {
        channel.trySend(it.parameter)
      }
    }
    sleep(100.milliseconds)
    db.notify("hello")
    db.notify("hello", "world")
    Transaction.current()!!.commit()
    expect(channel.receive()).toEqual("")
    expect(channel.receive()).toEqual("world")
    reader.interrupt()
    reader.join()
  }
}
