package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Punto único de lectura reactiva del log de actividad - misma razón que
 * [ObserveExpenses]: evita que la UI tenga una referencia directa al
 * repositorio.
 */
class ObserveActivity(private val activity: ActivityRepository) {
    operator fun invoke(): StateFlow<List<ActivityEntry>> = activity.recent
}
