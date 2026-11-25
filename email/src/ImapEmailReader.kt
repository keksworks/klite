package klite.email

import klite.Config
import klite.info
import klite.logger
import java.util.*
import javax.mail.*
import javax.mail.Flags.Flag.DELETED
import javax.mail.Flags.Flag.SEEN
import javax.mail.Folder.*
import javax.mail.search.FlagTerm
import javax.mail.search.SearchTerm

open class ImapEmailReader(
  imapUser: String = Config.required("IMAP_USER"),
  props: Properties = Properties().also {
    it["mail.store.protocol"] = "imaps"
    it["mail.imaps.host"] = Config.required("IMAP_HOST")
    it["mail.imaps.port"] = Config.optional("IMAP_PORT", "993")
    it["mail.imaps.ssl.protocols"] = "TLSv1.2"
    it["mail.imaps.auth"] = true
  },
  private val authenticator: Authenticator = object: Authenticator() {
    override fun getPasswordAuthentication() = PasswordAuthentication(imapUser, Config.required("IMAP_PASS"))
  },
  private val session: Session = Session.getInstance(props, authenticator),
  private val folderName: String = Config.optional("IMAP_FOLDER", "INBOX"),
) {
  private val log = logger()

  companion object {
    val unseen = FlagTerm(Flags(SEEN), false)
    val notDeleted = FlagTerm(Flags(DELETED), false)
  }

  /** Fetches messages without marking them as read */
  fun fetch(term: SearchTerm = unseen): List<EmailMessage> = useFolder {
    search(term).map { it.toEmailMessage() }
  }

  /**
   * Processes messages one by one, running [mark] on each message after [handler] returns successfully.
   * The default marks the message as seen, deleted messages are expunged once all of them are processed.
   */
  fun process(term: SearchTerm = unseen, mark: (Message) -> Unit = { it.markSeen() }, handler: (EmailMessage) -> Unit) = useFolder(READ_WRITE) {
    val messages = search(term)
    messages.forEach {
      val email = it.toEmailMessage()
      handler(email)
      mark(it)
      log.info("Processed email ${email.id ?: email.subject} from ${email.from}")
    }
    if (messages.any { it.isSet(DELETED) }) expunge()
  }

  /** Processes not deleted messages, deleting each of them after [handler] returns successfully */
  fun processAndDelete(handler: (EmailMessage) -> Unit) = process(notDeleted, { it.delete() }, handler)

  /** Processes not deleted messages, moving each of them to [targetFolder] after [handler] returns successfully */
  fun processAndMove(targetFolder: String, handler: (EmailMessage) -> Unit) {
    var target: Folder? = null
    process(notDeleted, {
      if (target == null) {
        target = it.folder.store.getFolder(targetFolder)
        if (!target.exists()) target.create(HOLDS_MESSAGES)
      }
      it.moveTo(target)
    }, handler)
  }

  /** Opens [folderName] with the given [mode], passing it to [block], then closes everything */
  fun <T> useFolder(mode: Int = READ_ONLY, block: Folder.() -> T): T {
    val store = session.store
    store.connect()
    return store.use { store ->
      store.getFolder(folderName).use { folder ->
        folder.open(mode)
        folder.block()
      }
    }
  }
}

fun Message.markSeen() = setFlag(SEEN, true)
fun Message.delete() = setFlag(DELETED, true)
fun Message.moveTo(target: Folder) = folder.copyMessages(arrayOf(this), target).also { delete() }
