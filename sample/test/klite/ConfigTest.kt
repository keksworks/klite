package klite

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile

class ConfigTest {
  init {
    Config["TEST_SOMETHING"] = "VALUE"
  }

  @Test fun required() {
    expect(Config["TEST_SOMETHING"]).toEqual("VALUE")
    expect(Config.required("TEST_SOMETHING")).toEqual("VALUE")
    expect { Config["NOTHING"] }.toThrow<IllegalStateException>().messageToContain("NOTHING should be provided as system property or env var")
  }

  @Test fun optional() {
    expect(Config.optional("TEST_SOMETHING")).toEqual("VALUE")
    expect(Config.optional("NOTHING")).toEqual(null)
    expect(Config.optional("NOTHING", "DEFAULT")).toEqual("DEFAULT")
  }

  @Test fun inherited() {
    expect(Config.inherited("TEST_SOMETHING.SOMETHING.MORE")).toEqual("VALUE")
    expect(Config.inherited("TEST_SOMETHINGxxx.SOMETHING.MORE")).toEqual(null)
    expect(Config.inherited("TEST_SOMETHINGxxx.SOMETHING.MORE", "DEFAULT")).toEqual("DEFAULT")

    Config["TEST_SOMETHING.SOMETHING"] = "SOMETHING"
    expect(Config.inherited("TEST_SOMETHING.SOMETHING.MORE")).toEqual("SOMETHING")
    Config["TEST_SOMETHING.SOMETHING.MORE"] = "MORE"
    expect(Config.inherited("TEST_SOMETHING.SOMETHING.MORE")).toEqual("MORE")
  }

  @Test fun overridable() {
    Config.overridable("TEST_SOMETHING", "VALUE2")
    expect(Config["TEST_SOMETHING"]).toEqual("VALUE")

    Config.overridable("TEST_SOMETHING2", "VALUE2")
    expect(Config["TEST_SOMETHING2"]).toEqual("VALUE2")
  }

  @Test fun envFileInterpolation() {
    val file = createTempFile().toFile()
    file.writeText("TEST_INTERPOLATION_BASE=BASE\nTEST_INTERPOLATION=\${TEST_INTERPOLATION_BASE}/VALUE\n")
    Config.useEnvFile(file)
    file.delete()
    expect(Config["TEST_INTERPOLATION"]).toEqual("BASE/VALUE")
  }
}
