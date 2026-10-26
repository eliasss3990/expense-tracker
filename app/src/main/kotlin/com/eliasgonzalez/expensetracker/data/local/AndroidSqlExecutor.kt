package com.eliasgonzalez.expensetracker.data.local

import android.database.sqlite.SQLiteDatabase

/** Adaptador de SqlExecutor sobre la base SQLite de Android. */
internal class AndroidSqlExecutor(private val db: SQLiteDatabase) : SqlExecutor {
    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.toTypedArray())
    }
}
