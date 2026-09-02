package klite.openapi

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import klite.json.JsonMapper
import org.junit.jupiter.api.Test

class JsonSerializationTest {
  @Test fun `ParameterIn serialization using klite-json`() {
    expect(JsonMapper().render(PATH)).toEqual("\"path\"")
  }
}
