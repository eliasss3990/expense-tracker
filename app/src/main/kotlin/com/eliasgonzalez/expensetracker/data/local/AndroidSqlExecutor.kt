package com.eliasgonzalez.expensetracker.data.local

import android.database.sqlite.SQLiteDatabase

/** Adaptador de SqlExecutor sobre la base SQLite de Android. */
internal class AndroidSqlExecutor(private val db: SQLiteDatabase) : SqlExecutor {
    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.toTypedArray())
    }

    override fun hasColumn(tableName: String, columnName: String): Boolean {
        require(tableName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))
        db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == columnName) return true
            }
        }
        return false
    }
}
