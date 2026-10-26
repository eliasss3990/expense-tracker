package com.eliasgonzalez.expensetracker.di

import android.content.Context
import android.util.Log
import com.eliasgonzalez.expensetracker.data.local.DbHelper
import com.eliasgonzalez.expensetracker.data.local.LocalActivityRepository
import com.eliasgonzalez.expensetracker.data.local.LocalCandidateRepository
import com.eliasgonzalez.expensetracker.data.local.LocalCatalogRepository
import com.eliasgonzalez.expensetracker.data.local.LocalExpenseRepository
import com.eliasgonzalez.expensetracker.domain.catalog.CatalogCategoryPolicy
import com.eliasgonzalez.expensetracker.domain.catalog.CategoryPolicy
import com.eliasgonzalez.expensetracker.domain.catalog.ExpenseCatalogUsage
import com.eliasgonzalez.expensetracker.domain.notification.CandidateNotifier
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import com.eliasgonzalez.expensetracker.domain.usecase.ConfirmCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.CreateCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.CreateCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.DeleteCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.DeleteExpense
import com.eliasgonzalez.expensetracker.domain.usecase.EditCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.EditExpense
import com.eliasgonzalez.expensetracker.domain.usecase.ExportBackup
import com.eliasgonzalez.expensetracker.domain.usecase.FindPendingCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.ObserveActivity
import com.eliasgonzalez.expensetracker.domain.usecase.ObserveCandidates
import com.eliasgonzalez.expensetracker.domain.usecase.ObserveCatalog
import com.eliasgonzalez.expensetracker.domain.usecase.ObserveExpenses
import com.eliasgonzalez.expensetracker.domain.usecase.RegisterExpense
import com.eliasgonzalez.expensetracker.domain.usecase.RejectCandidate
import com.eliasgonzalez.expensetracker.domain.usecase.RenameCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.SetCatalogEntryActive
import com.eliasgonzalez.expensetracker.notification.AndroidCandidateNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex

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
    private val localCatalogRepository = LocalCatalogRepository(dbHelper)

    // Privados a propósito: la UI no debería tener una referencia directa
    // a un repositorio (eso le permitiría leer o mutar salteándose los
    // casos de uso). Lecturas reactivas se exponen vía los Observe* de
    // abajo; escrituras, vía cada caso de uso puntual.
    private val expenseRepository: ExpenseRepository = localExpenseRepository
    private val candidateRepository: CandidateRepository = localCandidateRepository
    private val activityRepository: ActivityRepository = localActivityRepository
    private val catalogRepository: CatalogRepository = localCatalogRepository

    // Cada hydrate va aislado: que uno falle (fila corrupta, DB bloqueada)
    // ya no cancela a los demas ni deja el catalogo vacio todo el proceso.
    // Los casos de uso del catalogo esperan este Deferred antes de validar,
    // porque antes de hidratar las listas en memoria estan vacias.
    private val hydration = appScope.async {
        var allLoaded = true
        listOf<suspend () -> Unit>(
            { localExpenseRepository.hydrate() },
            { localCandidateRepository.hydrate() },
            { localActivityRepository.hydrate() },
            { localCatalogRepository.hydrate() },
        ).forEach { hydrate ->
            try {
                hydrate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allLoaded = false
                Log.e("AppContainer", "Fallo al hidratar un repositorio", e)
            }
        }
        allLoaded
    }

    // true solo si TODOS los repositorios cargaron: los casos de uso del
    // catalogo se niegan a operar (NOT_READY) si no, porque con listas
    // vacias "en uso" daria false para datos reales.
    private val awaitHydrated: suspend () -> Boolean = { hydration.await() }
    private val categoryPolicy: CategoryPolicy = CatalogCategoryPolicy(catalogRepository) { awaitHydrated() }

    // Un solo Mutex por dominio (candidatos, gastos), compartido entre
    // TODOS los casos de uso que escriben sobre ese dominio - no uno por
    // clase. Un mutex propio por clase solo serializaba llamadas
    // repetidas al MISMO caso de uso; no impedia, por ejemplo, que
    // ConfirmCandidate y EditCandidate operaran sobre el mismo candidato
    // al mismo tiempo y duplicaran el Expense resultante. Ver el
    // comentario en CreateCandidate.kt.
    private val candidateWriteMutex = Mutex()
    private val expenseWriteMutex = Mutex()
    private val catalogWriteMutex = Mutex()

    private val candidateNotifier: CandidateNotifier = AndroidCandidateNotifier(context.applicationContext)

    val registerExpense = RegisterExpense(expenseRepository, activityRepository, expenseWriteMutex, categoryPolicy)
    val createCandidate = CreateCandidate(candidateRepository, activityRepository, candidateWriteMutex, categoryPolicy)
    val confirmCandidate = ConfirmCandidate(candidateRepository, registerExpense, activityRepository, candidateWriteMutex, candidateNotifier)
    val editCandidate = EditCandidate(candidateRepository, registerExpense, activityRepository, candidateWriteMutex, candidateNotifier, categoryPolicy)
    val rejectCandidate = RejectCandidate(candidateRepository, activityRepository, candidateWriteMutex, candidateNotifier)
    val editExpense = EditExpense(expenseRepository, activityRepository, expenseWriteMutex, categoryPolicy)
    val deleteExpense = DeleteExpense(expenseRepository, activityRepository, expenseWriteMutex)
    val exportBackup = ExportBackup(expenseRepository, candidateRepository, activityRepository)
    val observeExpenses = ObserveExpenses(expenseRepository)
    val observeCandidates = ObserveCandidates(candidateRepository)
    val observeActivity = ObserveActivity(activityRepository)
    val findPendingCandidate = FindPendingCandidate(candidateRepository)

    // Catalogo gestionable (PR 1 de 4: todavia sin consumidores en la UI,
    // el enum Category sigue siendo la fuente hasta el PR 2).
    private val catalogUsage = ExpenseCatalogUsage(expenseRepository, candidateRepository)
    val observeCatalog = ObserveCatalog(catalogRepository)
    val createCatalogEntry = CreateCatalogEntry(catalogRepository, catalogWriteMutex, awaitHydrated)
    val renameCatalogEntry = RenameCatalogEntry(catalogRepository, catalogWriteMutex, awaitHydrated)
    val setCatalogEntryActive = SetCatalogEntryActive(catalogRepository, catalogWriteMutex, awaitHydrated)
    val deleteCatalogEntry = DeleteCatalogEntry(
        catalogRepository, catalogUsage, catalogWriteMutex, awaitHydrated,
        // Orden de locks: catalogo, candidatos, gastos (ver DeleteCatalogEntry).
        alsoLock = listOf(candidateWriteMutex, expenseWriteMutex),
    )
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
