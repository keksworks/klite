package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class MultipleTransactionsTest {
  val db1 = mockk<DataSource>()
  val db2 = mockk<DataSource>()

  @Test fun `multiple transactions on different DataSources`() {
    val tx1 = Transaction(db1)
    tx1.attachToThread()
    expect(Transaction.current()).toBeTheInstance(tx1)

    val tx2 = Transaction(db2)
    tx2.attachToThread()
    expect(Transaction.current()).toBeTheInstance(tx2)

    tx2.close()
    expect(Transaction.current()).toBeTheInstance(tx1)

    tx1.close()
    expect(Transaction.current()).toEqual(null)
  }

  @Test fun `nested transactions on same DataSource`() {
    val tx1 = Transaction(db1)
    tx1.attachToThread()

    val tx2 = Transaction(db1)
    tx2.attachToThread()

    expect(Transaction.current()).toBeTheInstance(tx2)

    tx2.close()
    expect(Transaction.current()).toEqual(null)

    tx1.close()
    expect(Transaction.current()).toEqual(null)
  }
}
