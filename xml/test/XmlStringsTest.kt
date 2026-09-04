package klite.xml

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class XmlStringsTest {
  @Test fun dropXmlHeader() {
    expect("<?xml?>\n<root/>".dropXmlHeader()).toEqual("<root/>")
  }

  @Test fun dropXmlRoot() {
    expect("<?xml?>\n<root><content/></root>".dropXmlRoot()).toEqual("<content/>")
  }

  @Test fun extractXmlTag() {
    @Language("xml") val xml = "<root><content>Hello</content></root>"
    expect(xml.extractXmlTag("content")).toEqual("<content>Hello</content>")
  }

  @Test fun extractXmlTagStrippingNamespaces() {
    @Language("xml") val xml = "<root xmlns:udt=\"UDT:NS\"><ns:content><udt:DateTimeString><value>123</value></udt:DateTimeString></ns:content></root>"
    expect(xml.extractXmlTag("content"))
      .toEqual("<content><DateTimeString><value>123</value></DateTimeString></content>")
  }

  @Test fun extractXmlTagPreservingNamespace() {
    @Language("xml") val xml = "<root xmlns:udt=\"UDT:NS\"><ns:content>\n<udt:DateTimeString><value>123</value></udt:DateTimeString>\n</ns:content></root>"
    expect(xml.extractXmlTag("content", preserveNs = setOf("UDT:NS")))
      .toEqual("<content xmlns:udt=\"UDT:NS\">\n<udt:DateTimeString><value>123</value></udt:DateTimeString>\n</content>")
  }

  @Test fun extractXmlTagThatHasNamespace() {
    @Language("xml") val xml = "<uilResponse xmlns=\"http://efti.eu/v1/edelivery\" requestId=\"123123123123\" status=\"200\"><consignment xmlns=\"http://efti.eu/v1/consignment/common\">data</consignment></uilResponse>"
    expect(xml.extractXmlTag("consignment"))
      .toEqual("<consignment xmlns=\"http://efti.eu/v1/consignment/common\">data</consignment>")
  }
}
