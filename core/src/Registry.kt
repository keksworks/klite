package klite

import java.lang.System.Logger.Level.DEBUG
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

interface Registry {
  fun <T: Any> require(type: KClass<T>): T
  fun <T: Any> requireAll(type: KClass<T>): List<T>
  fun contains(type: KClass<*>): Boolean
  fun <T: Any> optional(type: KClass<T>) = if (contains(type)) require(type) else null
}

interface MutableRegistry: Registry {
  fun <T: Any, I: T> register(type: KClass<T>, implementation: KClass<I>)
  fun <T: Any> register(type: KClass<T>, instance: T)
}

inline fun <reified T: Any> Registry.require() = require(T::class)
inline fun <reified T: Any> Registry.optional() = optional(T::class)
inline fun <reified T: Any> Registry.requireAll() = requireAll(T::class)
inline fun <reified T: Any> MutableRegistry.register(instance: T) = register(T::class, instance)
inline fun <reified T: Any> MutableRegistry.register() = register(T::class, T::class)
inline fun <reified T: Any> MutableRegistry.register(implementation: KClass<out T>) = register(T::class, implementation)

class RegistryException(message: String, cause: Throwable? = null): Exception(message, cause)

@Suppress("UNCHECKED_CAST")
open class SimpleRegistry: MutableRegistry {
  private val instances = mutableMapOf<KClass<*>, Any>(Registry::class to this)

  override fun <T: Any, I: T> register(type: KClass<T>, implementation: KClass<I>) = register(type, create(implementation))
  override fun <T: Any> register(type: KClass<T>, instance: T) {
    instances[type] = instance
    if (instance::class != type) instances[instance::class] = instance
  }

  override fun contains(type: KClass<*>) = instances.contains(type)
  override fun <T: Any> optional(type: KClass<T>) = instances[type] as T?
  override fun <T: Any> require(type: KClass<T>) = optional(type) ?: create(type).also { register(type, it) }
  override fun <T: Any> requireAll(type: KClass<T>): List<T> = instances.values.filter { type.java.isAssignableFrom(it.javaClass) }.distinct() as List<T>

  open fun <T: Any> create(type: KClass<T>): T = type.createInstance()
}

/**
 * Implements simple constructor injection that can easily replace your more complex dependency injection framework.
 * You may extend this class to override how exactly constructor parameters are created.
 * 
 * Parameters with default values are populated from the registry falling back to the default value if not found.
 * Parameters without default values are required to be present in the registry.
 */
open class DependencyInjectingRegistry: SimpleRegistry() {
  private val log = logger()

  override fun <T: Any> create(type: KClass<T>): T {
    val constructor = chooseConstructor(type) ?: throw RegistryException("$type has no usable constructor")
    try {
      val args = createArgs(constructor)
      return constructor.callBy(args).also {
        log.log(DEBUG) { "Auto-created ${type.simpleName}${args.values.map { it::class.simpleName }}" }
      }
    } catch (e: Throwable) {
      throw RegistryException("Failed to auto-create ${type.simpleName} with dependencies on ${constructor.parameters.map {it.type}}", e.cause ?: e)
    }
  }

  protected open fun <T : Any> chooseConstructor(type: KClass<T>): KFunction<T>? =
    type.primaryConstructor ?: type.constructors.minByOrNull { it.parameters.size }

  protected open fun <T : Any> createArgs(constructor: KFunction<T>): Map<KParameter, Any> =
    constructor.parameters.filter { !it.isOptional || contains(it.type.jvmErasure) }.associateWith { require(it.type.jvmErasure) }
}
