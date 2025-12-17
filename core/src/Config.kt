package klite

import klite.Config.useEnvFile
import java.io.File

/**
 * An overridable way to read configuration from env vars or system properties,
 * as per 12-factor apps spec. In development, it is convenient to use [useEnvFile],
 * which will skip already set env vars by default, giving them precedence.
 */
object Config {
  fun optional(env: String): String? = System.getProperty(env) ?: System.getenv(env)
  fun optional(env: String, default: String) = optional(env) ?: default
  fun required(env: String) = optional(env) ?: error("$env should be provided as system property or env var")

  /** For dot-separated inheritance, e.g. logger level */
  fun inherited(env: String): String? = optional(env) ?: if ("." in env) inherited(env.substringBeforeLast(".")) else null
  fun inherited(env: String, default: String): String = inherited(env) ?: default

  /** List of active configurations, e.g. dev or prod, from ENV var */
  val active: Set<String> by lazy {
    optional("ENV", "dev").split(",").map { it.trim() }.toSet()
  }
  fun isActive(conf: String) = conf in active
  fun isAnyActive(vararg confs: String) = confs.any { it in active }

  operator fun get(env: String) = required(env)
  operator fun set(env: String, value: String): String? = System.setProperty(env, value)

  fun overridable(env: String, value: String) {
    if (optional(env) == null) Config[env] = value
  }

  /**
   * Use this as the first thing in your app before creating a Server instance.
   * @param force use to override already set env vars
   */
  fun useEnvFile(name: String = ".env", force: Boolean = false) = useEnvFile(File(name), force)
  fun useEnvFile(file: File, force: Boolean = false) {
    if (!force && !file.exists()) return logger().info("No ${file.absolutePath} found, skipping")
    file.forEachLine {
      val line = it.trim()
      if (line.isNotEmpty() && !line.startsWith('#'))
        line.split('=', limit = 2).let { (key, value) ->
          if (force || optional(key) == null) set(key, value)
        }
    }
  }
}

val Config.isDev get() = isActive("dev")
val Config.isTest get() = isActive("test")
val Config.isProd get() = isActive("prod")
