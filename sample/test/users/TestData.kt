package klite.sample.users

import klite.Email
import users.Id
import users.User
import users.User.Address
import users.User.Name
import java.util.*

object TestData {
  val user = User(Email("john@doe.com"), Name("John", "Doe"), Locale.ENGLISH, "hash", null, Id("4024d2bc-ebb1-11ef-b470-0fea3b49cda0"))
  val address = Address(user.id!!, "Tallinn", "EE", Id("a7f033bc-04f3-4cc0-94f1-564783cab08f"))
}
