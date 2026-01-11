package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class TransactionTest {
  val db = mockk<ConfigDataSource>(relaxed = true)

  @Test fun `transaction does not open connection on creation`() {
    val tx = Transaction()
    verify(exactly = 0) { db.connection }
    tx.close(true)
    verify(exactly = 0) { db.connection }
  }

  @Test fun `transaction creates and reuses connection on demand`() {
    val tx = Transaction().attachToThread()
    val conn = db.withConnection { this }
    expect(db.withConnection { this }).toBeTheInstance(conn)
    verify { conn.autoCommit = false }
    tx.close(true)
    verify(exactly = 1) {
      db.connection.apply {
        commit()
        autoCommit = true
        close()
      }
    }
  }

  @Test fun `transaction commits multiple connections`() {
    val tx = Transaction().attachToThread()
    val db2 = mockk<ConfigDataSource>(relaxed = true)
    val conn1 = db.withConnection { this }
    val conn2 = db2.withConnection { this }

    verify { conn1.autoCommit = false }
    verify { conn2.autoCommit = false }

    tx.close(true)

    verify {
      conn1.commit()
      conn1.autoCommit = true
      conn1.close()
      conn2.commit()
      conn2.autoCommit = true
      conn2.close()
    }
  }

  @Test fun `transaction with rollbackOnly rolls back`() {
    val tx = Transaction().attachToThread()
    val conn = db.withConnection { this }
    verify { conn.autoCommit = false }
    tx.close(false)
    verify(exactly = 1) {
      db.connection.apply {
        rollback()
        autoCommit = true
        close()
      }
    }
  }

  @Test fun `connection without transaction is closed`() {
    val conn = db.withConnection { this }
    verify { conn.close() }
    verify(exactly = 0) { conn.autoCommit = any() }
  }
}
