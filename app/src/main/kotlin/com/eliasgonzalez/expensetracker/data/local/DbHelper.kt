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
class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount INTEGER NOT NULL,
                currency TEXT NOT NULL,
                merchant TEXT NOT NULL,
                category_id TEXT NOT NULL,
                description TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                source TEXT NOT NULL,
                source_reference INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE candidates (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount INTEGER NOT NULL,
                currency TEXT NOT NULL,
                merchant TEXT NOT NULL,
                category_suggestion TEXT NOT NULL,
                description TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                detected_at INTEGER NOT NULL,
                source_type TEXT NOT NULL,
                source_app TEXT,
                parser_id TEXT,
                confidence REAL NOT NULL,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE activity_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                expense_id INTEGER,
                candidate_id INTEGER,
                timestamp INTEGER NOT NULL,
                summary TEXT NOT NULL
            )
            """.trimIndent()
        )
        createCatalogTable(db)
        seedCatalog(db)
    }

    // `IF NOT EXISTS` + CONFLICT_IGNORE: corre en una instalacion nueva y en
    // la migracion v2->v3, y es segura de repetir: NUNCA reemplaza ni
    // borra entradas existentes (renombres, desactivadas, propias).
    //
    // OJO: llamarla SOLO desde onCreate y el bloque `oldVersion < 3`. Si
    // una migracion futura la reusara, volveria a sembrar una entrada de
    // fabrica que el usuario ya hubiera eliminado, y como lee el
    // CatalogDefaults ACTUAL el resultado dependeria de la version de la
    // app que hace el upgrade. Una migracion futura que necesite sembrar
    // algo nuevo debe llevar su propia lista congelada.
    private fun createCatalogTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalog_entries (
                kind TEXT NOT NULL,
                id TEXT NOT NULL,
                label TEXT NOT NULL,
                color TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                sort_order INTEGER NOT NULL DEFAULT 0,
                built_in INTEGER NOT NULL DEFAULT 0,
                protected INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (kind, id)
            )
            """.trimIndent()
        )
    }

    private fun seedCatalog(db: SQLiteDatabase) {
        CatalogDefaults.categories.forEach { entry ->
            db.insertWithOnConflict(
                "catalog_entries", null, entry.toContentValues(), SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    // INCIDENTE REAL (2026-08-31): el "sin usuarios reales todavia" de la
    // version anterior de este comentario era falso -- el DB_VERSION 1->2
    // (agregar `description` a `candidates`) corrio contra la base de un
    // usuario real y el DROP TABLE de mas abajo (que YA NO EXISTE, esto
    // documenta que paso) le borro todos los gastos guardados. Nunca mas:
    // de aca en adelante, onUpgrade SIEMPRE hace un ALTER TABLE real por
    // cada version intermedia, nunca dropea nada. Al agregar la proxima
    // migracion, sumar un bloque `if (oldVersion < N) { ... }` nuevo -- no
    // reemplazar los anteriores, para que actualizar desde CUALQUIER
    // version vieja instalada en un celular real siga funcionando.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE candidates ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            createCatalogTable(db)
            seedCatalog(db)
        }
    }

    companion object {
        private const val DB_NAME = "expense_tracker.db"
        // v2: agrega `description` a `candidates` (paridad con `expenses`,
        // que ya la tenia) -- ver onUpgrade, es un ALTER TABLE real.
        // v3: agrega `catalog_entries` (catalogo gestionable, ver
        // plan-catalogo-gestionable.md) y la siembra desde CatalogDefaults.
        // Migracion solo aditiva: no toca expenses/candidates/activity_log.
        private const val DB_VERSION = 3
    }
}
