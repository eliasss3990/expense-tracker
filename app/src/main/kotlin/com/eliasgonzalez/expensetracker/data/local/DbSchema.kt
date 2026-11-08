package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry

/**
 * Lo unico que el esquema necesita de una base de datos: ejecutar una sentencia
 * con parametros. En la app la implementa SQLite de Android (AndroidSqlExecutor);
 * en los tests, una base SQLite REAL en memoria de la JVM. Asi las mismas
 * sentencias de creacion y migracion que corren en el celular se prueban sin
 * emulador.
 */
internal interface SqlExecutor {
    fun exec(sql: String, args: List<Any?> = emptyList())
}

/**
 * Esquema y migraciones de la base, en Kotlin puro (sin Android) para poder
 * probarlos en la JVM. DbHelper solo los invoca.
 *
 * INCIDENTE REAL (2026-08-31): el onUpgrade 1->2 de una version anterior hacia
 * DROP TABLE y le borro a un usuario real todos los gastos guardados (ADR 0005).
 * Regla: las migraciones son SOLO ADITIVAS. Nunca DROP, DELETE ni
 * INSERT OR REPLACE sobre datos existentes; DbMigrationTest lo verifica
 * registrando cada sentencia que ejecuta. Una migracion nueva se suma como un
 * bloque `if (oldVersion < N)` mas, sin reemplazar los anteriores, para que
 * actualizar desde CUALQUIER version vieja siga funcionando.
 */
internal object DbSchema {

    const val VERSION = 4

    private val CREATE_EXPENSES = """
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

    /**
     * Cola persistente de avisos pendientes de analisis (v4). Guarda el texto de la notificacion
     * (necesario para reintentar y para reconstruir una decision) con retencion acotada: ver
     * AnalysisQueueRepository.pruneBefore. Sin ninguna columna propia de un modelo concreto.
     */
    private val CREATE_ANALYSIS_QUEUE = """
        CREATE TABLE IF NOT EXISTS analysis_queue (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            event_key TEXT NOT NULL UNIQUE,
            package_name TEXT NOT NULL,
            application_name TEXT NOT NULL,
            title TEXT NOT NULL,
            text TEXT NOT NULL,
            big_text TEXT NOT NULL,
            notified_at INTEGER NOT NULL,
            parser_kind TEXT NOT NULL,
            parser_id TEXT,
            parser_amount INTEGER,
            parser_currency TEXT,
            parser_merchant TEXT,
            parser_merchant_confident INTEGER,
            trust TEXT NOT NULL,
            state TEXT NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            last_error TEXT,
            enqueued_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """.trimIndent()

    private val CREATE_ANALYSIS_QUEUE_INDEX =
        "CREATE INDEX IF NOT EXISTS idx_analysis_queue_state ON analysis_queue (state, id)"

    /** Por que cada aviso termino como termino (v4): codigos y la interpretacion versionada en JSON. */
    private val CREATE_DECISION_AUDIT = """
        CREATE TABLE IF NOT EXISTS decision_audit (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            event_key TEXT NOT NULL,
            decided_at INTEGER NOT NULL,
            mode TEXT NOT NULL,
            source_app TEXT NOT NULL,
            outcome TEXT,
            reasons TEXT NOT NULL,
            effect TEXT NOT NULL,
            expense_id INTEGER,
            candidate_id INTEGER,
            interpretations TEXT NOT NULL,
            error_class TEXT,
            category_id TEXT,
            category_source TEXT
        )
    """.trimIndent()

    private val CREATE_DECISION_AUDIT_INDEX =
        "CREATE INDEX IF NOT EXISTS idx_decision_audit_decided_at ON decision_audit (decided_at)"

    private val CREATE_CANDIDATES = """
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

    private val CREATE_ACTIVITY_LOG = """
        CREATE TABLE activity_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL,
            expense_id INTEGER,
            candidate_id INTEGER,
            timestamp INTEGER NOT NULL,
            summary TEXT NOT NULL
        )
    """.trimIndent()

    // `IF NOT EXISTS` + INSERT OR IGNORE: corre en una instalacion nueva y en la
    // migracion v2->v3, y es segura de repetir: NUNCA reemplaza ni borra entradas
    // existentes (renombres, desactivadas, propias).
    private val CREATE_CATALOG = """
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

    private const val INSERT_CATALOG_IGNORING_EXISTING =
        "INSERT OR IGNORE INTO catalog_entries " +
            "(kind, id, label, color, active, sort_order, built_in, protected) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

    /** Instalacion nueva. [catalogSeed] es lo vigente (CatalogDefaults). */
    fun create(db: SqlExecutor, catalogSeed: List<CatalogEntry>) {
        db.exec(CREATE_EXPENSES)
        db.exec(CREATE_CANDIDATES)
        db.exec(CREATE_ACTIVITY_LOG)
        db.exec(CREATE_CATALOG)
        createAnalysisTables(db)
        seedCatalog(db, catalogSeed)
    }

    /**
     * Sube una base desde [oldVersion] hasta [VERSION], un bloque por version, solo
     * aditivo. La migracion v3 siembra CatalogSeedV3, una lista CONGELADA: como
     * CatalogDefaults cambia con el codigo, usarlo aca haria que el resultado del
     * upgrade dependiera de la version de la app y volveria a sembrar una categoria
     * de fabrica que el usuario ya hubiera eliminado. Una migracion futura debe
     * llevar SU PROPIA lista congelada.
     */
    fun migrate(db: SqlExecutor, oldVersion: Int) {
        if (oldVersion < 2) {
            // v2: `description` en candidates (paridad con expenses, que ya la tenia).
            db.exec("ALTER TABLE candidates ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            // v3: catalogo gestionable. No toca expenses/candidates/activity_log.
            db.exec(CREATE_CATALOG)
            seedCatalog(db, CatalogSeedV3.categories)
        }
        if (oldVersion < 4) {
            // v4: cola y auditoria del analisis de notificaciones. Solo tablas nuevas.
            createAnalysisTables(db)
        }
    }

    private fun createAnalysisTables(db: SqlExecutor) {
        db.exec(CREATE_ANALYSIS_QUEUE)
        db.exec(CREATE_ANALYSIS_QUEUE_INDEX)
        db.exec(CREATE_DECISION_AUDIT)
        db.exec(CREATE_DECISION_AUDIT_INDEX)
    }

    private fun seedCatalog(db: SqlExecutor, entries: List<CatalogEntry>) {
        entries.forEach { entry ->
            db.exec(
                INSERT_CATALOG_IGNORING_EXISTING,
                listOf(
                    entry.kind, entry.id, entry.label, entry.color,
                    if (entry.active) 1 else 0, entry.sortOrder,
                    if (entry.builtIn) 1 else 0, if (entry.protected) 1 else 0,
                ),
            )
        }
    }
}
