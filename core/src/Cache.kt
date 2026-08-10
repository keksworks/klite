package klite

import java.lang.System.currentTimeMillis
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.time.Duration

/** Simple in-memory cache with automatic expiration */
class Cache<K: Any, V>(val expiration: Duration, autoRemoveExpired: Boolean = true, val prolongOnAccess: Boolean = false, val keepAlive: (Map.Entry<K, Cache<K, V>.Entry<V>>) -> Unit = {}): AutoCloseable {
  private val expirationMs = expiration.inWholeMilliseconds
  val entries = ConcurrentHashMap<K, Entry<V>>()
  private val expirationTimer = if (autoRemoveExpired) thread(name = "${this}ExpirationTimer", isDaemon = true) {
    while (!Thread.interrupted()) {
      try { sleep(expirationMs) } catch (e: InterruptedException) { break }
      removeExpired()
    }
  } else null

  operator fun get(key: K) = entries[key]?.takeIf { !it.isExpired() }?.access()
  operator fun set(key: K, value: V) { entries.put(key, Entry(value)) }
  inline fun getOrSet(key: K, compute: (key: K) -> V) = entries.getOrPut(key) { Entry(compute(key)) }.access()
  fun isEmpty() = entries.isEmpty()

  fun removeExpired() {
    val now = currentTimeMillis()
    val i = entries.iterator()
    while (i.hasNext()) {
      val entry = i.next()
      keepAlive(entry)
      if (entry.value.isExpired(now)) i.remove()
    }
  }

  override fun close() {
    entries.clear()
    expirationTimer?.interrupt()
  }

  inner class Entry<V>(val value: V, var since: Long = currentTimeMillis()) {
    fun access() = value.also {
      if (prolongOnAccess) since = currentTimeMillis()
    }
    fun isExpired(now: Long = currentTimeMillis()) = since + expirationMs < now
  }
}
