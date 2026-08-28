package com.eliasgonzalez.expensetracker.ui.banners

import com.eliasgonzalez.expensetracker.ui.onboarding.PERMISSION_KEY_NOTIFICATION_LISTENER
import com.eliasgonzalez.expensetracker.ui.onboarding.PERMISSION_KEY_POST_NOTIFICATIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingPermissionsBannerTest {

    @Test
    fun `ningun permiso falta cuando ambos estan otorgados`() {
        val result = missingPermissionKeys(
            listenerEnabled = true,
            notificationsGranted = true,
            showNotificationsStep = true,
            dismissedKeys = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `falta el acceso a notificaciones si no esta habilitado`() {
        val result = missingPermissionKeys(
            listenerEnabled = false,
            notificationsGranted = true,
            showNotificationsStep = true,
            dismissedKeys = emptySet(),
        )
        assertEquals(listOf(PERMISSION_KEY_NOTIFICATION_LISTENER), result)
    }

    @Test
    fun `falta el permiso de notificaciones si no fue otorgado y aplica el paso (Android 13+)`() {
        val result = missingPermissionKeys(
            listenerEnabled = true,
            notificationsGranted = false,
            showNotificationsStep = true,
            dismissedKeys = emptySet(),
        )
        assertEquals(listOf(PERMISSION_KEY_POST_NOTIFICATIONS), result)
    }

    @Test
    fun `no pide el permiso de notificaciones en versiones anteriores a Android 13, aunque no este otorgado`() {
        val result = missingPermissionKeys(
            listenerEnabled = true,
            notificationsGranted = false,
            showNotificationsStep = false,
            dismissedKeys = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ambos permisos faltan a la vez, en orden`() {
        val result = missingPermissionKeys(
            listenerEnabled = false,
            notificationsGranted = false,
            showNotificationsStep = true,
            dismissedKeys = emptySet(),
        )
        assertEquals(listOf(PERMISSION_KEY_NOTIFICATION_LISTENER, PERMISSION_KEY_POST_NOTIFICATIONS), result)
    }

    @Test
    fun `un permiso descartado no aparece aunque siga faltando`() {
        val result = missingPermissionKeys(
            listenerEnabled = false,
            notificationsGranted = false,
            showNotificationsStep = true,
            dismissedKeys = setOf(PERMISSION_KEY_NOTIFICATION_LISTENER),
        )
        assertEquals(listOf(PERMISSION_KEY_POST_NOTIFICATIONS), result)
    }

    @Test
    fun `si se descartan los dos, no queda ninguno`() {
        val result = missingPermissionKeys(
            listenerEnabled = false,
            notificationsGranted = false,
            showNotificationsStep = true,
            dismissedKeys = setOf(PERMISSION_KEY_NOTIFICATION_LISTENER, PERMISSION_KEY_POST_NOTIFICATIONS),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `descartar un permiso ya otorgado no afecta nada (no aparecia igual)`() {
        val result = missingPermissionKeys(
            listenerEnabled = true,
            notificationsGranted = true,
            showNotificationsStep = true,
            dismissedKeys = setOf(PERMISSION_KEY_NOTIFICATION_LISTENER, PERMISSION_KEY_POST_NOTIFICATIONS),
        )
        assertTrue(result.isEmpty())
    }
}
