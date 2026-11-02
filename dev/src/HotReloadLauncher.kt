package klite.dev

/**
 * Starts the application in a disposable classloader and restarts it when the build output changes.
 *
 * The application class must have a static, no-argument factory method returning a `klite.Server`.
 * The factory must not start the server; the launcher starts and stops it.
 *
 * Usage: `klite.dev.HotReloadLauncherKt <application class> <factory method> [gracePeriodMs]`
 */
fun main(args: Array<String>) {
  require(args.size >= 2) { "Usage: HotReloadLauncherKt <application class> <factory method> [gracePeriodMs]" }
  HotReload.launch(args[0], args[1], args.getOrNull(2)?.toLong() ?: 300)
}
