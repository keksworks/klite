package klite

import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.util.*

class LoggerTest {
  @Test fun name() {
    expect(javaClass.nonAnonymousName()).toEqual(LoggerTest::class.java.name)
    expect((object: Date() {}).javaClass.nonAnonymousName()).toEqual(Date::class.java.name)
  }

  @Test fun testLogger() {
    expect(logger()).toBeAnInstanceOf<System.Logger>()
  }
}
