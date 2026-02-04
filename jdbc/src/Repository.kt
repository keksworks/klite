package klite.jdbc

import klite.PropValue
import klite.publicProperties
import klite.toValues
import org.intellij.lang.annotations.Language
import java.sql.ResultSet
import java.time.Instant
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

interface BaseEntity<ID> {
  val id: ID
}

interface NullableId<ID>: BaseEntity<ID?> {
  override var id: ID?
}

/**
 * Implement this for optimistic locking support in [BaseCrudRepository.save],
 * `add column updatedAt timestamptz not null default now()`, or use @Column annotation to give it a different name
 */
interface UpdatableEntity {
  var updatedAt: Instant? // initialize to null by default for new entities
}

@Deprecated("Declare your own Entity interface using BaseEntity and other interfaces", replaceWith = ReplaceWith("BaseEntity<UUID>"))
interface Entity: BaseEntity<UUID>

abstract class BaseRepository(protected val db: DataSource, val table: String) {
  protected open val orderAsc get() = "order by createdAt"
  protected open val orderDesc get() = "$orderAsc desc"
}

@Deprecated("Declare your own CrudRepository base class by extending BaseCrudRepository", replaceWith = ReplaceWith("BaseCrudRepository<Entity, UUID>"))
abstract class CrudRepository<E: Entity>(db: DataSource, table: String): BaseCrudRepository<E, UUID>(db, table) {
  override fun generateId() = UUID.randomUUID()
}

abstract class BaseCrudRepository<E: BaseEntity<ID>, ID>(db: DataSource, table: String): BaseRepository(db, table) {
  @Suppress("UNCHECKED_CAST")
  protected open val entityClass = this::class.supertypes.first().arguments.first().type!!.classifier as KClass<E>
  protected open val idProp = entityClass.publicProperties[BaseEntity<*>::id.name]!!
  protected open val updatedAtProp = entityClass.publicProperties[UpdatableEntity::updatedAt.name]

  override val orderAsc get() = "order by $table.createdAt"
  open val defaultOrder get() = orderDesc
  open val selectFrom @Language("SQL", prefix = "select * from ") get() = table

  /** Override to customize how entity properties are being converted to table columns during queries */
  protected open fun ResultSet.mapper(): E = create(entityClass)
  /** Override to customize how table columns are being converted to entity properties during inserts/updates */
  protected open fun E.persister(): Map<out ColName, Any?> = toValues()

  open fun get(id: ID, forUpdate: Boolean = false): E = db.select(selectFrom, id, "$table." + idProp.colName,
    if (forUpdate) (if (isPostgres) "for no key update" else "for update") else "") { mapper() }

  open fun list(vararg where: PropValue<E, *>?, @Language("SQL", prefix = selectFromTable) suffix: String = defaultOrder): List<E> =
    db.select(selectFrom, where.filterNotNull(), suffix) { mapper() }

  open fun by(vararg where: PropValue<E, *>?, @Language("SQL", prefix = selectFromTable) suffix: String = ""): E? = list(*where, suffix = suffix).firstOrNull()
  open fun count(vararg where: PropValue<E, *>?): Long = db.count(selectFrom, where.filterNotNull())

  open fun save(entity: E): Int {
    var useInsert = false
    (entity as? NullableId<ID>)?.takeIf { it.id == null }?.let {
      it.id = generateId()
      useInsert = true
    }
    if (entity is UpdatableEntity) {
      useInsert = useInsert || entity.updatedAt == null
      val now = nowSec()
      if (!useInsert) {
        val numUpdated = db.update(table, entity.persister() + (updatedAtProp!! to now),
            where = listOf(idProp to entity.id, updatedAtProp!! to entity.updatedAt))
        if (numUpdated == 0) throw StaleEntityException()
        entity.updatedAt = now
        return numUpdated
      }
      entity.updatedAt = now
    }
    return if (useInsert) db.insert(table, entity.persister())
      else db.upsert(table, entity.persister(), idProp.colName)
  }

  /** Recommended to override if used with [NullableId] */
  open fun generateId(): ID {
    val idClass = idProp.returnType.classifier as KClass<*>
    return (idClass.constructors.find { it.parameters.isEmpty() } ?: idClass.primaryConstructor!!).callBy(emptyMap()) as ID
  }
}

fun nowSec(): Instant = Instant.ofEpochSecond(System.currentTimeMillis() / 1000)
