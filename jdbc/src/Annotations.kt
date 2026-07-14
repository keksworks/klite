package klite.jdbc

import kotlin.annotation.AnnotationTarget.PROPERTY

/** Use to override the column name in the database */
@Target(PROPERTY) annotation class Column(val name: String)

/** Auto-map value to a json column in the database */
@Target(PROPERTY) annotation class JsonColumn

/** Flatten object's properties as separate columns */
@Target(PROPERTY) annotation class FlattenColumns
