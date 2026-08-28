package com.eliasgonzalez.expensetracker

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.eliasgonzalez.expensetracker.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E de humo: carga un gasto a mano desde el Dashboard y verifica que
 * aparece en "Últimos gastos" - cruza UI (Compose), caso de uso de
 * dominio y persistencia en SQLite real, todo en un emulador.
 *
 * Sin GrantPermissionRule, el diálogo runtime de POST_NOTIFICATIONS
 * (Android 13+) tapa la pantalla apenas arranca la Activity y el test
 * no puede tocar nada debajo.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ExpenseFlowE2ETest {

    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cargarGastoManual_apareceEnUltimosGastos() {
        // El splash nativo se mantiene un mínimo de 2s en pantalla
        // (ver MainActivity.SPLASH_MIN_DURATION_MS) y tapa la Activity -
        // sin esperar acá, el primer performClick() cae sobre el splash.
        Thread.sleep(2500)

        val merchant = "Test E2E ${System.currentTimeMillis()}"

        composeRule.onNodeWithContentDescription("Nuevo gasto").performClick()
        composeRule.onNodeWithText("Monto (₲)").performTextInput("12345")
        composeRule.onNodeWithText("Comercio").performTextInput(merchant)
        composeRule.onNodeWithText("Guardar").performClick()

        composeRule.waitUntilExactlyOneExists(hasText(merchant), timeoutMillis = 5_000)
        composeRule.onNodeWithText(merchant).assertIsDisplayed()
    }
}
