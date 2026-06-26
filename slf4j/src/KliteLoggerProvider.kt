package klite.slf4j

import org.slf4j.helpers.NOP_FallbackServiceProvider

class KliteLoggerProvider: NOP_FallbackServiceProvider() {
  private val loggerFactory = KliteLoggerFactory()
  override fun getLoggerFactory() = loggerFactory

  private val mdcAdapter = KliteMDCAdapter()
  override fun getMDCAdapter() = mdcAdapter
}
