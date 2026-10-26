package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Prueba el esquema y las migraciones de DbSchema contra una SQLite REAL en
 * memoria (la misma sentencia que corre en el celular, sin emulador).
 *
 * Existe por el incidente del 2026-08-31 (ADR 0005): una migracion con DROP TABLE
 * le borro a un usuario real todos sus gastos. Estos tests son la red de
 * seguridad automatica: una migracion que pierda o pise datos, o que deje la base
 * con un esquema distinto al de una instalacion nueva, falla aca antes de llegar
 * a un celular.
 */
class DbMigrationTest {

    private lateinit var conn: Connection

    @Before
    fun openInMemoryDb() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun closeDb() = conn.close()

    private class JdbcSqlExecutor(private val conn: Connection) : SqlExecutor {
        val executed = mutableListOf<String>()

        override fun exec(sql: String, args: List<Any?>) {
            executed += sql
            conn.prepareStatement(sql).use { st ->
                args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
                st.executeUpdate()
            }
        }
    }

    private fun executor() = JdbcSqlExecutor(conn)

    // --- esquemas HISTORICOS, escritos aca a mano a proposito -----------------
    // No se derivan de DbSchema: si DbSchema cambiara la definicion de v1/v2 por error,
    // este fixture seguiria describiendo la base REAL que tienen los usuarios.

    private val v1Expenses = """
        CREATE TABLE expenses (
            id INTEGER PRIMARY KEY AUTOINCREMENT, amount INTEGER NOT NULL, currency TEXT NOT NULL,
            merchant TEXT NOT NULL, category_id TEXT NOT NULL, description TEXT NOT NULL,
            occurred_at INTEGER NOT NULL, created_at INTEGER NOT NULL, source TEXT NOT NULL,
            source_reference INTEGER)
    """.trimIndent()
    private val v1Candidates = """
        CREATE TABLE candidates (
            id INTEGER PRIMARY KEY AUTOINCREMENT, amount INTEGER NOT NULL, currency TEXT NOT NULL,
            merchant TEXT NOT NULL, category_suggestion TEXT NOT NULL, occurred_at INTEGER NOT NULL,
            detected_at INTEGER NOT NULL, source_type TEXT NOT NULL, source_app TEXT, parser_id TEXT,
            confidence REAL NOT NULL, status TEXT NOT NULL)
    """.trimIndent()
    private val activityLog = """
        CREATE TABLE activity_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, expense_id INTEGER,
            candidate_id INTEGER, timestamp INTEGER NOT NULL, summary TEXT NOT NULL)
    """.trimIndent()

    private fun sql(statement: String) = conn.createStatement().use { it.execute(statement) }

    private fun createV1WithData() {
        sql(v1Expenses); sql(v1Candidates); sql(activityLog)
        sql("INSERT INTO expenses VALUES (1, 85000, 'PYG', 'Shell', 'FUEL', 'nafta', 100, 101, 'MANUAL', NULL)")
        sql("INSERT INTO expenses VALUES (2, 12000, 'PYG', 'Panadería', 'FOOD', '', 200, 201, 'NOTIFICATION', 7)")
        sql("INSERT INTO candidates VALUES (1, 50000, 'PYG', 'Ueno', 'OTHER', 300, 301, 'NOTIFICATION', 'com.ueno', 'ueno', 0.9, 'PENDING')")
        sql("INSERT INTO activity_log VALUES (1, 'EXPENSE_CREATED', 1, NULL, 102, 'Shell')")
    }

