package klite.jdbc

import klite.Cookie
import klite.HttpExchange
import klite.Session
import klite.SessionStore
import klite.urlDecodeParams
import klite.urlEncodeParams
import java.util.UUID
import java.util.WeakHashMap

/**
 * Stores session data in the database and keeps only its identifier in the cookie.
 *
 * Include [changeSetFile] in the application's DBMigrator changesets before using this store.
 */
open class DBSessionCookie(
  private val db: DB,
  val cookie: Cookie = Cookie("S", "", path = "/", httpOnly = true),
  private val table: String = "db_sessions",
) : SessionStore {
  private val sessionIds = WeakHashMap<Session, String>()

  override fun load(exchange: HttpExchange): Session {
    val id = exchange.cookie(cookie.name) ?: return Session()
    val data = db.select(table, "id" to id) { getString("data") }.firstOrNull() ?: return Session()
    return Session(urlDecodeParams(data).toMutableMap(), isNew = false).also {
      synchronized(sessionIds) { sessionIds[it] = id }
    }
  }

  override fun save(exchange: HttpExchange, session: Session) {
    if (!session.changed) return
    val existingId = synchronized(sessionIds) { sessionIds[session] }
    val id = existingId ?: UUID.randomUUID().toString()
    val data = urlEncodeParams(session.params)
    if (existingId == null) {
      db.insert(table, mapOf("id" to id, "data" to data))
      synchronized(sessionIds) { sessionIds[session] = id }
    } else {
      db.exec("update $table set data = ? where id = ?", data, id)
    }
    exchange += cookie.copy(value = id, secure = exchange.isSecure)
  }

  companion object {
    const val changeSetFile = "migrator/db-sessions.sql"
  }
}
