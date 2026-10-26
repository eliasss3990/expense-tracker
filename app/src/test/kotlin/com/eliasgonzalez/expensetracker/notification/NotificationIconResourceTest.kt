package com.eliasgonzalez.expensetracker.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android usa SOLO la transparencia del icono chico de una notificacion y tiñe el
 * resto: si el dibujo trae colores, degradados o rellenos de otro color, se ve como
 * un cuadrado blanco. Esto no se puede ver en un test de JVM, pero si se puede vigilar
 * que el recurso siga cumpliendo la regla: que nadie lo reemplace por una version de
 * colores (como ic_brand_mark) sin darse cuenta.
 */
class NotificationIconResourceTest {

    private val iconFile = File("src/main/res/drawable/ic_stat_expense.xml")

    private fun root(): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(iconFile).documentElement

    private fun allElements(e: Element): List<Element> =
        listOf(e) + (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }.flatMap { allElements(it) }

    @Test
    fun `el recurso existe y es un vector de 24dp`() {
        assertTrue("falta ${iconFile.path}", iconFile.exists())
        val vector = root()

        assertEquals("vector", vector.tagName)
        assertEquals("24dp", vector.getAttribute("android:width"))
        assertEquals("24dp", vector.getAttribute("android:height"))
        assertEquals("24", vector.getAttribute("android:viewportWidth"))
        assertEquals("24", vector.getAttribute("android:viewportHeight"))
    }

    @Test
    fun `todo color del dibujo es blanco o transparente - una sola silueta`() {
        val allowed = setOf("#FFFFFFFF", "#FFFFFF", "#00000000")
        val colores = allElements(root()).flatMap {
            listOf(it.getAttribute("android:fillColor"), it.getAttribute("android:strokeColor"))
        }.filter { it.isNotEmpty() }

        assertTrue("el icono no define ningun color", colores.isNotEmpty())
        colores.forEach { assertTrue("color no permitido en un icono de notificacion: $it", it.uppercase() in allowed) }
    }

    @Test
    fun `tiene algo visible (no es solo transparente)`() {
        val visibles = allElements(root()).flatMap {
            listOf(it.getAttribute("android:fillColor"), it.getAttribute("android:strokeColor"))
        }.filter { it.uppercase() == "#FFFFFFFF" || it.uppercase() == "#FFFFFF" }

        assertTrue(visibles.isNotEmpty())
    }

    @Test
    fun `la moneda usa relleno par-impar para que el check sea un HUECO (como la marca real)`() {
        // En la marca real el check va dibujado encima de la moneda. En una silueta monocroma eso
        // se ve como una moneda lisa sin check, asi que el check tiene que ser un hueco: el
        // relleno par-impar sobre un solo path (moneda + check) lo deja transparente.
        val paths = allElements(root()).filter { it.tagName == "path" }

        assertEquals("moneda con el check recortado + punto de acento", 2, paths.size)
        assertTrue(
            "falta fillType=evenOdd: el check quedaria relleno y la moneda se veria lisa",
            paths.any { it.getAttribute("android:fillType") == "evenOdd" },
        )
    }

    @Test
    fun `no usa degradados ni atributos de aapt (el launcher si, la notificacion no)`() {
        val source = iconFile.readText()

        assertTrue("no debe tener degradados", !source.contains("<gradient"))
        assertTrue("no debe usar aapt:attr", !source.contains("aapt:attr"))
    }
}
