package com.eliasgonzalez.expensetracker.di

import android.content.Context
import com.eliasgonzalez.expensetracker.data.local.DbHelper
import com.eliasgonzalez.expensetracker.data.local.LocalActivityRepository
import com.eliasgonzalez.expensetracker.data.local.LocalCandidateRepository
import com.eliasgonzalez.expensetracker.data.local.LocalExpenseRepository
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import com.eliasgonzalez.expensetracker.domain.usecase.ConfirmCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.CreateCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.DeleteExpense
import com.eliasgonzalez.expensetracker.domain.usecase.EditCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.EditExpense
import com.eliasgonzalez.expensetracker.domain.usecase.ExportBackup
import com.eliasgonzalez.expensetracker.domain.usecase.FindPendingCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.ObserveActivity
import com.eliasgonzalez.expensetracker.domain.usecase.ObserveCandidates
import com.eliasgonzalez.expensetracker.domain.usecase.ObserveExpenses
import com.eliasgonzalez.expensetracker.domain.usecase.RegisterExpense
import com.eliasgonzalez.expensetracker.domain.usecase.RejectCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Contenedor de dependencias manual (sin Hilt). Para el tamano actual del
 * proyecto, un grafo hecho a mano es mas simple de leer y depurar que
 * introducir un framework de DI - se puede migrar a Hilt mas adelante si
 * el grafo crece lo suficiente como para justificarlo.
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dbHelper = DbHelper(context.applicationContext)

    private val localExpenseRepository = LocalExpenseRepository(dbHelper)
    private val localCandidateRepository = LocalCandidateRepository(dbHelper)
    private val localActivityRepository = LocalActivityRepository(dbHelper)

    // Privados a propósito: la UI no debería tener una referencia directa
    // a un repositorio (eso le permitiría leer o mutar salteándose los
    // casos de uso). Lecturas reactivas se exponen vía los Observe* de
    // abajo; escrituras, vía cada caso de uso puntual.
    private val expenseRepository: ExpenseRepository = localExpenseRepository
    private val candidateRepository: CandidateRepository = localCandidateRepository
    private val activityRepository: ActivityRepository = localActivityRepository

    val registerExpense = RegisterExpense(expenseRepository, activityRepository)
    val createCandidate = CreateCandidate(candidateRepository, activityRepository)
    val confirmCandidate = ConfirmCandidate(candidateRepository, registerExpense, activityRepository)
    val editCandidate = EditCandidate(candidateRepository, registerExpense, activityRepository)
    val rejectCandidate = RejectCandidate(candidateRepository, activityRepository)
    val editExpense = EditExpense(expenseRepository, activityRepository)
    val deleteExpense = DeleteExpense(expenseRepository, activityRepository)
    val exportBackup = ExportBackup(expenseRepository, candidateRepository, activityRepository)
    val observeExpenses = ObserveExpenses(expenseRepository)
    val observeCandidates = ObserveCandidates(candidateRepository)
    val observeActivity = ObserveActivity(activityRepository)
    val findPendingCandidate = FindPendingCandidate(candidateRepository)

    init {
        appScope.launch {
            localExpenseRepository.hydrate()
            localCandidateRepository.hydrate()
            localActivityRepository.hydrate()
        }
    }
}

object ServiceLocator {
    @Volatile private var container: AppContainer? = null

    fun init(context: Context) {
        if (container == null) {
            synchronized(this) {
                if (container == null) container = AppContainer(context)
            }
        }
    }

    fun get(): AppContainer =
        container ?: error("ServiceLocator no inicializado - falta ExpenseTrackerApp en el manifest")
}
