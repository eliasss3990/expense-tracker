package com.eliasgonzalez.expensetracker.data.local

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

/** SqlDatabase sobre una SQLite real (JDBC) para probar los repositorios en la JVM, sin emulador. */
internal class JdbcSqlDatabase(private val conn: Connection) : SqlDatabase {
    override fun exec(sql: String, args: List<Any?>) {
        conn.prepareStatement(sql).use { st -> bind(st, args); st.executeUpdate() }
    }

    override fun insert(sql: String, args: List<Any?>): Long =
        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { st ->
            bind(st, args)
            st.executeUpdate()
            st.generatedKeys.use { keys -> keys.next(); keys.getLong(1) }
        }

    override fun update(sql: String, args: List<Any?>): Int =
        conn.prepareStatement(sql).use { st -> bind(st, args); st.executeUpdate() }

    override fun <T> query(sql: String, args: List<Any?>, map: (SqlRow) -> T): List<T> =
        conn.prepareStatement(sql).use { st ->
            bind(st, args)
            st.executeQuery().use { rs ->
                val row = ResultSetRow(rs)
                buildList { while (rs.next()) add(map(row)) }
            }
        }

    private fun bind(st: java.sql.PreparedStatement, args: List<Any?>) =
        args.forEachIndexed { i, a -> st.setObject(i + 1, a) }

    private class ResultSetRow(private val rs: ResultSet) : SqlRow {
        override fun long(column: String) = rs.getLong(column)
        override fun longOrNull(column: String) = rs.getLong(column).let { if (rs.wasNull()) null else it }
        override fun string(column: String): String = rs.getString(column)
        override fun stringOrNull(column: String): String? = rs.getString(column)
    }
}
