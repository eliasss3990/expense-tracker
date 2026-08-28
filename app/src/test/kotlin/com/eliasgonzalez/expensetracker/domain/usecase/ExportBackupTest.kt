package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests para ExportBackup, que arma JSON a mano con interpolacion de
 * strings (sin libreria - ver comentario en el archivo de produccion).
 * No hay ninguna libreria JSON en las dependencias del proyecto
 * (`app/build.gradle.kts` solo trae JUnit y coroutines-test), y
 * `org.json` del android.jar de test lanza "not mocked" en unit tests JVM
 * puros sin Robolectric, asi que en vez de depender de eso escribimos un
 * parser JSON minimo (MiniJson, mas abajo) que valida estructura y,
 * siguiendo el RFC 8259 al pie de la letra, rechaza caracteres de control
 * sin escapar dentro de un string - exactamente el tipo de bug que
 * queremos detectar en un serializador armado a mano.
 */
class ExportBackupTest {

    @Test
    fun `backup vacio es JSON valido con arrays vacios`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>

        assertEquals(1.0, root["formatVersion"])
        assertTrue((root["expenses"] as List<*>).isEmpty())
        assertTrue((root["candidates"] as List<*>).isEmpty())
        assertTrue((root["activity"] as List<*>).isEmpty())
    }

    @Test
    fun `merchant con comillas dobles sin escapar produce JSON valido y recupera el valor original`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)
        val merchantOriginal = """Pizza "La Preferida""""

        expenses.save(
            Expense(
                amount = 10_000,
                merchant = merchantOriginal,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["expenses"] as List<*>).first() as Map<*, *>

        assertEquals(merchantOriginal, first["merchant"])
    }

    @Test
    fun `merchant con backslashes produce JSON valido y recupera el valor original`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)
        val merchantOriginal = """C:\Users\test\backup"""

        expenses.save(
            Expense(
                amount = 10_000,
                merchant = merchantOriginal,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["expenses"] as List<*>).first() as Map<*, *>

        assertEquals(merchantOriginal, first["merchant"])
    }

    @Test
    fun `merchant con combinacion de backslash seguido de comilla no rompe el JSON`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)
        // Caso trampa clasico: si el orden de reemplazo (primero backslash,
        // luego comilla) estuviera invertido, esto produciria JSON invalido.
        val merchantOriginal = "Raro\\\""

        expenses.save(
            Expense(
                amount = 10_000,
                merchant = merchantOriginal,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["expenses"] as List<*>).first() as Map<*, *>

        assertEquals(merchantOriginal, first["merchant"])
    }

    @Test
    fun `merchant con salto de linea embebido se escapa correctamente`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)
        val merchantOriginal = "Super\nMercado"

        expenses.save(
            Expense(
                amount = 10_000,
                merchant = merchantOriginal,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["expenses"] as List<*>).first() as Map<*, *>

        assertEquals(merchantOriginal, first["merchant"])
    }

    @Test
    fun `merchant con emojis, unicode y el simbolo guarani se preserva`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)
        val merchantOriginal = "Ñoño's Café 🍔₲日本語𝕊"

        expenses.save(
            Expense(
                amount = 10_000,
                merchant = merchantOriginal,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["expenses"] as List<*>).first() as Map<*, *>

        assertEquals(merchantOriginal, first["merchant"])
    }

    @Test
    fun `regresion - merchant con retorno de carro embebido genera JSON valido`() = runTest {
        // Bug encontrado y corregido: ExportBackup.q() solo escapaba backslash,
        // comilla doble y '\n', dejando otros caracteres de control obligatorios
        // por RFC 8259 (U+0000-U+001F) crudos dentro del string - '\r' rompia el
        // JSON resultante. String.q() ahora escapa todo ese rango. Si alguien
        // vuelve a acotar el escapado, este test vuelve a fallar.
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)
        val merchantOriginal = "Super\rMercado"

        expenses.save(
            Expense(
                amount = 10_000,
                merchant = merchantOriginal,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val json = export()

        try {
            val root = MiniJson.parse(json) as Map<*, *>
            val first = (root["expenses"] as List<*>).first() as Map<*, *>
            assertEquals(merchantOriginal, first["merchant"])
        } catch (e: MiniJson.JsonParseException) {
            fail(
                "REGRESION: ExportBackup volvio a producir JSON invalido para un " +
                    "merchant con '\\r' embebido. Error del parser: ${e.message}"
            )
        }
    }

    @Test
    fun `regresion - merchant con tab embebido genera JSON valido`() = runTest {
        // Mismo caso que el retorno de carro, con tab - ver comentario de arriba.
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)
        val merchantOriginal = "Super\tMercado"

        expenses.save(
            Expense(
                amount = 10_000,
                merchant = merchantOriginal,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val json = export()

        try {
            val root = MiniJson.parse(json) as Map<*, *>
            val first = (root["expenses"] as List<*>).first() as Map<*, *>
            assertEquals(merchantOriginal, first["merchant"])
        } catch (e: MiniJson.JsonParseException) {
            fail(
                "REGRESION: ExportBackup volvio a producir JSON invalido para un " +
                    "merchant con '\\t' embebido. Error del parser: ${e.message}"
            )
        }
    }

    @Test
    fun `candidate con sourceApp y parserId null serializa como null literal`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)

        candidates.save(
            ExpenseCandidate(
                amount = 5_000,
                merchant = "Detectado",
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
                sourceApp = null,
                parserId = null,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["candidates"] as List<*>).first() as Map<*, *>

        assertNull(first["sourceApp"])
        assertNull(first["parserId"])
        assertTrue(first.containsKey("sourceApp"))
        assertTrue(first.containsKey("parserId"))
    }

    @Test
    fun `candidate con sourceApp y parserId no nulos con comillas y unicode se preserva`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)

        candidates.save(
            ExpenseCandidate(
                amount = 5_000,
                merchant = """App "Rara"""",
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
                sourceApp = "com.banco.app",
                parserId = """parser_"v2"_ñ""",
                status = CandidateStatus.PENDING,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["candidates"] as List<*>).first() as Map<*, *>

        assertEquals("com.banco.app", first["sourceApp"])
        assertEquals("""parser_"v2"_ñ""", first["parserId"])
        assertEquals("""App "Rara"""", first["merchant"])
    }

    @Test
    fun `activity con expenseId y candidateId null serializa como null literal`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)

        activity.record(
            ActivityEntry(
                type = ActivityType.CANDIDATE_CREATED,
                expenseId = null,
                candidateId = null,
                timestamp = 1L,
                summary = "resumen sin ids",
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["activity"] as List<*>).first() as Map<*, *>

        assertNull(first["expenseId"])
        assertNull(first["candidateId"])
    }

    @Test
    fun `activity con summary conteniendo comillas y backslashes produce JSON valido`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)
        val summaryOriginal = """Editado: "Kiosco" -> C:\nuevo\valor — ₲1,000"""

        activity.record(
            ActivityEntry(
                type = ActivityType.EXPENSE_EDITED,
                expenseId = 1L,
                timestamp = 1L,
                summary = summaryOriginal,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["activity"] as List<*>).first() as Map<*, *>

        assertEquals(summaryOriginal, first["summary"])
    }

    @Test
    fun `multiples expenses se separan correctamente con comas validas`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)

        expenses.save(
            Expense(amount = 1_000, merchant = "Uno", occurredAt = 1L, createdAt = 1L, source = ExpenseSource.MANUAL)
        )
        expenses.save(
            Expense(amount = 2_000, merchant = "Dos \"con comillas\"", occurredAt = 2L, createdAt = 2L, source = ExpenseSource.MANUAL)
        )
        expenses.save(
            Expense(amount = 3_000, merchant = "Tres", occurredAt = 3L, createdAt = 3L, source = ExpenseSource.MANUAL)
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val list = root["expenses"] as List<*>

        assertEquals(3, list.size)
        val merchants = list.map { (it as Map<*, *>)["merchant"] }
        assertTrue(merchants.contains("Uno"))
        assertTrue(merchants.contains("Dos \"con comillas\""))
        assertTrue(merchants.contains("Tres"))
    }

    @Test
    fun `sourceReference no nulo se serializa como numero`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)

        expenses.save(
            Expense(
                amount = 1_000,
                merchant = "Con referencia",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
                sourceReference = 42L,
            )
        )

        val json = export()
        val root = MiniJson.parse(json) as Map<*, *>
        val first = (root["expenses"] as List<*>).first() as Map<*, *>

        assertEquals(42.0, first["sourceReference"])
    }

    @Test
    fun `monto extremo Long MAX_VALUE no rompe el JSON`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val export = ExportBackup(expenses, candidates, activity)

        expenses.save(
            Expense(
                amount = Long.MAX_VALUE,
                merchant = "Monto extremo",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val json = export()
        // Solo verificamos estructura valida; un parser basado en Double
        // pierde precision en Long.MAX_VALUE, lo cual es un problema
        // aparte de validez estructural.
        MiniJson.parse(json)
    }
}

