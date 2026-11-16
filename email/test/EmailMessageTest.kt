package klite.email

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class EmailMessageTest {
  @Test fun `decodeHeader`() {
    expect(decodeHeader("Tere")).toEqual("Tere")
    expect(decodeHeader("=?UTF-8?B?VGVyZQ==?=")).toEqual("Tere")
    expect(decodeHeader("=?UTF-8?Q?T=C3=A4ht?= =?UTF-8?Q?_=C3=A4ri?=")).toEqual("Täht äri")
    expect(decodeHeader("Re: =?UTF-8?B?VGVyZQ==?=")).toEqual("Re: Tere")
  }
}
