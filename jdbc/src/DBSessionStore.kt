package klite.jdbc

import klite.*
import java.util.*
import java.util.UUID.randomUUID
import javax.sql.DataSource

/**
 * Keeps session attributes in a DB table and only a random session id in the cookie, unlike [klite.CookieSessionStore]
 * which stores them all in the cookie. That way session size is not limited by the cookie, and a session can be
 * invalidated server-side by deleting its row.
 *
 * Include the changeset that ships as `migrator/db_sessions.sql` into your `db.sql` to create the table:
 * ```sql
 * --include migrator/db_sessions.sql
 * ```
 * Attributes are stored as jsonb, so the json module is required on the runtime classpath.
 *
 * An id presented in a cookie but unknown to the table is never adopted, and [klite.Session.clear] deletes the stored
 * session, so the next write is given a new id.
 *
 * Rows of sessions that are simply abandoned are not deleted, remove them periodically using `updatedAt`.
 */
open class DBSessionStore(
  private val db: DataSource,
  val table: String = "db_sessions",
  val cookie: Cookie = Cookie("S", "", path = "/", httpOnly = true)
): SessionStore {
  override fun load(exchange: HttpExchange): Session {
    val id = exchange.sessionId ?: return Session()
    val params = db.query("select params from ${q(table)}", listOf("id" to id)) { getJsonOrNull<Map<String, String?>>("params") }.firstOrNull()
    return params?.let { Session(it.toMutableMap(), isNew = false) } ?: Session()
  }

  override fun save(exchange: HttpExchange, session: Session) {
    if (!session.changed) return
    val presentedId = exchange.sessionId
    if (session.params.isEmpty()) {
      presentedId?.let { delete(it) }
      return
    }
    val id = presentedId?.takeIf { !session.isNew && exists(it) } ?: randomUUID()
    if (id != presentedId) {
      // a new session replaces the old one, which happens when a cleared session is written again, e.g. on login
      presentedId?.let { delete(it) }
      exchange += cookie.copy(value = id.toString(), secure = exchange.isSecure)
    }
    db.upsert(table, mapOf("id" to id, "params" to jsonb(session.params), "updatedAt" to SqlComputed("current_timestamp")))
  }

  private fun delete(id: UUID): Int = db.delete(table, "id" to id)

  private fun exists(id: UUID) = db.query("select 1 from ${q(table)}", listOf("id" to id)) { 1 }.isNotEmpty()

  private val HttpExchange.sessionId get() = runCatching { cookie(cookie.name)?.uuid }.getOrNull()
}
