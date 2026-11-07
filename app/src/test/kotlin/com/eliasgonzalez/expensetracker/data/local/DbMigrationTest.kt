package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier
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
 *
 * Como en Android, create/migrate corren dentro de UNA transaccion
 * (SQLiteOpenHelper envuelve onCreate/onUpgrade): un fallo a mitad se revierte entero.
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

        override fun hasColumn(tableName: String, columnName: String): Boolean {
            require(tableName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))
            var found = false
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                    while (rs.next()) if (rs.getString(2) == columnName) found = true
                }
            }
            return found
        }
    }

    /** Lo que hace SQLiteOpenHelper: todo en una transaccion, commit si sale bien, rollback si lanza. */
    private fun inTransaction(block: () -> Unit) {
        conn.autoCommit = false
        try {
            block()
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    /** Migra como lo hace Android y devuelve las sentencias ejecutadas. */
    private fun migrate(oldVersion: Int): List<String> {
        val executor = JdbcSqlExecutor(conn)
        inTransaction { DbSchema.migrate(executor, oldVersion) }
        return executor.executed
    }

    private fun create(seed: List<CatalogEntry>): List<String> {
        val executor = JdbcSqlExecutor(conn)
        inTransaction { DbSchema.create(executor, seed) }
        return executor.executed
    }

    private fun <T> usingConnection(other: Connection, block: () -> T): T {
        val original = conn
        conn = other
        try {
            return block()
        } finally {
            conn = original
        }
    }

    private fun reopen() {
        conn.close()
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

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

    /** candidates tal como la crea onCreate en v2: description en el medio, SIN default. */
    private val v2CandidatesFromScratch = """
        CREATE TABLE candidates (
            id INTEGER PRIMARY KEY AUTOINCREMENT, amount INTEGER NOT NULL, currency TEXT NOT NULL,
            merchant TEXT NOT NULL, category_suggestion TEXT NOT NULL, description TEXT NOT NULL,
            occurred_at INTEGER NOT NULL, detected_at INTEGER NOT NULL, source_type TEXT NOT NULL,
            source_app TEXT, parser_id TEXT, confidence REAL NOT NULL, status TEXT NOT NULL)
    """.trimIndent()
    private val activityLog = """
        CREATE TABLE activity_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, expense_id INTEGER,
            candidate_id INTEGER, timestamp INTEGER NOT NULL, summary TEXT NOT NULL)
    """.trimIndent()

    private fun sql(statement: String) = conn.createStatement().use { it.execute(statement) }

    private fun insertCommonRows() {
        sql("INSERT INTO expenses VALUES (1, 85000, 'PYG', 'Shell', 'FUEL', 'nafta', 100, 101, 'MANUAL', NULL)")
        sql("INSERT INTO expenses VALUES (2, 12000, 'PYG', 'Panadería', 'FOOD', '', 200, 201, 'NOTIFICATION', 7)")
        sql("INSERT INTO activity_log VALUES (1, 'EXPENSE_CREATED', 1, NULL, 102, 'Shell')")
    }

    private fun createV1WithData() {
        sql(v1Expenses); sql(v1Candidates); sql(activityLog)
        insertCommonRows()
        sql("INSERT INTO candidates VALUES (1, 50000, 'PYG', 'Ueno', 'OTHER', 300, 301, 'NOTIFICATION', 'com.ueno', 'ueno', 0.9, 'PENDING')")
    }

    /**
     * v2 de una base que era v1 y paso por el ALTER (la mayoria de los celulares):
     * `description` queda AL FINAL y con DEFAULT ''.
     */
    private fun createV2ByAlterWithData() {
        createV1WithData()
        sql("ALTER TABLE candidates ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        sql("UPDATE candidates SET description = 'detectado' WHERE id = 1")
    }

    /** Esquema v3 real: v2 + catalogo, antes de agregar merchant_confident en v4. */
    private fun createV3WithData() {
        createV2ByAlterWithData()
        sql("""
            CREATE TABLE catalog_entries (
                kind TEXT NOT NULL, id TEXT NOT NULL, label TEXT NOT NULL, color TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1, sort_order INTEGER NOT NULL DEFAULT 0,
                built_in INTEGER NOT NULL DEFAULT 0, protected INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (kind, id)
            )
        """.trimIndent())
        CatalogSeedV3.categories.forEach { entry ->
            conn.prepareStatement(
                "INSERT OR IGNORE INTO catalog_entries " +
                    "(kind, id, label, color, active, sort_order, built_in, protected) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { st ->
                st.setString(1, entry.kind)
                st.setString(2, entry.id)
                st.setString(3, entry.label)
                st.setString(4, entry.color)
                st.setInt(5, if (entry.active) 1 else 0)
                st.setInt(6, entry.sortOrder)
                st.setInt(7, if (entry.builtIn) 1 else 0)
                st.setInt(8, if (entry.protected) 1 else 0)
                st.executeUpdate()
            }
        }
    }

    /** Esquema v4 real: v3 + merchant_confident, antes de la cola persistente v5. */
    private fun createV4WithData() {
        createV3WithData()
        sql("ALTER TABLE candidates ADD COLUMN merchant_confident INTEGER NOT NULL DEFAULT 1")
    }

    /** v2 de una base creada por onCreate (instalacion nueva o recreada tras el incidente): sin default. */
    private fun createV2FromScratchWithData() {
        sql(v1Expenses); sql(v2CandidatesFromScratch); sql(activityLog)
        insertCommonRows()
        sql("INSERT INTO candidates VALUES (1, 50000, 'PYG', 'Ueno', 'OTHER', 'detectado', 300, 301, 'NOTIFICATION', 'com.ueno', 'ueno', 0.9, 'PENDING')")
    }

    /** Las dos variantes REALES de v2 que existieron: cada prueba de v2->v5 corre sobre ambas. */
    private fun forEachV2Variant(block: (name: String) -> Unit) {
        listOf(
            "v2 migrada con ALTER" to ::createV2ByAlterWithData,
            "v2 creada por onCreate" to ::createV2FromScratchWithData,
        ).forEach { (name, create) ->
            reopen()
            create()
            block(name)
        }
    }

    private fun scalar(query: String): Any? = conn.createStatement().use { st ->
        st.executeQuery(query).use { rs -> if (rs.next()) rs.getObject(1) else null }
    }

    private fun count(query: String): Long = (scalar(query) as Number).toLong()

    private fun rows(query: String): List<List<Any?>> = conn.createStatement().use { st ->
        st.executeQuery(query).use { rs ->
            val cols = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..cols).map { rs.getObject(it) }) }
        }
    }

    private fun columnNames(table: String): Set<String> =
        rows("PRAGMA table_info($table)").map { it[1] as String }.toSet()

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

    private val schemaTables = listOf("expenses", "candidates", "activity_log", "catalog_entries", "notification_analysis_queue")

    // --- v2 -> v5 con datos reales, sobre las DOS variantes de v2 ----------------

    @Test
    fun `migrar de v2 a v5 conserva TODOS los gastos, candidatos y actividad`() {
        forEachV2Variant { variante ->
            val gastosAntes = rows("SELECT * FROM expenses ORDER BY id")
            val candidatosAntes = rows("SELECT * FROM candidates ORDER BY id")
            val actividadAntes = rows("SELECT * FROM activity_log ORDER BY id")

            migrate(2)

            assertEquals(variante, gastosAntes, rows("SELECT * FROM expenses ORDER BY id"))
            assertEquals(variante, candidatosAntes, rows("SELECT * FROM candidates ORDER BY id").map { it.dropLast(1) })
            assertEquals(variante, actividadAntes, rows("SELECT * FROM activity_log ORDER BY id"))
            assertEquals(variante, 2L, count("SELECT COUNT(*) FROM expenses"))
        }
    }

    @Test
    fun `migrar de v2 a v5 crea y siembra el catalogo con la lista congelada`() {
        forEachV2Variant { variante ->
            migrate(2)

            assertEquals(variante, CatalogSeedV3.categories, catalogFromDb())
        }
    }

    @Test
    fun `migrar de v2 a v5 siembra el catalogo una sola vez`() {
        forEachV2Variant { variante ->
            migrate(2)

            assertEquals(variante, 11L, count("SELECT COUNT(*) FROM catalog_entries"))
            assertTrue("merchant_confident no fue agregada", "merchant_confident" in columnNames("candidates"))
        }
    }

    @Test
    fun `migrar dos veces no duplica el catalogo ni vuelve a agregar columnas`() {
        forEachV2Variant { variante ->
            migrate(2)
            migrate(2)

            assertEquals(variante, 11L, count("SELECT COUNT(*) FROM catalog_entries"))
            assertEquals(variante, 1L, count("SELECT COUNT(*) FROM pragma_table_info('candidates') WHERE name = 'merchant_confident'"))
        }
    }

    @Test
    fun `repetir la migracion NO pisa lo que el usuario renombro, desactivo o creo`() {
        forEachV2Variant { variante ->
            migrate(2)
            sql("UPDATE catalog_entries SET label = 'Restaurantes' WHERE id = 'FOOD'")
            sql("UPDATE catalog_entries SET active = 0 WHERE id = 'FUEL'")
            sql("INSERT INTO catalog_entries VALUES ('category', 'uuid-1', 'Mascotas', '#A855F7', 1, 11, 0, 0)")

            migrate(2)

            assertEquals(variante, "Restaurantes", scalar("SELECT label FROM catalog_entries WHERE id = 'FOOD'"))
            assertEquals(variante, 0L, count("SELECT active FROM catalog_entries WHERE id = 'FUEL'"))
            assertEquals(variante, "Mascotas", scalar("SELECT label FROM catalog_entries WHERE id = 'uuid-1'"))
        }
    }

    @Test
    fun `COMPORTAMIENTO CONOCIDO - repetir la migracion re-siembra una categoria de fabrica que el usuario elimino`() {
        createV2ByAlterWithData()
        migrate(2)
        sql("DELETE FROM catalog_entries WHERE id = 'MECHANIC'")
        assertEquals(10L, count("SELECT COUNT(*) FROM catalog_entries"))

        migrate(2)

        assertEquals(11L, count("SELECT COUNT(*) FROM catalog_entries"))
    }

    // --- migraciones historicas v1/v2/v3/v4 -> v5 ------------------------------------

    @Test
    fun `migrar de v3 a v5 agrega merchant_confident y cola sin alterar los datos existentes`() {
        createV3WithData()
        val expensesBefore = rows("SELECT * FROM expenses ORDER BY id")
        val candidatesBefore = rows("SELECT * FROM candidates ORDER BY id")

        migrate(3)

        assertEquals(expensesBefore, rows("SELECT * FROM expenses ORDER BY id"))
        assertEquals(candidatesBefore, rows("SELECT * FROM candidates ORDER BY id").map { it.dropLast(1) })
        assertEquals(1L, count("SELECT merchant_confident FROM candidates WHERE id = 1"))
        assertTrue("merchant_confident" in columnNames("candidates"))
    }

    @Test
    fun `migrar de v4 a v5 crea la cola sin tocar datos financieros`() {
        createV4WithData()
        val expensesBefore = rows("SELECT * FROM expenses ORDER BY id")
        val candidatesBefore = rows("SELECT * FROM candidates ORDER BY id")
        val activityBefore = rows("SELECT * FROM activity_log ORDER BY id")

        migrate(4)

        assertEquals(expensesBefore, rows("SELECT * FROM expenses ORDER BY id"))
        assertEquals(candidatesBefore, rows("SELECT * FROM candidates ORDER BY id"))
        assertEquals(activityBefore, rows("SELECT * FROM activity_log ORDER BY id"))
        assertTrue("notification_analysis_queue" in schemaTables)
        assertEquals(0L, count("SELECT COUNT(*) FROM notification_analysis_queue"))
    }

    @Test
    fun `migrar de v1 a v5 agrega description, merchant_confident y cola sin perder datos`() {
        createV1WithData()
        val gastosAntes = rows("SELECT * FROM expenses ORDER BY id")

        migrate(1)

        assertEquals(gastosAntes, rows("SELECT * FROM expenses ORDER BY id"))
        // el candidato existente conserva sus datos y recibe description vacia
        assertEquals("", scalar("SELECT description FROM candidates WHERE id = 1"))
        assertEquals(50000L, count("SELECT amount FROM candidates WHERE id = 1"))
        assertEquals(CatalogSeedV3.categories, catalogFromDb())
    }

    // --- misma base que una instalacion nueva v5 -------------------------------

    @Test
    fun `una base migrada tiene el mismo esquema que una instalacion nueva, partiendo de cualquier v1 o v2`() {
        // Clasico bug de migraciones: onCreate y onUpgrade dejan esquemas distintos.
        val nueva = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            val esquemaNueva = usingConnection(nueva) {
                create(CatalogDefaults.categories)
                schemaTables.associateWith { columns(it) }
            }
            listOf(
                "v1" to Pair(::createV1WithData, 1),
                "v2 migrada con ALTER" to Pair(::createV2ByAlterWithData, 2),
                "v2 creada por onCreate" to Pair(::createV2FromScratchWithData, 2),
                "v3 con catalogo" to Pair(::createV3WithData, 3),
                "v4 con merchant_confident" to Pair(::createV4WithData, 4),
            ).forEach { (origen, par) ->
                val (crear, version) = par
                reopen()
                crear()
                migrate(version)

                assertEquals(origen, esquemaNueva, schemaTables.associateWith { columns(it) })
            }
        } finally {
            nueva.close()
        }
    }

    @Test
    fun `una instalacion nueva crea las cinco tablas y merchant_confident`() {
        create(CatalogDefaults.categories)

        val tablas = rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
            .map { it[0] }
        assertEquals(listOf("activity_log", "candidates", "catalog_entries", "expenses", "notification_analysis_queue"), tablas)
        assertEquals(CatalogDefaults.categories, catalogFromDb())
        assertTrue("merchant_confident" in columnNames("candidates"))
    }

    @Test
    fun `la cola persistente tiene los campos de entrada, estado y auditoria`() {
        create(CatalogDefaults.categories)

        assertEquals(
            setOf(
                "id", "event_key", "package_name", "application_name", "title",
                "notification_text", "big_text", "posted_at", "parser_amount",
                "parser_currency", "parser_merchant", "parser_merchant_confident",
                "parser_id", "parser_confidence", "status", "attempts", "last_error",
                "model_version", "decision", "movement", "result_amount",
                "result_currency", "result_merchant", "occurred_at", "reasons",
                "created_at", "updated_at",
            ),
            columnNames("notification_analysis_queue"),
        )
    }

    // --- la regla del ADR 0005: solo aditivo -----------------------------------

    /** Ademas de DROP/DELETE/TRUNCATE/REPLACE, UPDATE y RENAME tambien pisan o pierden datos. */
    private val destructive = Regex("""\b(DROP|DELETE|TRUNCATE|REPLACE|UPDATE|RENAME)\b""", RegexOption.IGNORE_CASE)

    @Test
    fun `el detector de sentencias destructivas atrapa cada forma de perder o pisar datos`() {
        listOf(
            "DROP TABLE candidates",
            "DELETE FROM expenses",
            "TRUNCATE TABLE expenses",
            "INSERT OR REPLACE INTO catalog_entries VALUES (1)",
            "REPLACE INTO catalog_entries VALUES (1)",
            "UPDATE expenses SET category_id = 'OTHER'",
            "ALTER TABLE expenses RENAME TO expenses_old",
            "alter table expenses rename column amount to monto",
            "ALTER TABLE expenses DROP COLUMN description",
        ).forEach { assertTrue("debia detectar: $it", destructive.containsMatchIn(it)) }
    }

    @Test
    fun `el detector no marca las sentencias aditivas legitimas`() {
        listOf(
            "CREATE TABLE IF NOT EXISTS catalog_entries (kind TEXT NOT NULL)",
            "ALTER TABLE candidates ADD COLUMN description TEXT NOT NULL DEFAULT ''",
            "INSERT OR IGNORE INTO catalog_entries (kind, id) VALUES (?, ?)",
            "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT)",
        ).forEach { assertFalse("no debia marcar: $it", destructive.containsMatchIn(it)) }
    }

    @Test
    fun `ninguna migracion ejecuta una sentencia destructiva (ADR 0005), desde ninguna base historica`() {
        val origenes = listOf(
            Triple("v1", ::createV1WithData, 1),
            Triple("v2 migrada con ALTER", ::createV2ByAlterWithData, 2),
            Triple("v2 creada por onCreate", ::createV2FromScratchWithData, 2),
            Triple("v3 con catalogo", ::createV3WithData, 3),
            Triple("v4 con merchant_confident", ::createV4WithData, 4),
        )
        origenes.forEach { (origen, crear, version) ->
            reopen()
            crear()

            val peligrosas = migrate(version).filter { destructive.containsMatchIn(it) }

            assertTrue("migrar desde $origen ejecuto sentencias destructivas: $peligrosas", peligrosas.isEmpty())
        }
    }

    @Test
    fun `crear la base tampoco ejecuta sentencias destructivas`() {
        val ejecutadas = create(CatalogDefaults.categories)

        assertTrue(ejecutadas.none { destructive.containsMatchIn(it) })
    }

    @Test
    fun `se puede migrar desde cada version anterior y no hay un hueco entre versiones`() {
        // Si alguien sube VERSION y agrega un bloque `oldVersion < N` pero se olvida de
        // que una base v1 tambien debe pasar por el, este test (que recorre todas) lo ve.
        for (oldVersion in 1 until DbSchema.VERSION) {
            reopen()
            when (oldVersion) {
                1 -> createV1WithData()
                2 -> createV2ByAlterWithData()
                3 -> createV3WithData()
                4 -> createV4WithData()
            }

            migrate(oldVersion)

            assertFalse("v$oldVersion no quedo con catalogo", rows("SELECT id FROM catalog_entries").isEmpty())
            assertEquals("v$oldVersion perdio gastos", 2L, count("SELECT COUNT(*) FROM expenses"))
        }
    }

    @Test
    fun `la version del esquema es la 5 (si cambia, hay que agregar su bloque y su test)`() {
        assertEquals(5, DbSchema.VERSION)
    }

    // --- transaccion: lo que garantiza Android y la migracion necesita ------------

    @Test
    fun `una migracion que falla a mitad se revierte COMPLETA y se puede reintentar desde la misma version`() {
        createV1WithData()
        val fallaEnElSeed = object : SqlExecutor {
            private val real = JdbcSqlExecutor(conn)
            override fun exec(sql: String, args: List<Any?>) {
                if (sql.startsWith("INSERT")) throw IllegalStateException("fallo simulado del seed")
                real.exec(sql, args)
            }
            override fun hasColumn(tableName: String, columnName: String): Boolean =
                real.hasColumn(tableName, columnName)
        }

        try {
            inTransaction { DbSchema.migrate(fallaEnElSeed, 1) }
            fail("debia propagar el fallo")
        } catch (e: IllegalStateException) {
            // esperado
        }

        // El ALTER y el CREATE que si alcanzaron a correr quedaron revertidos...
        assertFalse("description no debia quedar aplicada", "description" in columnNames("candidates"))
        assertEquals(0L, count("SELECT COUNT(*) FROM sqlite_master WHERE name = 'catalog_entries'"))
        assertEquals(2L, count("SELECT COUNT(*) FROM expenses"))
        // ...asi que reintentar desde la misma version funciona (sin 'duplicate column name').
        migrate(1)
        assertTrue("description" in columnNames("candidates"))
        assertEquals(11L, count("SELECT COUNT(*) FROM catalog_entries"))
    }

    // --- que entidad, tabla y seed no se desincronicen -------------------------------

    private fun camelToSnake(name: String) = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

    @Test
    fun `cada propiedad de CatalogEntry tiene su columna en la tabla (y viceversa)`() {
        create(CatalogDefaults.categories)
        val propiedades = CatalogEntry::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { camelToSnake(it.name) }
            .toSet()

        assertEquals(
            "agregar una propiedad a CatalogEntry exige su columna (y tocar el seed y CatalogRows)",
            propiedades,
            columnNames("catalog_entries"),
        )
    }

    @Test
    fun `el INSERT del seed lista TODAS las columnas de la tabla`() {
        val ejecutadas = create(CatalogDefaults.categories)
        val insert = ejecutadas.first { it.startsWith("INSERT OR IGNORE INTO catalog_entries") }
        val columnasDelInsert = Regex("""\(([^)]*)\)\s+VALUES""").find(insert)!!
            .groupValues[1].split(",").map { it.trim() }.toSet()

        assertEquals(
            "una columna nueva que el seed no incluye se sembraria con su valor por defecto, sin avisar",
            columnNames("catalog_entries"),
            columnasDelInsert,
        )
    }
}
