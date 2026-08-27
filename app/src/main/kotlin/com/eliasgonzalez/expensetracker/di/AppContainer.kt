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
import com.eliasgonzalez.expensetracker.domain.usecase.EditCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.ExportBackup
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

    val expenseRepository: ExpenseRepository = localExpenseRepository
    val candidateRepository: CandidateRepository = localCandidateRepository
    val activityRepository: ActivityRepository = localActivityRepository

    val registerExpense = RegisterExpense(expenseRepository, activityRepository)
    val createCandidate = CreateCandidate(candidateRepository, activityRepository)
    val confirmCandidate = ConfirmCandidate(candidateRepository, registerExpense, activityRepository)
    val editCandidate = EditCandidate(candidateRepository, registerExpense, activityRepository)
    val rejectCandidate = RejectCandidate(candidateRepository, activityRepository)
    val exportBackup = ExportBackup(expenseRepository, candidateRepository, activityRepository)

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
