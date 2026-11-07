package com.eliasgonzalez.expensetracker.data.local

/** Fila de un resultado, accedida por nombre de columna. Sin Android: se prueba en la JVM. */
internal interface SqlRow {
    fun long(column: String): Long
    fun longOrNull(column: String): Long?
    fun string(column: String): String
    fun stringOrNull(column: String): String?
}

/**
 * Lo minimo que necesitan los repositorios SQL nuevos (cola y auditoria del analisis): insertar con
 * id, actualizar y consultar. Extiende [SqlExecutor] sin cambiarlo. La implementacion Android usa
 * SQLiteDatabase; la de los tests, JDBC contra una SQLite real.
 */
internal interface SqlDatabase : SqlExecutor {
    fun insert(sql: String, args: List<Any?> = emptyList()): Long
    fun update(sql: String, args: List<Any?> = emptyList()): Int
    fun <T> query(sql: String, args: List<Any?> = emptyList(), map: (SqlRow) -> T): List<T>
}
