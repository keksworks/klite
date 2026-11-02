package klite.dev

import klite.Server
import klite.error
import klite.info
import klite.logger
import klite.warn
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.concurrent.thread

internal object HotReload {
  private val log = logger(HotReload::class)
  private var server: Server? = null
  private var appLoader: AppClassLoader? = null
  private var appClass: String? = null
  private var factoryMethod: String? = null
  private var watchDirs: List<Path> = emptyList()

  /**
   * Loads the application in a disposable classloader and watches its build output for changes.
   * The application must expose a static no-arg factory method that returns a [Server].
   */
  fun launch(appClass: String, factoryMethod: String, gracePeriodMs: Long = 300) {
    val dirs = findBuildDirs(appClass) ?: run {
      log.error("Cannot determine build directory of $appClass, hot reload is not enabled")
      return
    }

    this.appClass = appClass
    this.factoryMethod = factoryMethod
    watchDirs = dirs
    startApp()
    startWatcher(gracePeriodMs)
  }

  private fun startWatcher(gracePeriodMs: Long) {
    val dirs = watchDirs
    if (dirs.isEmpty()) return
    log.info("Hot reload watching ${dirs.joinToString()}")
    // must be non-daemon, otherwise the JVM exits when the old server is stopped
    thread(name = "klite-hot-reload", isDaemon = false) {
      watchLoop(dirs, gracePeriodMs)
    }
  }

  /** Finds the build output without loading the class into the system classloader. */
  private fun findBuildDirs(className: String): List<Path>? {
    val classFile = className.replace('.', '/') + ".class"
    val url = ClassLoader.getSystemResource(classFile) ?: return null
    var classes = runCatching { Path.of(url.toURI()) }.getOrNull() ?: return null
    repeat(Path.of(classFile).nameCount) { classes = classes.parent ?: return null }
    return buildDirs(classes.toAbsolutePath().normalize())
  }

  private fun buildDirs(classes: Path): List<Path>? {
    if (!Files.isDirectory(classes)) return null
    val build = generateSequence(classes) { it.parent }.firstOrNull { it.fileName?.toString() == "build" }
    val resources = build?.resolve("resources/main")?.takeIf { Files.isDirectory(it) }
    return listOfNotNull(classes, resources)
  }

  private fun watchLoop(dirs: List<Path>, gracePeriodMs: Long) {
    val watchService = FileSystems.getDefault().newWatchService()
    val keys = mutableMapOf<WatchKey, Path>()
    dirs.forEach { register(it, watchService, keys) }

    while (true) {
      var key = try {
        watchService.take()
      } catch (e: InterruptedException) {
        return
      }

      while (true) {
        process(key, watchService, keys)
        key = try {
          watchService.poll(gracePeriodMs, MILLISECONDS)
        } catch (e: InterruptedException) {
          return
        } ?: break
      }

      try {
        reload()
      } catch (e: Throwable) {
        log.error("Hot reload failed", e)
      }
    }
  }

  private fun process(key: WatchKey, watchService: WatchService, keys: MutableMap<WatchKey, Path>) {
    val dir = keys[key]
    if (dir != null) {
      for (event in key.pollEvents()) {
        if (event.kind() == OVERFLOW) continue
        val path = (event.context() as? Path)?.let { dir.resolve(it) } ?: continue
        if (event.kind() == ENTRY_CREATE && Files.isDirectory(path)) register(path, watchService, keys)
      }
    }
    if (!key.reset()) keys.remove(key)
  }

  private fun register(root: Path, watchService: WatchService, keys: MutableMap<WatchKey, Path>) {
    try {
      Files.walk(root).use { paths ->
        paths.filter { Files.isDirectory(it) }.forEach { dir ->
          if (keys.values.any { it == dir }) return@forEach
          try {
            keys[dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)] = dir
          } catch (e: Exception) {
            log.warn("Cannot watch $dir", e)
          }
        }
      }
    } catch (e: Exception) {
      log.warn("Cannot watch $root", e)
    }
  }

  private fun reload() {
    server?.let {
      log.info("Hot reload: stopping server")
      it.stop(0)
    }
    server = null
    startApp()
  }

  private fun startApp() {
    val appClass = appClass ?: return
    val factoryMethod = factoryMethod ?: return
    val dirs = watchDirs

    appLoader?.close()
    appLoader = null

    val loader = AppClassLoader(dirs, Server::class.java.classLoader)
    val tccl = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = loader
    try {
      val cls = Class.forName(appClass, true, loader)
      val factory = findFactory(cls, factoryMethod)
      val newServer = factory.invoke(null) as? Server
        ?: error("$appClass.$factoryMethod must return klite.Server")
      log.info("Starting $appClass.$factoryMethod")
      server = newServer
      newServer.start()
      appLoader = loader
    } catch (e: Throwable) {
      server?.stop(0)
      server = null
      loader.close()
      log.error("Failed to start $appClass.$factoryMethod", e.cause ?: e)
    } finally {
      Thread.currentThread().contextClassLoader = tccl
    }
  }

  private fun findFactory(cls: Class<*>, name: String): Method =
    cls.declaredMethods.firstOrNull {
      it.name == name && Modifier.isStatic(it.modifiers) && it.parameterCount == 0
    } ?: error("No static $name() without parameters found in ${cls.name}")
}

/**
 * Loads application classes from the build output before delegating to the parent,
 * because the same directories are also present on the system classpath when run via Gradle.
 */
private class AppClassLoader(
  private val dirs: List<Path>,
  parent: ClassLoader,
): URLClassLoader(dirs.map { it.toUri().toURL() }.toTypedArray(), parent) {
  override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
    findLoadedClass(name) ?: run {
      val cls = if (dirs.any { Files.exists(it.resolve(name.replace('.', '/') + ".class")) })
        findClass(name)
      else
        super.loadClass(name, false)
      if (resolve) resolveClass(cls)
      cls
    }
  }

  override fun getResource(name: String): URL? = findResource(name) ?: parent.getResource(name)
}
