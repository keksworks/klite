package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import klite.TSID
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KClass

class BaseCrudRepositoryTest {
  @Test fun `generateId for TSID`() {
    class TSIDEntity(override val id: TSID<TSIDEntity>): BaseEntity<TSID<TSIDEntity>>
    class Repo(db: DataSource): BaseCrudRepository<TSIDEntity, TSID<TSIDEntity>>(db, "")
    expect(Repo(mockk()).generateId()).toBeAnInstanceOf<TSID<*>>()
  }

  @Test fun `generateId for UUID`() {
    class Repo(db: DataSource): CrudRepository<Entity>(db, "")
    expect(Repo(mockk()).generateId()).toBeAnInstanceOf<UUID>()
  }

  open class Invoice<K>(override val id: UUID): BaseEntity<UUID>
  class Sales
  abstract class UUIDRepository<E: BaseEntity<UUID>>(db: DataSource, table: String): BaseCrudRepository<E, UUID>(db, table) {
    val detectedEntityClass: KClass<*> get() = entityClass
  }

  @Test fun `entityClass of direct subclass`() {
    class Repo(db: DataSource): UUIDRepository<Invoice<Sales>>(db, "")
    expect(Repo(mockk()).detectedEntityClass).toEqual(Invoice::class)
  }

  @Test fun `entityClass with base class without type parameters`() {
    abstract class InvoiceRepository(db: DataSource, table: String): UUIDRepository<Invoice<Sales>>(db, table)
    class Repo(db: DataSource): InvoiceRepository(db, "")
    expect(Repo(mockk()).detectedEntityClass).toEqual(Invoice::class)
  }

  @Test fun `entityClass with generic base class having a different first type argument`() {
    abstract class InvoiceRepository<K>(db: DataSource, table: String): UUIDRepository<Invoice<K>>(db, table)
    class Repo(db: DataSource): InvoiceRepository<Sales>(db, "")
    expect(Repo(mockk()).detectedEntityClass).toEqual(Invoice::class)
  }

  @Test fun `entityClass can be overridden if not detectable`() {
    class GenericRepo<E: BaseEntity<UUID>>(db: DataSource): UUIDRepository<E>(db, "")
    expect { GenericRepo<Invoice<Sales>>(mockk()).detectedEntityClass }.toThrow<IllegalStateException>().messageToContain("GenericRepo", "override entityClass")

    class OverriddenRepo<E: BaseEntity<UUID>>(db: DataSource, override val entityClass: KClass<E>): UUIDRepository<E>(db, "")
    expect(OverriddenRepo<Invoice<Sales>>(mockk(), Invoice::class as KClass<Invoice<Sales>>).detectedEntityClass).toEqual(Invoice::class)
  }

  @Test fun `entity without public id property`() {
    class Repo(db: DataSource): UUIDRepository<Invoice<Sales>>(db, "") {
      override val entityClass get() = Sales::class as KClass<Invoice<Sales>>
    }
    expect { Repo(mockk()).generateId() }.toThrow<IllegalStateException>().messageToContain("Sales has no public id property")
  }
}