    /** v2 = v1 + `description` en candidates (el ALTER que ya corrio en los celulares). */
    private fun createV2WithData() {
        createV1WithData()
        sql("ALTER TABLE candidates ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        sql("UPDATE candidates SET description = 'detectado' WHERE id = 1")
    }

    private fun scalar(query: String): Any? = conn.createStatement().use { st ->
        st.executeQuery(query).use { rs -> if (rs.next()) rs.getObject(1) else null }
    }

    private fun rows(query: String): List<List<Any?>> = conn.createStatement().use { st ->
        st.executeQuery(query).use { rs ->
            val cols = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..cols).map { rs.getObject(it) }) }
        }
    }

    /**
     * Columnas como (nombre, tipo, notnull, pk), ORDENADAS POR NOMBRE y sin el default.
     * Dos diferencias son inevitables y no afectan a la app, que lee por nombre de columna:
     * (1) ALTER TABLE ... ADD COLUMN siempre agrega AL FINAL, asi que en una base migrada
     * `candidates.description` queda ultima y en una nueva queda en el medio; (2) en la
     * migrada `description` tiene DEFAULT '' (necesario para el ALTER) y en la nueva no.
     */
    private fun columns(table: String): List<List<Any?>> =
        rows("PRAGMA table_info($table)")
            .map { listOf(it[1], it[2], it[3], it[5]) }
            .sortedBy { it[0] as String }

    private fun catalogFromDb(): List<CatalogEntry> =
        rows("SELECT kind, id, label, color, active, sort_order, built_in, protected FROM catalog_entries ORDER BY sort_order")
            .map {
                CatalogEntry(
                    kind = it[0] as String, id = it[1] as String, label = it[2] as String, color = it[3] as String,
                    active = (it[4] as Number).toInt() == 1, sortOrder = (it[5] as Number).toInt(),
                    builtIn = (it[6] as Number).toInt() == 1, protected = (it[7] as Number).toInt() == 1,
                )
            }

    // --- v2 -> v3 con datos reales ---------------------------------------------

    @Test
    fun `migrar de v2 a v3 conserva TODOS los gastos, candidatos y actividad`() {
        createV2WithData()
        val gastosAntes = rows("SELECT * FROM expenses ORDER BY id")
        val candidatosAntes = rows("SELECT * FROM candidates ORDER BY id")
        val actividadAntes = rows("SELECT * FROM activity_log ORDER BY id")

        DbSchema.migrate(executor(), oldVersion = 2)

        assertEquals(gastosAntes, rows("SELECT * FROM expenses ORDER BY id"))
        assertEquals(candidatosAntes, rows("SELECT * FROM candidates ORDER BY id"))
        assertEquals(actividadAntes, rows("SELECT * FROM activity_log ORDER BY id"))
        assertEquals(2L, (scalar("SELECT COUNT(*) FROM expenses") as Number).toLong())
    }

    @Test
    fun `migrar de v2 a v3 crea y siembra el catalogo con la lista congelada`() {
        createV2WithData()

        DbSchema.migrate(executor(), oldVersion = 2)

        assertEquals(CatalogSeedV3.categories, catalogFromDb())
    }

    @Test
    fun `migrar dos veces no duplica el catalogo (idempotente)`() {
        createV2WithData()

        DbSchema.migrate(executor(), oldVersion = 2)
        DbSchema.migrate(executor(), oldVersion = 2)

        assertEquals(11L, (scalar("SELECT COUNT(*) FROM catalog_entries") as Number).toLong())
    }

    @Test
    fun `repetir la migracion NO pisa lo que el usuario ya configuro en el catalogo`() {
        createV2WithData()
        DbSchema.migrate(executor(), oldVersion = 2)
        // El usuario renombra una de fabrica, desactiva otra, borra otra y crea una propia.
        sql("UPDATE catalog_entries SET label = 'Restaurantes' WHERE id = 'FOOD'")
        sql("UPDATE catalog_entries SET active = 0 WHERE id = 'FUEL'")
        sql("INSERT INTO catalog_entries VALUES ('category', 'uuid-1', 'Mascotas', '#A855F7', 1, 11, 0, 0)")

        DbSchema.migrate(executor(), oldVersion = 2)

        assertEquals("Restaurantes", scalar("SELECT label FROM catalog_entries WHERE id = 'FOOD'"))
        assertEquals(0L, (scalar("SELECT active FROM catalog_entries WHERE id = 'FUEL'") as Number).toLong())
        assertEquals("Mascotas", scalar("SELECT label FROM catalog_entries WHERE id = 'uuid-1'"))
    }

    // --- v1 -> v3 (salto de dos versiones) -------------------------------------

    @Test
    fun `migrar de v1 a v3 agrega description a los candidatos existentes sin perder nada`() {
        createV1WithData()
        val gastosAntes = rows("SELECT * FROM expenses ORDER BY id")

        DbSchema.migrate(executor(), oldVersion = 1)

        assertEquals(gastosAntes, rows("SELECT * FROM expenses ORDER BY id"))
        // el candidato existente conserva sus datos y recibe description vacia
        assertEquals("", scalar("SELECT description FROM candidates WHERE id = 1"))
        assertEquals(50000L, (scalar("SELECT amount FROM candidates WHERE id = 1") as Number).toLong())
        assertEquals(CatalogSeedV3.categories, catalogFromDb())
    }

    // --- misma base que una instalacion nueva ----------------------------------

    @Test
    fun `una base migrada tiene el mismo esquema que una instalacion nueva`() {
        // Clasico bug de migraciones: onCreate y onUpgrade dejan esquemas distintos.
        val migrada = DriverManager.getConnection("jdbc:sqlite::memory:")
        val nueva = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            val original = conn
            conn = migrada; createV2WithData(); DbSchema.migrate(executor(), oldVersion = 2)
            val esquemaMigrada = listOf("expenses", "candidates", "activity_log", "catalog_entries").associateWith { columns(it) }
            conn = nueva; DbSchema.create(executor(), CatalogDefaults.categories)
            val esquemaNueva = listOf("expenses", "candidates", "activity_log", "catalog_entries").associateWith { columns(it) }
            conn = original

            assertEquals(esquemaNueva, esquemaMigrada)
        } finally {
            migrada.close(); nueva.close()
        }
    }

    @Test
    fun `una instalacion nueva crea las cuatro tablas y siembra las categorias vigentes`() {
        DbSchema.create(executor(), CatalogDefaults.categories)

        val tablas = rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
            .map { it[0] }
        assertEquals(listOf("activity_log", "candidates", "catalog_entries", "expenses"), tablas)
        assertEquals(CatalogDefaults.categories, catalogFromDb())
    }

    // --- la regla del ADR 0005: solo aditivo -----------------------------------

    private val destructive = Regex("""\b(DROP|DELETE|TRUNCATE|REPLACE)\b""", RegexOption.IGNORE_CASE)

    @Test
    fun `ninguna migracion ejecuta una sentencia destructiva (ADR 0005)`() {
        for (oldVersion in 1 until DbSchema.VERSION) {
            conn.close(); conn = DriverManager.getConnection("jdbc:sqlite::memory:")
            if (oldVersion == 1) createV1WithData() else createV2WithData()
            val ejecutor = executor()

            DbSchema.migrate(ejecutor, oldVersion)

            val peligrosas = ejecutor.executed.filter { destructive.containsMatchIn(it) }
            assertTrue("migrar desde v$oldVersion ejecuto sentencias destructivas: $peligrosas", peligrosas.isEmpty())
        }
    }

    @Test
    fun `crear la base tampoco ejecuta sentencias destructivas`() {
        val ejecutor = executor()

        DbSchema.create(ejecutor, CatalogDefaults.categories)

        assertTrue(ejecutor.executed.none { destructive.containsMatchIn(it) })
    }

    @Test
    fun `se puede migrar desde cada version anterior y no hay un hueco entre versiones`() {
        // Si alguien sube VERSION a 4 y agrega el bloque `oldVersion < 4` pero se olvida de
        // que una base v1 tambien debe pasar por el, este test (que recorre todas) lo ve.
        for (oldVersion in 1 until DbSchema.VERSION) {
            conn.close(); conn = DriverManager.getConnection("jdbc:sqlite::memory:")
            if (oldVersion == 1) createV1WithData() else createV2WithData()

            DbSchema.migrate(executor(), oldVersion)

            assertFalse("v$oldVersion no quedo con catalogo", rows("SELECT id FROM catalog_entries").isEmpty())
            assertEquals(
                "v$oldVersion perdio gastos",
                2L, (scalar("SELECT COUNT(*) FROM expenses") as Number).toLong(),
            )
        }
    }

    @Test
    fun `la version del esquema es la 3 (si cambia, hay que agregar su bloque y su test)`() {
        assertEquals(3, DbSchema.VERSION)
    }
}
