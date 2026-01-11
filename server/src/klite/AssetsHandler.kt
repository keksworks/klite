package klite

import klite.RequestMethod.GET
import klite.RequestMethod.HEAD
import klite.StatusCode.Companion.OK
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import kotlin.io.path.*
import kotlin.time.Duration.Companion.days

open class AssetsHandler(
  path: Path,
  val indexFile: String = "index.html",
  val useIndexForUnknownPaths: Boolean = false,
  val additionalHeaders: Map<String, String> = mapOf("Cache-Control" to "max-age=${7.days.inWholeSeconds}"),
  val indexHeaders: Map<String, String> = mapOf("Cache-Control" to "max-age=0, must-revalidate"),
  val useChunkedResponseForFilesLargerThan: Long = 30 * (1L shl 20),
  val headerModifier: HttpExchange.(file: Path) -> Unit = {}
): (HttpExchange) -> Any? {
  protected val log = logger()
  val path = path.normalize()

  init {
    if (!path.isDirectory()) log.warn("Assets path ${path.toAbsolutePath()} is not a readable directory")
  }

  override fun invoke(exchange: HttpExchange) {
    if (exchange.method != GET && exchange.method != HEAD) throw NotFoundException(exchange.method.name)
    try {
      var file = (path / exchange.path.substring(1)).normalize()
      if (!file.startsWith(path)) throw ForbiddenException(exchange.path)
      if (file.isDirectory()) file /= indexFile
      if (!file.exists()) {
        if (useIndexForUnknownPaths && !exchange.path.contains(".")) file = path / indexFile
        else throw NotFoundException(exchange.path)
      }
      send(exchange, file)
    } catch (e: FileNotFoundException) {
      throw NotFoundException(e.message, e)
    }
  }

  protected open fun send(e: HttpExchange, file: Path): Unit = e.run {
    checkLastModified(file.getLastModifiedTime().toInstant())
    responseHeaders += if (file.endsWith(indexFile)) indexHeaders else additionalHeaders
    var contentType = MimeTypes.typeFor(file)
    if (contentType == null) {
      log.warn("Cannot detect content-type for $file")
      contentType = MimeTypes.unknown
    }

    headerModifier(file)

    val gzFile = Path.of("$file.gz")
    val fileToSend = if (header("Accept-Encoding")?.contains("gzip") == true && gzFile.exists()) {
      header("Content-Encoding", "gzip")
      gzFile
    } else file

    val out = startResponse(OK, fileToSend.fileSize().takeIf { it <= useChunkedResponseForFilesLargerThan }, contentType)
    fileToSend.inputStream(READ).use { it.transferTo(out) }
  }
}
