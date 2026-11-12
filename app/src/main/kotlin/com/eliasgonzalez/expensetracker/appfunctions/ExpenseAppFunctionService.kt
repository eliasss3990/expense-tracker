package com.eliasgonzalez.expensetracker.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.chat.ChatPeriod
import com.eliasgonzalez.expensetracker.domain.usecase.sumSpending
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de [BaseExpenseAppFunctionService.getSpendingTotal]. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SpendingSummary(
    /** Total gastado en el periodo, en la unidad entera de la moneda (guaranies no tienen decimales). */
    val total: Long,
    /** Cantidad de gastos incluidos en el total. */
    val expenseCount: Int,
    /** Codigo de la moneda del total, por ejemplo "PYG". */
    val currency: String,
    /** Primer dia incluido (formato AAAA-MM-DD), o vacio si el periodo no tiene limite inferior. */
    val fromDate: String,
    /** Ultimo dia incluido (formato AAAA-MM-DD), o vacio si el periodo no tiene limite superior. */
    val toDate: String,
)

/**
 * PRUEBA de AppFunctions: una unica funcion de solo lectura para comprobar que un asistente (Gemini) puede llamar a la
 * app. Reutiliza los mismos datos y el calculo de periodos del chat de IA (ADR 0015): el asistente solo interpreta,
 * la app calcula. KSP genera el servicio concreto `ExpenseAppFunctionService` a partir de esta clase.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "ExpenseAppFunctionService",
    appFunctionXmlFileName = "expense_app_function_service",
)
abstract class BaseExpenseAppFunctionService : AppFunctionService() {

    /**
     * Calcula cuanto dinero gasto el usuario en un periodo, a partir de los gastos registrados en la app.
     *
     * @param period Periodo a consultar. Uno de: TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH,
     * LAST_7_DAYS, LAST_30_DAYS, ALL. Las semanas empiezan el lunes. Lanza [AppFunctionInvalidArgumentException] si no
     * es uno de estos valores.
     * @return El total gastado, la cantidad de gastos y el rango de fechas usado.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getSpendingTotal(period: String): SpendingSummary = withContext(Dispatchers.IO) {
        val parsed = ChatPeriod.entries.firstOrNull { it != ChatPeriod.CUSTOM && it.name.equals(period.trim(), ignoreCase = true) }
            ?: throw AppFunctionInvalidArgumentException(
                "Periodo no valido: '$period'. Usa TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, " +
                    "LAST_7_DAYS, LAST_30_DAYS o ALL.",
            )
        val container = ServiceLocator.get()
        // Sin esperar la carga inicial las listas en memoria estan vacias (el proceso puede arrancar frio por este llamado).
        if (!container.awaitDataLoaded()) {
            throw IllegalStateException("No se pudieron cargar los datos de la app")
        }
        val zone = ZoneId.systemDefault()
        val (from, to) = parsed.resolve(LocalDate.now(zone), customFrom = null, customTo = null)
        val result = sumSpending(container.observeExpenses().value, from, to, zone)
        SpendingSummary(
            total = result.total,
            expenseCount = result.count,
            currency = result.currency,
            fromDate = from?.toString().orEmpty(),
            toDate = to?.toString().orEmpty(),
        )
    }
}
