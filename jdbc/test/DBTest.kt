import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import klite.jdbc.Transaction
import klite.jdbc.withConnection
import org.junit.jupiter.api.Test
import java.sql.Connection
import javax.sql.DataSource

class DBTest {
  val conn = mockk<Connection>(relaxed = true)
  val db = mockk<DataSource> {
    every { connection } returns conn
  }

  @Test fun `withConnection should not close a connection`() {
    conn.withConnection {
      expect(this).toBeTheInstance(conn)
    }
    verify(exactly = 0) { conn.close() }
  }

  @Test fun `withConnection gets a new one from DataSource`() {
    db.withConnection {
      expect(this).toBeTheInstance(conn)
    }
    verify(exactly = 1) {
      db.connection
      conn.close()
    }
  }

  @Test fun `withConnection gets a current one from Transaction`() {
    val tx = Transaction().attachToThread()
    try {
      db.withConnection {
        expect(this).toBeTheInstance(conn)
      }
      db.withConnection {
        expect(this).toBeTheInstance(conn)
      }
      verify(exactly = 1) { db.connection }
      verify(exactly = 0) { conn.close() }
    } finally {
      tx.close()
      verify(exactly = 1) { conn.close() }
    }
  }
}
