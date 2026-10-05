package klite

import klite.crypto.KeyCipher
import klite.crypto.KeyGenerator
import kotlin.time.Duration

class Session(
  val params: MutableMap<String, String?> = mutableMapOf(),
  isNew: Boolean = true
) {
  /** true until the session is loaded from a store, and true again after [clear], so that a store knows it has no stored content */
  var isNew = isNew; private set
  var changed = false; private set

  operator fun get(key: String) = params[key]
  operator fun set(key: String, value: String?) = params.put(key, value).also { changed = true }
  fun clear() = params.clear().also { changed = true; isNew = true }
}

interface SessionStore {
  fun load(exchange: HttpExchange): Session
  fun save(exchange: HttpExchange, session: Session)
}

/**
 * Stores the whole session in an encrypted cookie, so no server-side state is needed. Clearing the session expires
 * the cookie, but nothing is invalidated server-side, so a replayed cookie stays valid until `SESSION_SECRET`
 * changes - use DBSessionStore from the jdbc module where logout has to invalidate a session.
 */
open class CookieSessionStore(
  sessionSecret: String = Config.required("SESSION_SECRET"),
  val cookie: Cookie = Cookie("S", "", path = "/", httpOnly = true),
  keyGenerator: KeyGenerator = KeyGenerator()
): SessionStore {
  private val log = logger()
  private val keyCipher = KeyCipher(keyGenerator.keyFromSecret(sessionSecret))

  override fun load(exchange: HttpExchange) = exchange.cookie(cookie.name)?.let {
    try {
      Session(urlDecodeParams(keyCipher.decrypt(it)) as MutableMap<String, String?>, isNew = false)
    } catch (e: Exception) {
      log.info("Failed to decrypt session cookie: $e"); null
    }
  } ?: Session()

  override fun save(exchange: HttpExchange, session: Session) {
    if (!session.changed) return
    val sessionCookie = cookie.copy(secure = exchange.isSecure)
    exchange += if (session.params.isEmpty())
      sessionCookie.copy(value = "", maxAge = Duration.ZERO)
    else
      sessionCookie.copy(value = keyCipher.encrypt(urlEncodeParams(session.params)))
  }
}
