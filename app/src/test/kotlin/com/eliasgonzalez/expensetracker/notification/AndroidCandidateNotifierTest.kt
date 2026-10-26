package com.eliasgonzalez.expensetracker.notification

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regresion: el id con el que se retira la notificacion tiene que ser
 * EXACTAMENTE el mismo con el que se publica (`candidate.id.toInt()`). Si
 * alguien cambia uno sin el otro, el cancel deja de encontrar la
 * notificacion y el bug de "notificacion colgada" vuelve en silencio.
 * Ambos lados pasan por notificationIdFor, y este test fija su contrato.
 */
class AndroidCandidateNotifierTest {

    @Test
    fun `el id de notificacion es el id del candidato como Int`() {
        assertEquals(1, AndroidCandidateNotifier.notificationIdFor(1L))
        assertEquals(42, AndroidCandidateNotifier.notificationIdFor(42L))
    }

    @Test
    fun `candidatos distintos tienen ids de notificacion distintos`() {
        val ids = (1L..500L).map { AndroidCandidateNotifier.notificationIdFor(it) }.toSet()
        assertEquals(500, ids.size)
    }
}
