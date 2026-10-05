package users

import klite.jdbc.BaseCrudRepository
import klite.jdbc.NullableId
import klite.jdbc.UpdatableEntity
import klite.uuid
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource

/**
 * A sample type-safe Id class.
 * Beware: Mockk's any() matcher has [trouble with inline classes](https://github.com/mockk/mockk/issues/847).
 * Workaround: `fun <T> MockKMatcherScope.anyId(): Id<T> = Id(ofType(UUID::class))`
 */
@JvmInline value class Id<T>(val uuid: UUID): Comparable<Id<T>> {
  constructor(): this(UUID.randomUUID())
  constructor(uuid: String): this(uuid.uuid)
  override fun toString() = uuid.toString()
  override fun compareTo(other: Id<T>) = uuid.compareTo(other.uuid)
}

fun <T> String.toId(): Id<T> = Id(uuid)

fun <T> ResultSet.getId(column: String = "id") = getString(column).toId<T>()
fun <T> ResultSet.getIdOrNull(column: String) = getString(column)?.toId<T>()

interface Entity<T>: NullableId<Id<T>>, UpdatableEntity
abstract class CrudRepository<T: Entity<T>>(db: DataSource, table: String): BaseCrudRepository<T, Id<T>?>(db, table)
