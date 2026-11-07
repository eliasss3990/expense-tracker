package com.eliasgonzalez.expensetracker.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteStatement

/** Adaptador de [SqlDatabase] sobre la base SQLite de Android (a traves de DbHelper). */
internal class AndroidSqlDatabase(private val dbHelper: DbHelper) : SqlDatabase {
    private val writable get() = dbHelper.writableDatabase

    override fun exec(sql: String, args: List<Any?>) = AndroidSqlExecutor(writable).exec(sql, args)

    override fun insert(sql: String, args: List<Any?>): Long =
        writable.compileStatement(sql).use { it.bindAll(args).executeInsert() }

    override fun update(sql: String, args: List<Any?>): Int =
        writable.compileStatement(sql).use { it.bindAll(args).executeUpdateDelete() }

    override fun <T> query(sql: String, args: List<Any?>, map: (SqlRow) -> T): List<T> =
        writable.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { cursor ->
            val row = CursorRow(cursor)
            buildList { while (cursor.moveToNext()) add(map(row)) }
        }

    private fun SQLiteStatement.bindAll(args: List<Any?>): SQLiteStatement {
        args.forEachIndexed { i, a ->
            val index = i + 1
            when (a) {
                null -> bindNull(index)
                is Long -> bindLong(index, a)
                is Int -> bindLong(index, a.toLong())
                is Boolean -> bindLong(index, if (a) 1 else 0)
                else -> bindString(index, a.toString())
            }
        }
        return this
    }

    private class CursorRow(private val cursor: Cursor) : SqlRow {
        private fun index(column: String) = cursor.getColumnIndexOrThrow(column)
        override fun long(column: String) = cursor.getLong(index(column))
        override fun longOrNull(column: String) = index(column).let { if (cursor.isNull(it)) null else cursor.getLong(it) }
        override fun string(column: String) = cursor.getString(index(column))
        override fun stringOrNull(column: String) = index(column).let { if (cursor.isNull(it)) null else cursor.getString(it) }
    }
}
