package com.eliasgonzalez.expensetracker.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `version remota mayor en el ultimo segmento es mas nueva`() {
        assertTrue(isNewerVersion("0.3.0", "0.2.0"))
    }

    @Test
    fun `version remota menor no es mas nueva`() {
        assertFalse(isNewerVersion("0.1.0", "0.2.0"))
    }

    @Test
    fun `version identica no es mas nueva`() {
        assertFalse(isNewerVersion("0.2.0", "0.2.0"))
    }

    @Test
    fun `compara numericamente, no alfabeticamente - 0-10-0 es mayor que 0-9-0`() {
        assertTrue(isNewerVersion("0.10.0", "0.9.0"))
        assertFalse(isNewerVersion("0.9.0", "0.10.0"))
    }

    @Test
    fun `mayor version en un segmento mas significativo gana aunque el siguiente sea menor`() {
        assertTrue(isNewerVersion("1.0.0", "0.99.99"))
        assertFalse(isNewerVersion("0.99.99", "1.0.0"))
    }

    @Test
    fun `segmentos faltantes cuentan como cero`() {
        assertTrue(isNewerVersion("0.3", "0.2.9"))
        assertFalse(isNewerVersion("0.2", "0.2.1"))
        assertTrue(isNewerVersion("0.2.1", "0.2"))
    }

    @Test
    fun `segmentos no numericos cuentan como cero sin crashear`() {
        assertFalse(isNewerVersion("poc", "0.0.0"))
        assertFalse(isNewerVersion("", ""))
    }

    // --- Regresion (hallazgo de auditoria, 2026-08-28): un tag remoto
    // con sufijo de pre-release (ej. "-beta.1") se comparaba mal - el
    // sufijo se colaba dentro de un segmento normal via toIntOrNull()->0,
    // "corriendo" la cuenta de segmentos y dando un falso "hay
    // actualizacion" aunque el remoto fuera la misma version o un beta
    // mas viejo que la version estable ya instalada.

    @Test
    fun `un pre-release remoto con el mismo nucleo que la version actual no es mas nueva`() {
        assertFalse(isNewerVersion("1.2.0-beta.1", "1.2.0"))
    }

    @Test
    fun `un pre-release remoto con nucleo menor tampoco es mas nueva`() {
        assertFalse(isNewerVersion("1.1.0-beta.1", "1.2.0"))
    }

    @Test
    fun `un pre-release remoto con nucleo mayor si es mas nueva (el nucleo manda)`() {
        assertTrue(isNewerVersion("1.3.0-beta.1", "1.2.0"))
    }

    @Test
    fun `pasar de un pre-release instalado al release estable del mismo nucleo cuenta como actualizacion`() {
        assertTrue(isNewerVersion("1.2.0", "1.2.0-beta.1"))
    }

    @Test
    fun `dos pre-release del mismo nucleo no se consideran mas nuevos entre si`() {
        assertFalse(isNewerVersion("1.2.0-beta.1", "1.2.0-beta.2"))
        assertFalse(isNewerVersion("1.2.0-beta.2", "1.2.0-beta.1"))
    }

    // Casos de borde de splitVersionCore señalados en la revisión de los
    // fixes de la auditoría (no eran un bug, pero no estaban cubiertos).

    @Test
    fun `sufijo pre-release sin nucleo numerico (solo -beta) no crashea y no cuenta como mas nuevo`() {
        assertFalse(isNewerVersion("-beta", "0.0.0"))
    }

    @Test
    fun `multiples guiones - solo el primero separa el nucleo del sufijo`() {
        // "1.2.0-beta-2" y "1.2.0-rc-1" tienen el mismo nucleo (1.2.0) y
        // ambos son pre-release, asi que ninguno es "mas nuevo" que el otro.
        assertFalse(isNewerVersion("1.2.0-beta-2", "1.2.0-rc-1"))
        assertTrue(isNewerVersion("1.3.0-beta-2", "1.2.0-rc-1"))
    }

    @Test
    fun `guion como primer caracter no crashea`() {
        assertFalse(isNewerVersion("-1.2.0", "0.0.0"))
    }
}
