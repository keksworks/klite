package klite.csv

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class CSVParserTest {
  val parser = CSVParser(separator = ";")

  @Test fun quotes() {
    expect(parser.splitLine("""1;2;"hello; world";4""").toList()).toContainExactly("1", "2", "hello; world", "4")
    expect(parser.splitLine("\"\"\"Aare Mägi FLORES\"\"\";101;;R").toList()).toContainExactly("\"Aare Mägi FLORES\"", "101", "", "R")
  }

  @Test fun parse() {
    val lines = parser.parse("""
      ${bomUTF8}nimi;ariregistri_kood;ettevotja_oiguslik_vorm;ettevotja_oigusliku_vormi_alaliik;kmkr_nr;ettevotja_staatus;ettevotja_staatus_tekstina;ettevotja_esmakande_kpv;ettevotja_aadress;asukoht_ettevotja_aadressis;asukoha_ehak_kood;asukoha_ehak_tekstina;indeks_ettevotja_aadressis;ads_adr_id;ads_ads_oid;ads_normaliseeritud_taisaadress;teabesysteemi_link
      001 Kinnisvara OÜ;12652512;Osaühing;;EE101721589;R;Registrisse kantud;25.04.2014;;Õismäe tee 78-9;0176;Haabersti linnaosa, Tallinn, Harju maakond;13513;2182337;;Harju maakond, Tallinn, Haabersti linnaosa, Õismäe tee 78-9;https://ariregister.rik.ee/est/company/12652512
    """.trimIndent().byteInputStream()).toList()
    expect(lines.first()).toEqual(mapOf(
      "nimi" to "001 Kinnisvara OÜ",
      "ariregistri_kood" to "12652512",
      "ettevotja_oiguslik_vorm" to "Osaühing",
      "ettevotja_oigusliku_vormi_alaliik" to "",
      "kmkr_nr" to "EE101721589",
      "ettevotja_staatus" to "R",
      "ettevotja_staatus_tekstina" to "Registrisse kantud",
      "ettevotja_esmakande_kpv" to "25.04.2014",
      "ettevotja_aadress" to "",
      "asukoht_ettevotja_aadressis" to "Õismäe tee 78-9",
      "asukoha_ehak_kood" to "0176",
      "asukoha_ehak_tekstina" to "Haabersti linnaosa, Tallinn, Harju maakond",
      "indeks_ettevotja_aadressis" to "13513",
      "ads_adr_id" to "2182337",
      "ads_ads_oid" to "",
      "ads_normaliseeritud_taisaadress" to "Harju maakond, Tallinn, Haabersti linnaosa, Õismäe tee 78-9",
      "teabesysteemi_link" to "https://ariregister.rik.ee/est/company/12652512"
    ))
  }
}