/**
 * Parser JSON minimo, de un solo archivo, sin dependencias externas.
 * Implementa el subconjunto de RFC 8259 necesario para validar los
 * backups de ExportBackup: objetos, arrays, strings (con escapes
 * estandar y validacion estricta de caracteres de control), numeros,
 * true/false/null.
 */
object MiniJson {
    class JsonParseException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw JsonParseException("Texto sobrante despues del JSON en posicion ${parser.pos}")
        }
        return value
    }

    private class Parser(private val text: String) {
        var pos = 0

        fun isAtEnd() = pos >= text.length

        fun peek(): Char {
            if (isAtEnd()) throw JsonParseException("Fin de texto inesperado en posicion $pos")
            return text[pos]
        }

        fun skipWhitespace() {
            while (!isAtEnd() && text[pos].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (isAtEnd()) throw JsonParseException("Se esperaba un valor en posicion $pos")
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        fun parseLiteral(literal: String, value: Any?): Any? {
            if (pos + literal.length > text.length || text.substring(pos, pos + literal.length) != literal) {
                throw JsonParseException("Literal invalido en posicion $pos, se esperaba '$literal'")
            }
            pos += literal.length
            return value
        }

        fun parseObject(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            pos++ // consume '{'
            skipWhitespace()
            if (!isAtEnd() && peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (isAtEnd() || peek() != '"') {
                    throw JsonParseException("Se esperaba una clave string en posicion $pos")
                }
                val key = parseString()
                skipWhitespace()
                if (isAtEnd() || peek() != ':') {
                    throw JsonParseException("Se esperaba ':' en posicion $pos")
                }
                pos++
                skipWhitespace()
                val value = parseValue()
                result[key] = value
                skipWhitespace()
                if (isAtEnd()) throw JsonParseException("Objeto sin cerrar en posicion $pos")
                when (peek()) {
                    ',' -> {
                        pos++
                    }
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> throw JsonParseException("Se esperaba ',' o '}' en posicion $pos")
                }
            }
        }

        fun parseArray(): List<Any?> {
            val result = mutableListOf<Any?>()
            pos++ // consume '['
            skipWhitespace()
            if (!isAtEnd() && peek() == ']') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                val value = parseValue()
                result.add(value)
                skipWhitespace()
                if (isAtEnd()) throw JsonParseException("Array sin cerrar en posicion $pos")
                when (peek()) {
                    ',' -> {
                        pos++
                    }
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> throw JsonParseException("Se esperaba ',' o ']' en posicion $pos")
                }
            }
        }

        fun parseString(): String {
            pos++ // consume opening quote
            val sb = StringBuilder()
            while (true) {
                if (isAtEnd()) throw JsonParseException("String sin cerrar en posicion $pos")
                val c = text[pos]
                val code = c.code
                if (code in 0x00..0x1F) {
                    // RFC 8259: los caracteres de control deben ir escapados.
                    throw JsonParseException(
                        "Caracter de control sin escapar (0x${code.toString(16)}) dentro de un string en posicion $pos"
                    )
                }
                when (c) {
                    '"' -> {
                        pos++
                        return sb.toString()
                    }
                    '\\' -> {
                        pos++
                        if (isAtEnd()) throw JsonParseException("Escape incompleto en posicion $pos")
                        when (val esc = text[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 >= text.length) throw JsonParseException("Escape unicode incompleto en posicion $pos")
                                val hex = text.substring(pos + 1, pos + 5)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonParseException("Escape invalido '\\$esc' en posicion $pos")
                        }
                        pos++
                    }
                    else -> {
                        sb.append(c)
                        pos++
                    }
                }
            }
        }

        fun parseNumber(): Double {
            val start = pos
            if (!isAtEnd() && (peek() == '-' || peek() == '+')) pos++
            while (!isAtEnd() && (text[pos].isDigit() || text[pos] == '.' || text[pos] == 'e' || text[pos] == 'E' || text[pos] == '+' || text[pos] == '-')) {
                pos++
            }
            if (pos == start) throw JsonParseException("Numero invalido en posicion $pos")
            return text.substring(start, pos).toDouble()
        }
    }
}
