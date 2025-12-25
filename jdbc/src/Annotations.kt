package klite.jdbc

import kotlin.annotation.AnnotationTarget.PROPERTY

/** Use to override the column name in the database table */
@Target(PROPERTY) annotation class Column(val name: String)
