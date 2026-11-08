package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ParserEvidence
import com.eliasgonzalez.expensetracker.domain.analysis.ParserSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.SourceTrust
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class LocalAnalysisQueueRepositoryTest {
    private lateinit var conn: Connection
    private lateinit var repo: LocalAnalysisQueueRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        DbSchema.create(JdbcSqlDatabase(conn), emptyList())
        repo = LocalAnalysisQueueRepository(JdbcSqlDatabase(conn)) { clock }
    }

    @After
    fun tearDown() = conn.close()

    private fun event(key: String, parser: ParserEvidence = ParserEvidence.NoParser, text: String = "Compra Gs. 85.000") =
        AnalysisEvent(key, NotificationSnapshot("py.banco", "Banco", "Aviso", text, "mas texto", 500L), parser, SourceTrust.BANK)

    @Test
    fun `encolar es idempotente por clave de evento`() = runTest {
        assertNotNull(repo.enqueue(event("k1")))
        assertNull(repo.enqueue(event("k1")))
        assertNotNull(repo.enqueue(event("k2")))
    }

    @Test
    fun `lo encolado se lee igual, incluidos los tres tipos de parser`() = runTest {
        val parsed = ParserEvidence.Parsed(ParserSnapshot(85_000, "PYG", "Tienda", true, "ueno", confidence = 0.6, curated = false))
        repo.enqueue(event("a", parsed))
        repo.enqueue(event("b", ParserEvidence.ExplicitlyIgnored("ueno")))
        repo.enqueue(event("c"))

        val first = repo.nextPending(emptySet(), 3)!!
        assertEquals(parsed, first.event.parser)
        assertEquals("Compra Gs. 85.000", first.event.notification.text)
        assertEquals(SourceTrust.BANK, first.event.trust)
        repo.markProcessing(first.id); repo.markDone(first.id)
        val second = repo.nextPending(emptySet(), 3)!!
        assertEquals(ParserEvidence.ExplicitlyIgnored("ueno"), second.event.parser)
        repo.markProcessing(second.id); repo.markDone(second.id)
        assertEquals(ParserEvidence.NoParser, repo.nextPending(emptySet(), 3)!!.event.parser)
    }

    @Test
    fun `markProcessing cuenta el intento y solo lo toma uno`() = runTest {
        val id = repo.enqueue(event("k"))!!
        assertTrue(repo.markProcessing(id))
        assertFalse("ya esta tomado", repo.markProcessing(id))
        repo.markRetry(id, "Boom")
        assertEquals(1, repo.nextPending(emptySet(), 3)!!.attempts)
    }

    @Test
    fun `un reintento que no cuenta devuelve el intento`() = runTest {
        val id = repo.enqueue(event("k"))!!
        repo.markProcessing(id)
        repo.markRetry(id, "NotReady", refundAttempt = true)
        assertEquals(0, repo.nextPending(emptySet(), 3)!!.attempts)
    }

    @Test
    fun `no devuelve lo que ya agoto los intentos ni lo excluido`() = runTest {
        val a = repo.enqueue(event("a"))!!
        val b = repo.enqueue(event("b"))!!
        repeat(3) { repo.markProcessing(a); repo.markRetry(a, "x") }
        assertEquals(b, repo.nextPending(emptySet(), 3)!!.id)
        assertNull(repo.nextPending(setOf(b), 3))
    }

    @Test
    fun `recoverProcessing devuelve a pendiente lo que quedo por la muerte del proceso`() = runTest {
        val id = repo.enqueue(event("k"))!!
        repo.markProcessing(id)
        assertNull(repo.nextPending(emptySet(), 3))
        repo.recoverProcessing()
        assertEquals(id, repo.nextPending(emptySet(), 3)!!.id)
    }

    @Test
    fun `pruneBefore borra solo lo terminado y viejo, nunca lo pendiente`() = runTest {
        val old = repo.enqueue(event("old"))!!
        val pending = repo.enqueue(event("pending"))!!
        repo.markProcessing(old); repo.markDone(old)
        clock = 10_000L
        val fresh = repo.enqueue(event("fresh"))!!
        repo.markProcessing(fresh); repo.markDone(fresh)

        repo.pruneBefore(5_000L)

        val remaining = conn.createStatement().use { st ->
            st.executeQuery("SELECT event_key FROM analysis_queue ORDER BY id").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
        assertEquals(listOf("pending", "fresh"), remaining)
        assertEquals(pending, repo.nextPending(emptySet(), 3)!!.id)
    }

    @Test
    fun `nextExhausted devuelve el pendiente que agoto sus intentos y nunca uno que aun puede reintentarse`() = runTest {
        val spent = repo.enqueue(event("spent"))!!
        val alive = repo.enqueue(event("alive"))!!
        repeat(3) { repo.markProcessing(spent); repo.markRetry(spent, "x") }
        repo.markProcessing(alive); repo.markRetry(alive, "x")

        assertEquals(spent, repo.nextExhausted(3)!!.id)
        repo.markGaveUp(spent, "AttemptsExhausted")
        assertNull("el otro aun tiene intentos", repo.nextExhausted(3))
    }
}
