package klite

import java.lang.System.currentTimeMillis
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Time-Sorted unique ID, a more compact and DB index-friendly alternative to UUID.
 * Add a `typealias Id<T> = TSID<T>` or `Id = TSID<Any>` in your own project.
 */
@JvmInline value class TSID<T>(val value: Long) {
  companion object: TSIDGenerator() {
    init {
      Converter.use { TSID<Any>(it) }
    }
  }

  constructor(): this(generateValue())
  constructor(tsid: String): this(tsid.toLong(36))
  override fun toString() = value.toString(36)

  val createdAt: Instant get() = createdAt(value)
}

open class TSIDGenerator(
  val epoch: Long = Instant.parse(Config.optional("TSID_EPOCH", "2022-10-21T03:45:00.000Z")).toEpochMilli(),
  val randomBits: Int = 22
) {
  val randomMask = (1 shl randomBits) - 1
  private var random = SecureRandom()
  private val counter = AtomicInteger()
  @Volatile private var lastTime = 0L
  var deterministic: AtomicLong? = null

  open fun generateValue(createdAt: Long = currentTimeMillis()): Long {
    deterministic?.let { return it.incrementAndGet() }
    val time = (createdAt - epoch) shl randomBits
    if (time != lastTime) {
      counter.set(random.nextInt())
      lastTime = time
    }
    val tail = counter.incrementAndGet() and randomMask
    return time or tail.toLong()
  }

  open fun createdAt(value: Long): Instant = Instant.ofEpochMilli((value shr randomBits) + epoch)
}
