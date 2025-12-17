package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.time.Duration.Companion.milliseconds

class PooledDataSourceTest {
  val db = mockk<DataSource>(relaxed = true)
  val pool = PooledDataSource(db, maxSize = 3, timeout = 100.milliseconds)

  @Test fun unwrap() {
    expect(pool.unwrap<DataSource>()).toBeTheInstance(db)
  }

  @Test fun `retrieved connections are checked`() {
    val pooled = pool.connection
    expect(pooled).toBeAnInstanceOf<PooledDataSource.PooledConnection>()
    val conn = pooled.unwrap<Connection>()
    expect(conn).notToEqual(pooled)
    verify {
      conn.setNetworkTimeout(null, pool.queryTimeout.inWholeMilliseconds.toInt())
      conn.applicationName = Thread.currentThread().name
    }
    expect(pool.size.get()).toEqual(1)
    expect(pool.available).toBeEmpty()
    expect(pool.used.keys).toContainExactly(pooled)

    pooled.close()
    verify { conn.rollback() }
    verify(exactly = 0) { conn.close() }
    expect(pool.size.get()).toEqual(1)
    expect(pool.available).toContainExactly(pooled)
    expect(pool.used).toBeEmpty()

    every { conn.applicationName = any() } throws IOException("boom")
    val conn2 = mockk<Connection>(relaxed = true)
    every { db.connection } returns conn2
    val pooled2 = pool.connection
    expect(pooled2).notToEqual(pooled)
    expect(pool.size.get()).toEqual(1)
    expect(pool.available).toBeEmpty()
    expect(pool.used.keys).toContainExactly(pooled2)

    pool.close()
    verify { conn2.close() }
  }

  @Test fun `handle exceptions`() {
    every { db.connection } throws SQLException("connection refused")
    repeat(pool.maxSize + 1) {
      expect { pool.connection }.toThrow<SQLException>().messageToContain("connection refused")
    }
  }

  @Test fun maxSize() {
    val conns = (1..3).map { pool.connection }
    expect(pool.used.keys).toHaveSize(3)
    expect(pool.available).toHaveSize(0)

    val extra = GlobalScope.async { pool.connection }
    expect(extra.isActive).toEqual(true)
    expect(extra.isCompleted).toEqual(false)
    conns.forEach { it.close() }

    runBlocking {
      val conn = extra.await()
      expect(pool.used.keys).toHaveSize(1)
      expect(pool.available).toHaveSize(2)
      conn.close()
      expect(pool.used.keys).toHaveSize(0)
      expect(pool.available).toHaveSize(3)
    }

    pool.close()
    expect(pool.available).toHaveSize(0)
    expect(pool.used.keys).toHaveSize(0)
  }

  @Test fun `close same connection multiple times`() {
    val conn = pool.connection
    expect(pool.used.keys).toHaveSize(1)
    expect(pool.available).toHaveSize(0)
    expect(pool.size.get()).toEqual(1)

    conn.close()
    conn.close()
    conn.close()

    expect(pool.used.keys).toHaveSize(0)
    expect(pool.available).toHaveSize(1)
    expect(pool.size.get()).toEqual(1)

    val conn2 = pool.connection
    expect(pool.connection).notToEqual(conn2)
    expect(pool.connection).notToEqual(conn2)
  }

  @Test fun `failing connections decrease pool size`() {
    val conn = pool.connection
    expect(pool.size.get()).toEqual(1)
    every { conn.unwrap<Connection>().rollback() } throws SQLException("already closed")
    conn.close()

    expect(pool.size.get()).toEqual(0)
    expect(pool.used.keys).toHaveSize(0)
    expect(pool.available).toHaveSize(0)
  }
}
