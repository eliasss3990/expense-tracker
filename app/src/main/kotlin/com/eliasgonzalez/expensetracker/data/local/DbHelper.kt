package com.eliasgonzalez.expensetracker.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults

/**
 * Persistencia via SQLite directo, no Room.
 *
 * Motivo: Room 2.8.x requiere KSP para el codegen de @Entity/@Dao, y al
 * momento de escribir esto KSP todavia no publico soporte para Kotlin
 * 2.4.10 (el que usa este proyecto via el Kotlin embebido de AGP 9). En
 * vez de bajar la version de Kotlin del proyecto entero por una sola
 * libreria, se implementa la persistencia a mano detras de las mismas
 * interfaces de repositorio del dominio (ExpenseRepository,
 * CandidateRepository, ActivityRepository) - el dominio y los casos de
 * uso no se enteran de este detalle. Migrar a Room despues, cuando KSP lo
 * soporte, no deberia tocar una sola linea fuera de data/local.
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DbSchema.VERSION) {

    // El esquema y las migraciones viven en DbSchema (Kotlin puro, probado en la JVM
    // contra una SQLite real en DbMigrationTest). Este helper solo se los pasa.
    override fun onCreate(db: SQLiteDatabase) {
        // Instalacion nueva: lo vigente. (La migracion v3 usa su propia lista congelada.)
        DbSchema.create(AndroidSqlExecutor(db), CatalogDefaults.categories)
    }

    // INCIDENTE REAL (2026-08-31): el "sin usuarios reales todavia" de la
    // version anterior de este comentario era falso -- el DB_VERSION 1->2
    // (agregar `description` a `candidates`) corrio contra la base de un
    // usuario real y el DROP TABLE de mas abajo (que YA NO EXISTE, esto
    // documenta que paso) le borro todos los gastos guardados. Nunca mas:
    // de aca en adelante las migraciones SOLO suman (ver DbSchema.migrate),
    // nunca dropean nada, y un test lo comprueba.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DbSchema.migrate(AndroidSqlExecutor(db), oldVersion)
    }

    // Instalar sobre una base MAS NUEVA (ej. probar un hotfix o una rama con
    // DB_VERSION menor sobre datos ya en v3) lanzaria SQLiteException en cada
    // arranque y solo dejaria borrar los datos. Como las migraciones son SOLO
    // aditivas, la version vieja ignora las tablas/columnas que no conoce, asi
    // que tolerar el downgrade es seguro.
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private companion object {
        const val DB_NAME = "expense_tracker.db"
    }
}
